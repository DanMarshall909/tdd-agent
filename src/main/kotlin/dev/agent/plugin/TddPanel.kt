package dev.agent.plugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import dev.agent.workflow.Step
import dev.agent.workflow.TransitionResult
import dev.agent.workflow.WorkflowEvent
import dev.agent.workflow.WorkflowPhase
import dev.agent.workflow.WorkflowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.JTabbedPane
import javax.swing.ListSelectionModel

/**
 * Main UI panel for the TDD Agent tool window.
 * Orchestrates requirements, research, planning, and implementation phases.
 */
class TddPanel(private val project: Project) : JBPanel<TddPanel>(BorderLayout()) {
    companion object {
        private val LOG = Logger.getInstance(TddPanel::class.java)
    }

    private val tddService = TddService.getInstance(project)
    private val workflowService = WorkflowService.getInstance(project)
    private val scope = CoroutineScope(Dispatchers.Default)

    // Phase tabs
    private val phaseTabs = JTabbedPane()

    // Requirements UI
    private val featureDescriptionArea = JBTextArea()
    private val additionalRequirementsArea = JBTextArea()
    private val generateScenariosButton = JButton("Generate Scenarios")
    private val approveScenariosButton = JButton("Approve Scenarios")
    private val scenariosArea = JBTextArea()

    // Research UI (placeholder scaffolding)
    private val researchSummaryArea = JBTextArea()
    private val completeResearchButton = JButton("Mark Research Complete")

    // Planning UI (placeholder scaffolding)
    private val planArea = JBTextArea()
    private val savePlanButton = JButton("Save Plan")
    private val approvePlanButton = JButton("Approve Plan")

    // Implementation UI
    private val stepListModel = DefaultListModel<String>()
    private val stepList = JBList(stepListModel)
    private val currentStepLabel = JBLabel("Current step: -")
    private val outputEditor = createKotlinEditor()
    private val generateTestButton = JButton("Generate Test")
    private val generateImplButton = JButton("Generate Implementation")
    private val insertRunButton = JButton("Insert & Run")
    private val copyButton = JButton("Copy to Clipboard")

    private val statusLabel = JBLabel("Ready")

    private var lastTestCode: String? = null
    private var lastStepKey: String? = null

    override fun removeNotify() {
        super.removeNotify()
        if (!outputEditor.isDisposed) {
            EditorFactory.getInstance().releaseEditor(outputEditor)
        }
    }

    init {
        configureTextArea(featureDescriptionArea, rows = 6)
        configureTextArea(additionalRequirementsArea, rows = 4)
        configureTextArea(scenariosArea, rows = 12, readOnly = true)
        configureTextArea(researchSummaryArea, rows = 8)
        configureTextArea(planArea, rows = 8)

        stepList.selectionMode = ListSelectionModel.SINGLE_SELECTION

        phaseTabs.addTab("Requirements", buildRequirementsPanel())
        phaseTabs.addTab("Research", buildResearchPanel())
        phaseTabs.addTab("Planning", buildPlanningPanel())
        phaseTabs.addTab("Implementation", buildImplementationPanel())

        add(phaseTabs, BorderLayout.CENTER)
        add(buildStatusPanel(), BorderLayout.SOUTH)

        wireActions()

        updateFromState(workflowService.getState())
    }

    private fun wireActions() {
        generateScenariosButton.addActionListener { onGenerateScenarios() }
        approveScenariosButton.addActionListener { onApproveScenarios() }
        completeResearchButton.addActionListener { onCompleteResearch() }
        savePlanButton.addActionListener { onSavePlan() }
        approvePlanButton.addActionListener { onApprovePlan() }
        generateTestButton.addActionListener { onGenerateTest() }
        generateImplButton.addActionListener { onGenerateImplementation() }
        insertRunButton.addActionListener { onInsertAndRun() }
        copyButton.addActionListener { onCopyToClipboard() }
    }

    private fun onGenerateScenarios() {
        val feature = featureDescriptionArea.text.trim()
        if (feature.isBlank()) {
            updateStatus("Error: Enter a feature description")
            return
        }

        val additional = additionalRequirementsArea.text.trim().ifBlank { null }
        val featureResult = dispatch(WorkflowEvent.FeatureSubmitted(feature, additional))
        if (featureResult is TransitionResult.Rejected) {
            handleTransitionResult(featureResult)
            return
        }

        setRequirementsButtonsEnabled(false)
        updateStatus("Generating scenarios...")

        scope.launch {
            try {
                val scenarios = tddService.orchestrator.generateScenarios(feature, additional)
                ApplicationManager.getApplication().invokeLater {
                    val result = dispatch(WorkflowEvent.ScenariosGenerated(scenarios))
                    handleTransitionResult(result)
                    updateStatus("Scenarios generated")
                    setRequirementsButtonsEnabled(true)
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    LOG.error("Error generating scenarios", e)
                    updateStatus("Error: ${e.message}")
                    setRequirementsButtonsEnabled(true)
                }
            }
        }
    }

    private fun onApproveScenarios() {
        val result = dispatch(WorkflowEvent.ScenariosApproved)
        handleTransitionResult(result)
    }

    private fun onCompleteResearch() {
        val summary = researchSummaryArea.text.trim()
        if (summary.isBlank()) {
            updateStatus("Error: Add a research summary")
            return
        }
        val result = dispatch(WorkflowEvent.ResearchCompleted(summary))
        handleTransitionResult(result)
    }

    private fun onSavePlan() {
        val plan = planArea.text.trim()
        if (plan.isBlank()) {
            updateStatus("Error: Add a plan before saving")
            return
        }
        val result = dispatch(WorkflowEvent.PlanProposed(plan))
        handleTransitionResult(result)
    }

    private fun onApprovePlan() {
        val plan = planArea.text.trim()
        if (plan.isNotBlank()) {
            dispatch(WorkflowEvent.PlanProposed(plan))
        }
        val result = dispatch(WorkflowEvent.PlanApproved)
        handleTransitionResult(result)
    }

    private fun onGenerateTest() {
        val step = workflowService.getState().implementation.currentStepOrNull()
        if (step == null) {
            updateStatus("Error: No current step")
            return
        }
        val stepText = formatStepForPrompt(step)

        LOG.info("User action: Generate Test - Step: '$stepText'")
        setImplementationButtonsEnabled(false)
        updateStatus("Generating test...")

        scope.launch {
            try {
                val code = tddService.orchestrator.generateTestCode(stepText)
                ApplicationManager.getApplication().invokeLater {
                    ApplicationManager.getApplication().runWriteAction {
                        outputEditor.document.setText(code)
                    }
                    lastTestCode = code
                    generateImplButton.isEnabled = true
                    copyButton.isEnabled = true
                    updateStatus("Test generated")
                    setImplementationButtonsEnabled(true, preserveImplState = true)
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    LOG.error("Error generating test", e)
                    updateStatus("Error: ${e.message}")
                    setImplementationButtonsEnabled(true)
                }
            }
        }
    }

    private fun onGenerateImplementation() {
        val testCode = lastTestCode ?: outputEditor.document.text
        if (testCode.isBlank()) {
            updateStatus("Error: Generate a test first")
            return
        }

        LOG.info("User action: Generate Implementation")
        setImplementationButtonsEnabled(false)
        updateStatus("Generating implementation...")

        scope.launch {
            try {
                val code = tddService.orchestrator.generateImplementationCode(testCode)
                ApplicationManager.getApplication().invokeLater {
                    ApplicationManager.getApplication().runWriteAction {
                        outputEditor.document.setText(code)
                    }
                    copyButton.isEnabled = true
                    updateStatus("Implementation generated")
                    setImplementationButtonsEnabled(true, preserveImplState = true)
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    LOG.error("Error generating implementation", e)
                    updateStatus("Error: ${e.message}")
                    setImplementationButtonsEnabled(true)
                }
            }
        }
    }

    private fun onInsertAndRun() {
        val step = workflowService.getState().implementation.currentStepOrNull()
        if (step == null) {
            updateStatus("Error: No current step")
            return
        }
        val stepText = formatStepForPrompt(step)

        LOG.info("User action: Insert & Run - Step: '$stepText'")
        setImplementationButtonsEnabled(false)
        updateStatus("Running full TDD cycle...")

        scope.launch {
            try {
                val result = tddService.orchestrator.executeStep(stepText)
                ApplicationManager.getApplication().invokeLater {
                    val code = result.implCode?.takeIf { it.isNotBlank() } ?: result.testCode.orEmpty()
                    if (code.isNotBlank()) {
                        ApplicationManager.getApplication().runWriteAction {
                            outputEditor.document.setText(code)
                        }
                        copyButton.isEnabled = true
                    }

                    if (result.success) {
                        dispatch(WorkflowEvent.StepCompleted)
                        updateStatus("TDD cycle complete")
                    } else {
                        LOG.warn("TDD cycle failed: ${result.error}")
                        updateStatus("Error: ${result.error ?: "TDD cycle failed"}")
                    }
                    setImplementationButtonsEnabled(true)
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    LOG.error("Error during TDD cycle", e)
                    updateStatus("Error: ${e.message ?: "Unknown error during TDD cycle"}")
                    setImplementationButtonsEnabled(true)
                }
            }
        }
    }

    private fun onCopyToClipboard() {
        val text = outputEditor.document.text
        if (text.isBlank()) {
            updateStatus("Error: Nothing to copy")
            return
        }

        LOG.info("User action: Copy to Clipboard (${text.length} chars)")
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(text), null)
        updateStatus("Copied to clipboard")
    }

    private fun dispatch(event: WorkflowEvent): TransitionResult {
        val result = workflowService.dispatch(event)
        if (result is TransitionResult.Success) {
            updateFromState(result.state)
        }
        return result
    }

    private fun handleTransitionResult(result: TransitionResult) {
        when (result) {
            is TransitionResult.Success -> {
                updateStatus("Phase: ${result.state.phase.name.lowercase().replaceFirstChar { it.uppercase() }}")
            }
            is TransitionResult.Rejected -> {
                updateStatus("Error: ${result.reason}")
            }
        }
    }

    private fun updateFromState(state: WorkflowState) {
        if (featureDescriptionArea.text != state.requirements.featureDescription) {
            featureDescriptionArea.text = state.requirements.featureDescription
        }
        val additional = state.requirements.additionalRequirements.orEmpty()
        if (additionalRequirementsArea.text != additional) {
            additionalRequirementsArea.text = additional
        }
        featureDescriptionArea.isEnabled = !state.requirements.approved
        additionalRequirementsArea.isEnabled = !state.requirements.approved
        generateScenariosButton.isEnabled = !state.requirements.approved
        scenariosArea.text = formatScenarios(state.requirements.scenarios)
        approveScenariosButton.isEnabled = state.requirements.scenarios.isNotEmpty() && !state.requirements.approved

        val isResearch = state.phase == WorkflowPhase.RESEARCH
        val researchSummary = state.research.summary.orEmpty()
        if (researchSummaryArea.text != researchSummary) {
            researchSummaryArea.text = researchSummary
        }
        researchSummaryArea.isEnabled = isResearch
        completeResearchButton.isEnabled = isResearch

        val isPlanning = state.phase == WorkflowPhase.PLANNING
        val planText = state.planning.plan.orEmpty()
        if (planArea.text != planText) {
            planArea.text = planText
        }
        planArea.isEnabled = isPlanning
        savePlanButton.isEnabled = isPlanning
        approvePlanButton.isEnabled = isPlanning && !state.planning.plan.isNullOrBlank()

        val isImplementation = state.phase == WorkflowPhase.IMPLEMENTATION
        setImplementationButtonsEnabled(isImplementation)
        updateStepQueue(state.implementation.steps, state.implementation.currentStepIndex)

        val currentStep = state.implementation.currentStepOrNull()
        currentStepLabel.text = if (currentStep == null) {
            "Current step: -"
        } else {
            "Current step: ${currentStep.type.name} ${currentStep.text}"
        }

        val currentKey = currentStep?.let { "${state.implementation.currentStepIndex}:${it.type}:${it.text}" }
        if (currentKey != lastStepKey) {
            lastStepKey = currentKey
            lastTestCode = null
            generateImplButton.isEnabled = false
        }

        phaseTabs.selectedIndex = when (state.phase) {
            WorkflowPhase.REQUIREMENTS -> 0
            WorkflowPhase.RESEARCH -> 1
            WorkflowPhase.PLANNING -> 2
            WorkflowPhase.IMPLEMENTATION -> 3
        }
    }

    private fun updateStepQueue(steps: List<Step>, currentIndex: Int) {
        stepListModel.clear()
        steps.forEachIndexed { index, step ->
            val marker = when {
                index < currentIndex -> "[x]"
                index == currentIndex -> "[*]"
                else -> "[ ]"
            }
            stepListModel.addElement("$marker ${step.type.name} ${step.text}")
        }
        if (currentIndex in 0 until stepListModel.size()) {
            stepList.selectedIndex = currentIndex
        }
    }

    private fun setRequirementsButtonsEnabled(enabled: Boolean) {
        val state = workflowService.getState()
        val unlocked = !state.requirements.approved
        generateScenariosButton.isEnabled = enabled && unlocked
        approveScenariosButton.isEnabled = enabled && unlocked && state.requirements.scenarios.isNotEmpty()
    }

    private fun setImplementationButtonsEnabled(enabled: Boolean, preserveImplState: Boolean = false) {
        generateTestButton.isEnabled = enabled
        insertRunButton.isEnabled = enabled
        if (!preserveImplState) {
            generateImplButton.isEnabled = enabled && lastTestCode != null
            copyButton.isEnabled = enabled && outputEditor.document.text.isNotBlank()
        }
        LOG.debug("Setting implementation buttons enabled: $enabled")
    }

    private fun updateStatus(message: String) {
        statusLabel.text = message
        LOG.info("Status: $message")
    }

    private fun formatStepForPrompt(step: Step): String {
        return "${step.type.name}: ${step.text}"
    }

    private fun formatScenarios(scenarios: List<dev.agent.workflow.Scenario>): String {
        if (scenarios.isEmpty()) return ""
        return scenarios.joinToString("\n\n") { scenario ->
            buildString {
                append("Scenario: ")
                appendLine(scenario.name)
                scenario.steps.forEach { step ->
                    append("  ")
                    append(step.type.name)
                    append(" ")
                    appendLine(step.text)
                }
            }
        }
    }

    private fun buildRequirementsPanel(): JPanel {
        val panel = JPanel(BorderLayout())

        val inputPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JBLabel("Feature description"))
            add(JBScrollPane(featureDescriptionArea))
            add(Box.createVerticalStrut(8))
            add(JBLabel("Additional requirements (optional)"))
            add(JBScrollPane(additionalRequirementsArea))
        }

        val scenariosPanel = JPanel(BorderLayout()).apply {
            add(JBLabel("Generated scenarios"), BorderLayout.NORTH)
            add(JBScrollPane(scenariosArea), BorderLayout.CENTER)
        }

        val buttonPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(generateScenariosButton)
            add(Box.createHorizontalStrut(8))
            add(approveScenariosButton)
            add(Box.createHorizontalGlue())
        }

        panel.add(inputPanel, BorderLayout.NORTH)
        panel.add(scenariosPanel, BorderLayout.CENTER)
        panel.add(buttonPanel, BorderLayout.SOUTH)
        return panel
    }

    private fun buildResearchPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.add(JBLabel("Research summary (M5 placeholder)"), BorderLayout.NORTH)
        panel.add(JBScrollPane(researchSummaryArea), BorderLayout.CENTER)
        panel.add(completeResearchButton, BorderLayout.SOUTH)
        return panel
    }

    private fun buildPlanningPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.add(JBLabel("Plan proposal (M6 placeholder)"), BorderLayout.NORTH)
        panel.add(JBScrollPane(planArea), BorderLayout.CENTER)

        val buttonPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(savePlanButton)
            add(Box.createHorizontalStrut(8))
            add(approvePlanButton)
            add(Box.createHorizontalGlue())
        }

        panel.add(buttonPanel, BorderLayout.SOUTH)
        return panel
    }

    private fun buildImplementationPanel(): JPanel {
        val panel = JPanel(BorderLayout())

        val listPanel = JPanel(BorderLayout()).apply {
            add(JBLabel("Step queue"), BorderLayout.NORTH)
            add(JBScrollPane(stepList), BorderLayout.CENTER)
        }

        val editorPanel = JPanel(BorderLayout()).apply {
            add(currentStepLabel, BorderLayout.NORTH)
            add(JBScrollPane(outputEditor.component), BorderLayout.CENTER)
        }

        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listPanel, editorPanel).apply {
            resizeWeight = 0.3
        }

        val buttonPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(generateTestButton)
            add(Box.createHorizontalStrut(8))
            add(generateImplButton)
            add(Box.createHorizontalStrut(8))
            add(insertRunButton)
            add(Box.createHorizontalStrut(8))
            add(copyButton)
            add(Box.createHorizontalGlue())
        }

        panel.add(splitPane, BorderLayout.CENTER)
        panel.add(buttonPanel, BorderLayout.SOUTH)
        return panel
    }

    private fun buildStatusPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            add(statusLabel, BorderLayout.WEST)
        }
    }

    private fun configureTextArea(area: JBTextArea, rows: Int, readOnly: Boolean = false) {
        area.lineWrap = true
        area.wrapStyleWord = true
        area.rows = rows
        area.isEditable = !readOnly
    }

    private fun createKotlinEditor(): EditorEx {
        val factory = FileTypeManager.getInstance()
        val kotlinFileType = factory.getFileTypeByExtension("kt") ?: error("Kotlin file type not found")

        val document = EditorFactory.getInstance().createDocument("")
        val editor = EditorFactory.getInstance().createEditor(document, project, kotlinFileType, true) as EditorEx

        editor.settings.apply {
            isLineNumbersShown = true
            isLineMarkerAreaShown = false
            isFoldingOutlineShown = false
            isRightMarginShown = false
            isIndentGuidesShown = false
            isCaretRowShown = false
        }

        val highlighter = EditorHighlighterFactory.getInstance()
            .createEditorHighlighter(project, kotlinFileType)
        editor.highlighter = highlighter

        val colorScheme = EditorColorsManager.getInstance().globalScheme
        editor.colorsScheme = colorScheme
        editor.isViewer = true
        editor.component.background = colorScheme.defaultBackground

        return editor
    }
}
