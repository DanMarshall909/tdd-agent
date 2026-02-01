package dev.agent

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class OpenCodeAdapterTest : BehaviorSpec({
    given("an OpenCodeAdapter") {
        `when`("parsing streaming JSON response") {
            then("it extracts text from text event") {
                val adapter = OpenCodeAdapter()
                val event = buildJsonObject {
                    put("type", "text")
                    put(
                        "part",
                        buildJsonObject {
                            put("text", "fun hello() = println(\"world\")")
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
                    put("type", "step_start")
                }.toString()
                val event2 = buildJsonObject {
                    put("type", "text")
                    put(
                        "part",
                        buildJsonObject {
                            put("text", "fun greet() = \"hi\"")
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
                var threwException = false
                try {
                    adapter.parseResponse("not json")
                } catch (e: RuntimeException) {
                    threwException = true
                    e.message shouldContain "Failed to parse"
                }
                threwException shouldBe true
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
