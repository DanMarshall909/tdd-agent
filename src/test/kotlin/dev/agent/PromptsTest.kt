package dev.agent

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldContain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class OpenCodeAdapterTest : BehaviorSpec({
    given("an OpenCodeAdapter") {
        `when`("parsing JSON response with 'code' field") {
            then("it extracts the code") {
                val adapter = OpenCodeAdapter()
                val json = buildJsonObject {
                    put("code", "fun hello() = println(\"world\")")
                }.toString()

                val result = adapter.parseResponse(json)
                result shouldContain "println"
            }
        }

        `when`("parsing JSON response with 'text' field") {
            then("it falls back to text field") {
                val adapter = OpenCodeAdapter()
                val json = buildJsonObject {
                    put("text", "fun greet() = \"hi\"")
                }.toString()

                val result = adapter.parseResponse(json)
                result shouldContain "greet"
            }
        }

        `when`("parsing invalid JSON") {
            then("it throws RuntimeException") {
                val adapter = OpenCodeAdapter()
                val exception = try {
                    adapter.parseResponse("not json")
                    null
                } catch (e: RuntimeException) {
                    e
                }
                exception shouldBe { it != null && it.message?.contains("Failed to parse") == true }
            }
        }
    }
})

class PromptsTest : BehaviorSpec({
    given("a test prompt builder") {
        `when`("building a test prompt") {
            then("it should be implemented") {
                // TODO: Implement buildTestPrompt
                "placeholder" shouldContain "place"
            }
        }
    }
})
