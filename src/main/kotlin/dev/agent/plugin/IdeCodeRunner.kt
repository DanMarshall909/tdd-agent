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
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * IDE implementation of CodeRunner using Gradle test execution.
 */
class IdeCodeRunner(
    private val project: Project,
    private val timeout: Duration = 60.seconds
) : CodeRunner {
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

        // Gradle supports inner classes with $ separator (e.g., com.example.Outer$Inner)
        val gradleCmd = if (SystemInfo.isWindows) "gradlew.bat" else "./gradlew"
        val commandLine = GeneralCommandLine()
            .withWorkDirectory(projectPath)
            .withExePath(gradleCmd)
            .withParameters("test", "--tests", classFqn)
            .withCharset(StandardCharsets.UTF_8)

        val result = withTimeoutOrNull(timeout) {
            runCommand(commandLine)
        }
        return result ?: CodeRunner.Result(
            success = false,
            output = "",
            error = "Test execution timeout (${timeout.inWholeSeconds}s exceeded)",
        )
    }

    private suspend fun runCommand(commandLine: GeneralCommandLine): CodeRunner.Result =
        suspendCancellableCoroutine { continuation ->
            val output = StringBuilder()
            var handler: OSProcessHandler? = null
            try {
                handler = OSProcessHandler(commandLine)
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
                continuation.invokeOnCancellation {
                    handler.destroyProcess()
                }
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
