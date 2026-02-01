package dev.agent.plugin

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import dev.agent.CodeInserter
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile

/**
 * IDE implementation of CodeInserter for test and implementation insertion.
 */
class IdeCodeInserter(private val project: Project) : CodeInserter {
    companion object {
        private val LOG = Logger.getInstance(IdeCodeInserter::class.java)
    }
    override suspend fun insertTest(testCode: String): Boolean {
        LOG.info("insertTest() called with ${testCode.length} chars")

        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        if (editor == null) {
            LOG.warn("No active editor")
            notify("No active editor. Open a production file to locate its test file.")
            return false
        }

        // Wrap PSI access in ReadAction to comply with IntelliJ threading model
        val psiFile = runReadAction {
            PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
        }

        if (psiFile == null) {
            LOG.error("No PSI file found for current editor")
            notify("No PSI file found for the current editor.")
            return false
        }

        LOG.debug("Current editor file: ${psiFile.name}")

        val existingTestResult = runReadAction {
            TestFileLocator.findTestFile(project, psiFile)
        }

        val testFile = if (existingTestResult != null) {
            LOG.info("Found existing test file: ${existingTestResult.name}")
            existingTestResult
        } else {
            LOG.debug("No test file found, creating new one")
            val baseName = psiFile.virtualFile?.nameWithoutExtension ?: psiFile.name

            // Show dialog on EDT - use mutable var to capture result
            var response = Messages.NO
            ApplicationManager.getApplication().invokeAndWait {
                response = Messages.showYesNoDialog(
                    project,
                    "No test file found for $baseName. Create ${baseName}Test.kt?",
                    "Create Test File",
                    null,
                )
            }

            if (response != Messages.YES) {
                LOG.info("User declined to create test file")
                return false
            }

            TestFileLocator.createTestFile(project, psiFile)
                ?: run {
                    LOG.error("Failed to create test file")
                    notify("Failed to create test file. Check test source roots.")
                    return false
                }
        }

        val ktFile = testFile as? KtFile
        if (ktFile == null) {
            LOG.error("Test file is not a Kotlin file: ${testFile.name}")
            notify("Test file is not a Kotlin file.")
            return false
        }

        // Wrap PSI reads for finding insertion point and behavior class
        val insertionPointResult = runReadAction {
            TestInsertionLocator.findInsertionPoint(ktFile)
        }

        if (insertionPointResult == null) {
            LOG.error("No BehaviorSpec found in test file")
            notify("No BehaviorSpec found in the test file.")
            return false
        }

        val testFqnResult = runReadAction {
            val behaviorClass = TestInsertionLocator.findBehaviorSpecClass(ktFile)
            behaviorClass?.fqName?.asString()
        }

        if (testFqnResult == null) {
            LOG.error("Could not determine test class FQN")
            notify("Could not determine test class name.")
            return false
        }

        LOG.debug("Test class FQN: $testFqnResult")

        val inserted = insertAtAnchor(ktFile, insertionPointResult, testCode)
        if (!inserted) {
            LOG.error("Failed to insert test code")
            notify("Failed to insert test code.")
            return false
        }

        val virtualFile = testFile.virtualFile
        if (virtualFile != null) {
            FileEditorManager.getInstance(project).openFile(virtualFile, true)
        }

        val runState = TddRunState.getInstance(project)
        runState.lastTestFile = virtualFile
        runState.lastTestClassFqn = testFqnResult

        val path = testFile.virtualFile?.path ?: testFile.name
        LOG.info("Successfully inserted test into $path")
        notify("Inserted test into $path")
        return true
    }

    override suspend fun insertImplementation(implCode: String): Boolean {
        LOG.info("insertImplementation() called with ${implCode.length} chars")

        val runState = TddRunState.getInstance(project)

        // Wrap PSI access in ReadAction
        val testFile = runReadAction {
            runState.lastTestFile
                ?.let { com.intellij.psi.PsiManager.getInstance(project).findFile(it) }
                ?: FileEditorManager.getInstance(project).selectedTextEditor
                    ?.let { PsiDocumentManager.getInstance(project).getPsiFile(it.document) }
        } ?: run {
            LOG.error("No test file available to locate production code")
            notify("No test file available to locate production code.")
            return false
        }

        LOG.debug("Test file for locating production: ${testFile.name}")

        val production = runReadAction {
            ProductionFileLocator.findProductionFile(project, testFile)
        } ?: run {
            LOG.error("No production file found for test")
            notify("No production file found for the test.")
            return false
        }

        LOG.info("Found production file: ${production.name}")

        val ktFile = production as? KtFile
        if (ktFile == null) {
            LOG.error("Production file is not a Kotlin file: ${production.name}")
            notify("Production file is not a Kotlin file.")
            return false
        }

        val className = ktFile.virtualFile?.nameWithoutExtension
        LOG.debug("Target class name: $className")

        val classOrObject = runReadAction {
            findTargetClass(ktFile, className)
        }
        if (classOrObject == null) {
            LOG.error("No class or object found in production file")
            notify("No class or object found to insert implementation.")
            return false
        }

        LOG.debug("Target class: ${classOrObject.name}")

        val body = runReadAction {
            ensureBody(classOrObject)
        }
        val anchor = body?.rBrace ?: run {
            LOG.error("No class body available for insertion")
            notify("No class body available for insertion.")
            return false
        }

        LOG.debug("Found insertion anchor at offset ${anchor.textRange.endOffset}")

        val inserted = insertAtAnchor(ktFile, anchor, implCode, indentLevelDelta = 1)
        if (!inserted) {
            LOG.error("Failed to insert implementation code")
            notify("Failed to insert implementation code.")
            return false
        }

        val virtualFile = production.virtualFile
        if (virtualFile != null) {
            FileEditorManager.getInstance(project).openFile(virtualFile, true)
        }

        runState.lastProductionFile = virtualFile
        val path = production.virtualFile?.path ?: production.name
        LOG.info("Successfully inserted implementation into $path")
        notify("Inserted implementation into $path")
        return true
    }

    private fun notify(message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("TDD Agent")
            .createNotification(message, NotificationType.INFORMATION)
            .notify(project)
    }

    private fun ensureBody(classOrObject: KtClassOrObject): KtClassBody? {
        classOrObject.body?.let { return it }
        val ktFile = classOrObject.containingFile as? KtFile ?: return null
        val document = PsiDocumentManager.getInstance(project).getDocument(ktFile) ?: return null
        val insertOffset = classOrObject.textRange.endOffset
        val className = classOrObject.name

        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.writeCommandAction(project, ktFile).run<RuntimeException> {
                document.insertString(insertOffset, " {\n}")
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }
        }

        // Force PSI reparse by finding the class again
        val updatedClass = PsiTreeUtil.findChildrenOfType(ktFile, KtClassOrObject::class.java)
            .find { it.name == className }
        return updatedClass?.body
    }

    private fun insertAtAnchor(
        ktFile: KtFile,
        anchor: PsiElement,
        code: String,
        indentLevelDelta: Int = 0
    ): Boolean {
        LOG.debug("insertAtAnchor: file=${ktFile.name}, offset=${anchor.textRange.endOffset}")

        val document = PsiDocumentManager.getInstance(project).getDocument(ktFile) ?: run {
            LOG.error("Could not get document for file ${ktFile.name}")
            return false
        }

        val anchorOffset = anchor.textRange.endOffset
        val baseIndent = lineIndent(document, anchorOffset)
        val indent = baseIndent + "    ".repeat(indentLevelDelta)
        val formatted = indentCode(code, indent)
        val insertText = "\n\n" + formatted + "\n"

        LOG.debug("Inserting ${insertText.length} bytes at offset $anchorOffset")

        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.writeCommandAction(project, ktFile).run<RuntimeException> {
                document.insertString(anchorOffset, insertText)
                PsiDocumentManager.getInstance(project).commitDocument(document)
                val range = TextRange(anchorOffset, anchorOffset + insertText.length)
                CodeStyleManager.getInstance(project).reformatText(ktFile, range.startOffset, range.endOffset)
                LOG.debug("Reformatted code range [${range.startOffset}, ${range.endOffset}]")

                // Optimize imports after insertion
                optimizeImports(ktFile)
            }
        }

        moveCaret(ktFile, anchorOffset + 1)
        LOG.info("Successfully inserted and formatted code at offset $anchorOffset")
        return true
    }

    private fun lineIndent(document: Document, offset: Int): String {
        val line = document.getLineNumber(offset)
        val start = document.getLineStartOffset(line)
        val end = document.getLineEndOffset(line)
        val text = document.charsSequence.subSequence(start, end)
        val wsLength = text.indexOfFirst { it != ' ' && it != '\t' }.let { if (it == -1) text.length else it }
        return text.subSequence(0, wsLength).toString()
    }

    private fun indentCode(code: String, indent: String): String {
        return code.lines().joinToString("\n") { line ->
            if (line.isBlank()) line else indent + line
        }
    }

    private fun moveCaret(file: KtFile, offset: Int) {
        val virtualFile = file.virtualFile ?: return
        val editor = FileEditorManager.getInstance(project).openTextEditor(
            com.intellij.openapi.fileEditor.OpenFileDescriptor(project, virtualFile, offset),
            true,
        )
        editor?.caretModel?.moveToOffset(offset)
    }

    private fun findTargetClass(file: KtFile, className: String?): KtClassOrObject? {
        val classes = PsiTreeUtil.findChildrenOfType(file, KtClassOrObject::class.java)
        if (!className.isNullOrBlank()) {
            classes.firstOrNull { it.name == className }?.let { return it }
        }
        return classes.firstOrNull()
    }

    private fun optimizeImports(ktFile: KtFile) {
        try {
            LOG.debug("Optimizing imports for ${ktFile.name}")
            // This forces Kotlin plugin to optimize imports and remove unused ones
            val codeStyleManager = CodeStyleManager.getInstance(project)
            codeStyleManager.reformat(ktFile)
            LOG.debug("Import optimization complete")
        } catch (e: Exception) {
            // Silently fail - import optimization is nice-to-have, not critical
            LOG.debug("Import optimization failed (non-critical): ${e.message}")
        }
    }
}
