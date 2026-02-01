package dev.agent.plugin

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDocumentManager
import dev.agent.CodeInserter

/**
 * Stub implementation of CodeInserter for IDE integration.
 * M2: Shows notifications. Real implementation in M3.
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

        val path = testFile.virtualFile?.path ?: testFile.name
        notify("Test file ready: $path. Insertion not implemented yet.")
        return true
    }

    override suspend fun insertImplementation(implCode: String): Boolean {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("TDD Agent")
            .createNotification(
                "Implementation insertion not yet implemented",
                "Real PSI manipulation coming in M3",
            )
            .notify(project)
        return true // Pretend success for M2
    }

    private fun notify(message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("TDD Agent")
            .createNotification(message, NotificationType.INFORMATION)
            .notify(project)
    }
}
