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
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel

/**
 * Main UI panel for the TDD Agent tool window.
 * Handles test and implementation generation with syntax highlighting.
 */
class TddPanel(private val project: Project) : JBPanel<TddPanel>(BorderLayout()) {
    companion object {
        private val LOG = Logger.getInstance(TddPanel::class.java)
    }

    private val service = TddService.getInstance(project)
    private val scope = CoroutineScope(Dispatchers.Default)

    // UI components
    private val inputField = JBTextField()
    private val outputEditor = createKotlinEditor()
    private val generateTestButton = JButton("Generate Test")
    private val generateImplButton = JButton("Generate Implementation")
    private val insertRunButton = JButton("Insert & Run")
    private val copyButton = JButton("Copy to Clipboard")
    private val statusLabel = JBLabel("Ready")

    override fun removeNotify() {
        super.removeNotify()
        // Guard against double release
        if (!outputEditor.isDisposed) {
            EditorFactory.getInstance().releaseEditor(outputEditor)
        }
    }

    init {
        // Set up input panel
        val inputPanel = JPanel(BorderLayout()).apply {
            add(JBLabel("BDD Step:"), BorderLayout.WEST)
            add(inputField, BorderLayout.CENTER)
        }

        // Set up button panel
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

        // Set up status panel
        val statusPanel = JPanel(BorderLayout()).apply {
            add(statusLabel, BorderLayout.WEST)
        }

        // Set up main layout
        add(inputPanel, BorderLayout.NORTH)
        add(JBScrollPane(outputEditor.component), BorderLayout.CENTER)
        add(Box.createVerticalStrut(8), BorderLayout.SOUTH)

        // Create a footer panel with buttons and status
        val footerPanel = JPanel(BorderLayout()).apply {
            add(buttonPanel, BorderLayout.CENTER)
            add(statusPanel, BorderLayout.SOUTH)
        }
        add(footerPanel, BorderLayout.SOUTH)

        // Wire up event handlers
        generateTestButton.addActionListener {
            onGenerateTest()
        }
        generateImplButton.addActionListener {
            onGenerateImplementation()
        }
        insertRunButton.addActionListener {
            onInsertAndRun()
        }
        copyButton.addActionListener {
            onCopyToClipboard()
        }

        // Set initial state
        generateImplButton.isEnabled = false
        insertRunButton.isEnabled = true
        copyButton.isEnabled = false
        inputField.toolTipText = "Enter a BDD step, e.g., 'User can login with valid credentials'"
    }

    private fun updateStatus(message: String) {
        statusLabel.text = message
        LOG.info("Status: $message")
    }

    private fun onGenerateTest() {
        val step = inputField.text.trim()
        if (step.isBlank()) {
            updateStatus("Error: Enter a BDD step")
            return
        }

        LOG.info("User action: Generate Test - Step: '$step'")
        setButtonsEnabled(false)
        updateStatus("Generating test...")

        scope.launch {
            try {
                val code = service.orchestrator.generateTestCode(step)

                ApplicationManager.getApplication().invokeLater {
                    ApplicationManager.getApplication().runWriteAction {
                        outputEditor.document.setText(code)
                    }
                    updateStatus("Test generated")
                    setButtonsEnabled(true)
                    copyButton.isEnabled = true
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    LOG.error("Error generating test", e)
                    updateStatus("Error: ${e.message}")
                    setButtonsEnabled(true)
                }
            }
        }
    }

    private fun onGenerateImplementation() {
        val testCode = outputEditor.document.text
        if (testCode.isBlank()) {
            updateStatus("Error: Generate a test first")
            return
        }

        LOG.info("User action: Generate Implementation")
        setButtonsEnabled(false)
        updateStatus("Generating implementation...")

        scope.launch {
            try {
                val code = service.orchestrator.generateImplementationCode(testCode)

                ApplicationManager.getApplication().invokeLater {
                    ApplicationManager.getApplication().runWriteAction {
                        outputEditor.document.setText(code)
                    }
                    updateStatus("Implementation generated")
                    setButtonsEnabled(true)
                    copyButton.isEnabled = true
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    LOG.error("Error generating implementation", e)
                    updateStatus("Error: ${e.message}")
                    setButtonsEnabled(true)
                }
            }
        }
    }

    private fun onInsertAndRun() {
        val step = inputField.text.trim()
        if (step.isBlank()) {
            updateStatus("Error: Enter a BDD step")
            return
        }

        LOG.info("User action: Insert & Run - Step: '$step'")
        setButtonsEnabled(false)
        updateStatus("Running full TDD cycle...")

        scope.launch {
            try {
                val orchestrator = service.orchestrator
                if (orchestrator == null) {
                    ApplicationManager.getApplication().invokeLater {
                        LOG.error("Orchestrator not initialized")
                        updateStatus("Error: Orchestrator not initialized")
                        setButtonsEnabled(true)
                    }
                    return@launch
                }

                val result = orchestrator.executeStep(step)
                if (result == null) {
                    ApplicationManager.getApplication().invokeLater {
                        LOG.error("Execution returned null")
                        updateStatus("Error: Execution returned null")
                        setButtonsEnabled(true)
                    }
                    return@launch
                }

                ApplicationManager.getApplication().invokeLater {
                    // Prioritize impl code if present, fall back to test code
                    val code = result.implCode?.takeIf { it.isNotBlank() } ?: result.testCode.orEmpty()
                    if (code.isNotBlank()) {
                        ApplicationManager.getApplication().runWriteAction {
                            outputEditor.document.setText(code)
                        }
                        copyButton.isEnabled = true
                    }

                    if (result.success) {
                        updateStatus("TDD cycle complete")
                    } else {
                        LOG.warn("TDD cycle failed: ${result.error}")
                        updateStatus("Error: ${result.error ?: "TDD cycle failed"}")
                    }
                    setButtonsEnabled(true)
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    LOG.error("Error during TDD cycle", e)
                    updateStatus("Error: ${e.message ?: "Unknown error during TDD cycle"}")
                    setButtonsEnabled(true)
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

    private fun setButtonsEnabled(enabled: Boolean) {
        generateTestButton.isEnabled = enabled
        generateImplButton.isEnabled = enabled
        insertRunButton.isEnabled = enabled
        LOG.debug("Setting buttons enabled: $enabled")
    }

    private fun createKotlinEditor(): EditorEx {
        val factory = FileTypeManager.getInstance()
        val kotlinFileType = factory.getFileTypeByExtension("kt") ?: error("Kotlin file type not found")

        val document = EditorFactory.getInstance().createDocument("")
        val editor = EditorFactory.getInstance().createEditor(document, project, kotlinFileType, true) as EditorEx

        // Configure editor
        editor.settings.apply {
            isLineNumbersShown = true
            isLineMarkerAreaShown = false
            isFoldingOutlineShown = false
            isRightMarginShown = false
            isIndentGuidesShown = false
            isCaretRowShown = false
        }

        // Set syntax highlighting
        val highlighter = EditorHighlighterFactory.getInstance()
            .createEditorHighlighter(project, kotlinFileType)
        editor.highlighter = highlighter

        // Set colors from global scheme
        val colorScheme = EditorColorsManager.getInstance().globalScheme
        editor.colorsScheme = colorScheme

        editor.isViewer = true

        // Set background color for tool window context
        editor.component.background = colorScheme.defaultBackground

        return editor
    }
}
