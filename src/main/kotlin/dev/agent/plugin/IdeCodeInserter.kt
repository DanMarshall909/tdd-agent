package dev.agent.plugin

import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.project.Project
import dev.agent.CodeInserter

/**
 * Stub implementation of CodeInserter for IDE integration.
 * M2: Shows notifications. Real implementation in M3.
 */
class IdeCodeInserter(private val project: Project) : CodeInserter {
    override suspend fun insertTest(testCode: String): Boolean {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("TDD Agent")
            .createNotification(
                "Test insertion not yet implemented",
                "Real PSI manipulation coming in M3",
            )
            .notify(project)
        return true // Pretend success for M2
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
}
