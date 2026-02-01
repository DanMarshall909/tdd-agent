package dev.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.util.concurrent.TimeUnit

class OpenCodeAdapter(
    private val model: String? = null,
    private val timeout: Duration = Duration.ofMinutes(5)
) {
    suspend fun chat(prompt: String): String = withContext(Dispatchers.IO) {
        val cmd = buildCommand()
        println("🔍 OpenCode command: ${cmd.joinToString(" ")}")
        println("📄 Prompt: ${prompt.take(100)}...")

        val process = ProcessBuilder(cmd)
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .start()

        // Write prompt to stdin
        process.outputStream.bufferedWriter().use { writer ->
            writer.write(prompt)
        }

        // Read output in a background thread to prevent deadlock
        val outputFuture = java.util.concurrent.CompletableFuture.supplyAsync {
            process.inputStream.bufferedReader().readText()
        }

        val errorFuture = java.util.concurrent.CompletableFuture.supplyAsync {
            process.errorStream.bufferedReader().readText()
        }

        val completed = process.waitFor(timeout.seconds, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            throw TimeoutException("OpenCode call exceeded ${timeout.seconds}s timeout")
        }

        val exitCode = process.exitValue()
        println("⏹️  Process exited with code: $exitCode")

        val output = outputFuture.get()
        val errors = errorFuture.get()

        println("📊 Output length: ${output.length} bytes")
        println("📊 Error output length: ${errors.length} bytes")

        if (output.isNotBlank()) {
            println("📋 OpenCode Output:")
            println(output)
        }

        if (errors.isNotBlank()) {
            println("📋 OpenCode Errors:")
            println(errors)
        }

        if (output.isBlank() && errors.isBlank()) {
            println("⚠️  No output received from opencode")
        }
        println()

        if (exitCode != 0) {
            throw RuntimeException("OpenCode exited with code $exitCode")
        }

        parseResponse(output)
    }

    private fun buildCommand(): List<String> {
        val opencodePath = resolveOpencodePath()
        val cmd = mutableListOf(
            opencodePath,
            "run",
            "--format",
            "json",
            "--print-logs",
            "--log-level",
            "DEBUG",
        )
        if (model != null) {
            cmd.addAll(listOf("--model", model))
        }
        return cmd
    }

    private fun resolveOpencodePath(): String {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val pathEnv = System.getenv("PATH") ?: ""
        val pathDirs = pathEnv.split(System.getProperty("path.separator"))

        val executableName = if (isWindows) "opencode.cmd" else "opencode"

        for (dir in pathDirs) {
            val candidate = java.io.File(dir, executableName)
            if (candidate.exists() && candidate.isFile) {
                return candidate.absolutePath
            }
        }

        return "opencode"
    }

    fun parseResponse(output: String): String {
        return try {
            val json = Json.parseToJsonElement(output).jsonObject
            json["code"]?.jsonPrimitive?.content
                ?: json["text"]?.jsonPrimitive?.content
                ?: json["result"]?.jsonPrimitive?.content
                ?: throw RuntimeException("No code field in response")
        } catch (e: Exception) {
            throw RuntimeException("Failed to parse OpenCode response: ${e.message}", e)
        }
    }
}

class TimeoutException(message: String) : RuntimeException(message)
