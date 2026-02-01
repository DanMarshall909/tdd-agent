package dev.agent.plugin

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import dev.agent.CodeRunner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume

/**
 * IDE implementation of CodeRunner using Gradle test execution.
 */
class IdeCodeRunner(private val project: Project) : CodeRunner {
    override suspend fun runTests(): CodeRunner.Result {
        val runState = TddRunState.getInstance(project)
        val classFqn = runState.lastTestClassFqn
        if (classFqn.isNullOrBlank()) {
            return CodeRunner.Result(
                success = false,
                output = "",
                error = "No test class available. Generate and insert a test first.",
            )
        }

        val projectPath = project.basePath
        if (projectPath.isNullOrBlank()) {
            return CodeRunner.Result(
                success = false,
                output = "",
                error = "Project path not found.",
            )
        }

        val gradleCmd = if (SystemInfo.isWindows) "gradlew.bat" else "./gradlew"
        val commandLine = GeneralCommandLine()
            .withWorkDirectory(projectPath)
            .withExePath(gradleCmd)
            .withParameters("test", "--tests", classFqn)
            .withCharset(StandardCharsets.UTF_8)

        return runCommand(commandLine)
    }

    private suspend fun runCommand(commandLine: GeneralCommandLine): CodeRunner.Result =
        suspendCancellableCoroutine { continuation ->
            val output = StringBuilder()
            try {
                val handler = OSProcessHandler(commandLine)
                handler.addProcessListener(object : ProcessAdapter() {
                    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                        output.append(event.text)
                    }

                    override fun processTerminated(event: ProcessEvent) {
                        val exitCode = event.exitCode
                        continuation.resume(
                            CodeRunner.Result(
                                success = exitCode == 0,
                                output = output.toString(),
                                error = if (exitCode == 0) null else "Tests failed (exit code $exitCode)",
                            ),
                        )
                    }
                })
                handler.startNotify()
            } catch (e: Exception) {
                continuation.resume(
                    CodeRunner.Result(
                        success = false,
                        output = output.toString(),
                        error = e.message,
                    ),
                )
            }
        }
}
