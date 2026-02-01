package dev.agent.plugin

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
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
    override suspend fun insertTest(testCode: String): Boolean {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        if (editor == null) {
            notify("No active editor. Open a production file to locate its test file.")
            return false
        }

        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document)
        if (psiFile == null) {
            notify("No PSI file found for the current editor.")
            return false
        }

        val existingTest = TestFileLocator.findTestFile(project, psiFile)
        val testFile = if (existingTest != null) {
            existingTest
        } else {
            val baseName = psiFile.virtualFile?.nameWithoutExtension ?: psiFile.name
            val response = Messages.showYesNoDialog(
                project,
                "No test file found for $baseName. Create ${baseName}Test.kt in test sources?",
                "Create Test File",
                null,
            )
            if (response != Messages.YES) {
                return false
            }

            TestFileLocator.createTestFile(project, psiFile)
                ?: run {
                    notify("Failed to create test file. Check your test source roots.")
                    return false
                }
        }

        val ktFile = testFile as? KtFile
        if (ktFile == null) {
            notify("Test file is not a Kotlin file.")
            return false
        }

        val insertionPoint = TestInsertionLocator.findInsertionPoint(ktFile)
        if (insertionPoint == null) {
            notify("No BehaviorSpec found in the test file.")
            return false
        }

        val behaviorClass = TestInsertionLocator.findBehaviorSpecClass(ktFile)
        val testFqn = behaviorClass?.fqName?.asString()
        if (testFqn == null) {
            notify("Could not determine test class name.")
            return false
        }

        val inserted = insertAtAnchor(ktFile, insertionPoint, testCode)
        if (!inserted) {
            notify("Failed to insert test code.")
            return false
        }

        val virtualFile = testFile.virtualFile
        if (virtualFile != null) {
            FileEditorManager.getInstance(project).openFile(virtualFile, true)
        }

        val runState = TddRunState.getInstance(project)
        runState.lastTestFile = virtualFile
        runState.lastTestClassFqn = testFqn

        val path = testFile.virtualFile?.path ?: testFile.name
        notify("Inserted test into $path")
        return true
    }

    override suspend fun insertImplementation(implCode: String): Boolean {
        val runState = TddRunState.getInstance(project)
        val testFile = runState.lastTestFile?.let { com.intellij.psi.PsiManager.getInstance(project).findFile(it) }
            ?: run {
                val editor = FileEditorManager.getInstance(project).selectedTextEditor
                editor?.let { PsiDocumentManager.getInstance(project).getPsiFile(it.document) }
            }
            ?: run {
                notify("No test file available to locate production code.")
                return false
            }

        val production = ProductionFileLocator.findProductionFile(project, testFile)
            ?: run {
                notify("No production file found for the test.")
                return false
            }

        val ktFile = production as? KtFile
        if (ktFile == null) {
            notify("Production file is not a Kotlin file.")
            return false
        }

        val className = ktFile.virtualFile?.nameWithoutExtension
        val classOrObject = findTargetClass(ktFile, className)
        if (classOrObject == null) {
            notify("No class or object found to insert implementation.")
            return false
        }

        val body = ensureBody(classOrObject)
        val anchor = body?.rBrace ?: run {
            notify("No class body available for insertion.")
            return false
        }
        val inserted = insertAtAnchor(ktFile, anchor, implCode, indentLevelDelta = 1)
        if (!inserted) {
            notify("Failed to insert implementation code.")
            return false
        }

        val virtualFile = production.virtualFile
        if (virtualFile != null) {
            FileEditorManager.getInstance(project).openFile(virtualFile, true)
        }

        runState.lastProductionFile = virtualFile
        val path = production.virtualFile?.path ?: production.name
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
        val document = PsiDocumentManager.getInstance(project).getDocument(ktFile) ?: return false
        val anchorOffset = anchor.textRange.endOffset
        val baseIndent = lineIndent(document, anchorOffset)
        val indent = baseIndent + "    ".repeat(indentLevelDelta)
        val formatted = indentCode(code, indent)
        val insertText = "\n\n" + formatted + "\n"

        ApplicationManager.getApplication().invokeAndWait {
            WriteCommandAction.writeCommandAction(project, ktFile).run<RuntimeException> {
                document.insertString(anchorOffset, insertText)
                PsiDocumentManager.getInstance(project).commitDocument(document)
                val range = TextRange(anchorOffset, anchorOffset + insertText.length)
                CodeStyleManager.getInstance(project).reformatText(ktFile, range.startOffset, range.endOffset)
            }
        }

        moveCaret(ktFile, anchorOffset + 1)
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
}
