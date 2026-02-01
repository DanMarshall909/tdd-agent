package dev.agent.plugin

import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.project.Project
import dev.agent.CodeRunner

/**
 * Stub implementation of CodeRunner for IDE integration.
 * M2: Shows notifications, returns mock success. Real implementation in M3.
 */
class IdeCodeRunner(private val project: Project) : CodeRunner {
    override suspend fun runTests(): CodeRunner.Result {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("TDD Agent")
            .createNotification(
                "Test execution not yet implemented",
                "Real test running coming in M3",
            )
            .notify(project)

        // Mock success for M2
        return CodeRunner.Result(
            success = true,
            output = "Mock test execution (real implementation in M3)",
            error = null,
        )
    }
}
