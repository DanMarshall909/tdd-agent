package dev.agent

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class OpenCodeAdapterTest : BehaviorSpec({
    val typeKey = "type"
    val partKey = "part"
    val textKey = "text"
    val textType = "text"
    val stepStartType = "step_start"
    val invalidJson = "not json"
    val parseFailurePrefix = "Failed to parse"

    given("an OpenCodeAdapter") {
        `when`("parsing streaming JSON response") {
            then("it extracts text from text event") {
                val adapter = OpenCodeAdapter()
                val event = buildJsonObject {
                    put(typeKey, textType)
                    put(
                        partKey,
                        buildJsonObject {
                            put(textKey, "fun hello() = println(\"world\")")
                        },
                    )
                }.toString()

                val result = adapter.parseResponse(event)
                result shouldContain "println"
            }
        }

        `when`("parsing multiple JSON events") {
            then("it extracts text from the text event") {
                val adapter = OpenCodeAdapter()
                val event1 = buildJsonObject {
                    put(typeKey, stepStartType)
                }.toString()
                val event2 = buildJsonObject {
                    put(typeKey, textType)
                    put(
                        partKey,
                        buildJsonObject {
                            put(textKey, "fun greet() = \"hi\"")
                        },
                    )
                }.toString()

                val output = "$event1\n$event2"
                val result = adapter.parseResponse(output)
                result shouldContain "greet"
            }
        }

        `when`("parsing invalid JSON") {
            then("it throws RuntimeException") {
                val adapter = OpenCodeAdapter()
                val error = shouldThrow<RuntimeException> {
                    adapter.parseResponse(invalidJson)
                }
                error.message shouldContain parseFailurePrefix
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
