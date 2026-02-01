package dev.agent.plugin

import com.intellij.openapi.application.ApplicationManager
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
    private val service = TddService.getInstance(project)
    private val scope = CoroutineScope(Dispatchers.Default)

    // UI components
    private val inputField = JBTextField()
    private val outputEditor = createKotlinEditor()
    private val generateTestButton = JButton("Generate Test")
    private val generateImplButton = JButton("Generate Implementation")
    private val copyButton = JButton("Copy to Clipboard")
    private val statusLabel = JBLabel("Ready")

    override fun removeNotify() {
        super.removeNotify()
        EditorFactory.getInstance().releaseEditor(outputEditor)
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
        copyButton.addActionListener {
            onCopyToClipboard()
        }

        // Set initial state
        generateImplButton.isEnabled = false
        copyButton.isEnabled = false
        inputField.toolTipText = "Enter a BDD step, e.g., 'User can login with valid credentials'"
    }

    private fun onGenerateTest() {
        val step = inputField.text.trim()
        if (step.isBlank()) {
            statusLabel.text = "❌ Error: Enter a BDD step"
            return
        }

        generateTestButton.isEnabled = false
        generateImplButton.isEnabled = false
        statusLabel.text = "⏳ Generating test..."

        scope.launch {
            try {
                val code = service.orchestrator.generateTestCode(step)

                ApplicationManager.getApplication().invokeLater {
                    ApplicationManager.getApplication().runWriteAction {
                        outputEditor.document.setText(code)
                    }
                    statusLabel.text = "✅ Test generated"
                    generateTestButton.isEnabled = true
                    generateImplButton.isEnabled = true
                    copyButton.isEnabled = true
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    statusLabel.text = "❌ Error: ${e.message}"
                    generateTestButton.isEnabled = true
                }
            }
        }
    }

    private fun onGenerateImplementation() {
        val testCode = outputEditor.document.text
        if (testCode.isBlank()) {
            statusLabel.text = "❌ Error: Generate a test first"
            return
        }

        generateTestButton.isEnabled = false
        generateImplButton.isEnabled = false
        statusLabel.text = "⏳ Generating implementation..."

        scope.launch {
            try {
                val code = service.orchestrator.generateImplementationCode(testCode)

                ApplicationManager.getApplication().invokeLater {
                    ApplicationManager.getApplication().runWriteAction {
                        outputEditor.document.setText(code)
                    }
                    statusLabel.text = "✅ Implementation generated"
                    generateTestButton.isEnabled = true
                    generateImplButton.isEnabled = true
                    copyButton.isEnabled = true
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    statusLabel.text = "❌ Error: ${e.message}"
                    generateImplButton.isEnabled = true
                }
            }
        }
    }

    private fun onCopyToClipboard() {
        val text = outputEditor.document.text
        if (text.isBlank()) {
            statusLabel.text = "❌ Error: Nothing to copy"
            return
        }

        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(text), null)
        statusLabel.text = "✅ Copied to clipboard"
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

        // Set colors
        val colorScheme = EditorColorsManager.getInstance().globalScheme
        editor.colorsScheme = colorScheme

        editor.isViewer = true
        return editor
    }
}
