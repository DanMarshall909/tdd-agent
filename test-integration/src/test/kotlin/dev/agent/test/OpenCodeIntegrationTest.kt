package dev.agent.test

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Integration test to verify OpenCode CLI is callable and returns responses.
 */
class OpenCodeIntegrationTest : BehaviorSpec({
    given("OpenCode CLI is available") {
        `when`("calling opencode with a simple prompt") {
            then("it should return a non-empty response") {
                runBlocking {
                    val result = callOpenCode("Say hello", Duration.ofSeconds(30))
                    result.isNotEmpty() shouldBe true

                    // Parse first line and verify it's JSON
                    val firstLine = result.lines().first { it.isNotBlank() }
                    val json = Json.parseToJsonElement(firstLine).jsonObject
                    json["type"]?.jsonPrimitive?.content shouldBe "step_start"
                }
            }
        }

        `when`("calling opencode for code generation") {
            then("it should return text events with generated code") {
                runBlocking {
                    val result = callOpenCode("Write a simple add function", Duration.ofSeconds(30))
                    result.isNotEmpty() shouldBe true

                    // Find text event
                    var foundTextEvent = false
                    result.lines().forEach { line ->
                        if (line.isNotBlank()) {
                            val json = Json.parseToJsonElement(line).jsonObject
                            if (json["type"]?.jsonPrimitive?.content == "text") {
                                foundTextEvent = true
                            }
                        }
                    }
                    foundTextEvent shouldBe true
                }
            }
        }
    }
})

/**
 * Call OpenCode and return raw JSON output.
 */
private suspend fun callOpenCode(prompt: String, timeout: Duration): String {
    val cmd = listOf("opencode", "run", "--format", "json", prompt)
    val process = ProcessBuilder(cmd)
        .redirectInput(ProcessBuilder.Redirect.PIPE)
        .start()

    // Close stdin
    process.outputStream.bufferedWriter().use { writer ->
        writer.write(prompt)
    }

    // Read output
    val outputFuture = java.util.concurrent.CompletableFuture.supplyAsync {
        process.inputStream.bufferedReader().readText()
    }

    val completed = process.waitFor(timeout.seconds, TimeUnit.SECONDS)
    if (!completed) {
        process.destroyForcibly()
        throw RuntimeException("OpenCode exceeded ${timeout.seconds}s timeout")
    }

    return outputFuture.get()
}
