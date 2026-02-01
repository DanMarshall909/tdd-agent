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
        val cmd = buildCommand(prompt)
        println("🔍 OpenCode: ${cmd.joinToString(" ")}")
        val process = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .start()
        process.outputStream.close()

        val completed = process.waitFor(timeout.seconds, TimeUnit.SECONDS)
        if (!completed) {
            process.destroyForcibly()
            throw TimeoutException("OpenCode call exceeded ${timeout.seconds}s timeout")
        }

        val exitCode = process.exitValue()
        println("⏹️  Process exited with code: $exitCode")

        val output = process.inputStream.bufferedReader().readText()
        println("📊 Output length: ${output.length} bytes")

        if (output.isNotBlank()) {
            println("📋 OpenCode Output:")
            println(output)
            println()
        } else {
            println("⚠️  No output received from opencode")
        }

        if (exitCode != 0) {
            throw RuntimeException("OpenCode exited with code $exitCode: $output")
        }

        parseResponse(output)
    }

    private fun buildCommand(prompt: String): List<String> {
        val opencodePath = resolveOpencodePath()
        val cmd = mutableListOf(
            opencodePath,
            "run",
            "--format",
            "json",
            "--print-logs",
            "--log-level",
            "DEBUG",
            prompt,
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
