package dev.agent.plugin

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.openapi.diagnostic.Logger
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
    companion object {
        private val LOG = Logger.getInstance(IdeCodeRunner::class.java)
    }
    override suspend fun runTests(): CodeRunner.Result {
        LOG.info("runTests() called")

        val runState = TddRunState.getInstance(project)
        val classFqn = runState.lastTestClassFqn
        LOG.debug("Last test class FQN: $classFqn")

        if (classFqn.isNullOrBlank()) {
            LOG.warn("No test class FQN available")
            return CodeRunner.Result(
                success = false,
                output = "",
                error = "No test class available. Generate and insert a test first.",
            )
        }

        val projectPath = project.basePath
        LOG.debug("Project path: $projectPath")

        if (projectPath.isNullOrBlank()) {
            LOG.error("Project base path is null or blank")
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

        LOG.info("Executing Gradle command: ${commandLine.commandLineString}")

        val result = withTimeoutOrNull(timeout) {
            runCommand(commandLine)
        }

        return if (result != null) {
            LOG.info("Test execution completed with result: success=${result.success}")
            result
        } else {
            LOG.error("Test execution timed out after ${timeout.inWholeSeconds}s")
            CodeRunner.Result(
                success = false,
                output = "",
                error = "Test execution timeout (${timeout.inWholeSeconds}s exceeded)",
            )
        }
    }

    private suspend fun runCommand(commandLine: GeneralCommandLine): CodeRunner.Result =
        suspendCancellableCoroutine { continuation ->
            val output = StringBuilder()
            var handler: OSProcessHandler? = null
            try {
                handler = OSProcessHandler(commandLine)
                LOG.debug("Created process handler for Gradle test execution")

                handler.addProcessListener(object : ProcessAdapter() {
                    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                        output.append(event.text)
                        LOG.debug("Process output: ${event.text.trim().take(200)}")
                    }

                    override fun processTerminated(event: ProcessEvent) {
                        val exitCode = event.exitCode
                        LOG.info("Gradle process terminated with exit code: $exitCode")
                        LOG.debug("Total output length: ${output.length} bytes")

                        if (exitCode != 0) {
                            LOG.debug("Process output (first 500 chars):\n${output.toString().take(500)}")
                        }

                        continuation.resume(
                            CodeRunner.Result(
                                success = exitCode == 0,
                                output = output.toString(),
                                error = if (exitCode == 0) null else "Tests failed (exit code $exitCode)",
                            ),
                        )
                    }
                })

                LOG.debug("Starting process notification")
                handler.startNotify()

                continuation.invokeOnCancellation {
                    LOG.info("Test execution cancelled, destroying process")
                    handler.destroyProcess()
                }
            } catch (e: Exception) {
                LOG.error("Exception during test execution", e)
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
