package dev.agent.workflow

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class ScenarioParserTest : BehaviorSpec({
    val expectedFirstScenarioCount = 1
    val expectedStepCount = 3
    val resetScenarioName = "Reset"

    given("a scenario parser") {
        `when`("parsing a clean JSON array") {
            then("it returns scenarios") {
                val json = """
                    [
                      {
                        "name": "Login",
                        "steps": [
                          { "type": "GIVEN", "text": "a user" },
                          { "type": "WHEN", "text": "they sign in" },
                          { "type": "THEN", "text": "they see a dashboard" }
                        ]
                      }
                    ]
                """.trimIndent()

                val scenarios = ScenarioParser.parse(json)
                scenarios.size shouldBe expectedFirstScenarioCount
                scenarios.first().steps.size shouldBe expectedStepCount
            }
        }

        `when`("parsing JSON wrapped with extra text") {
            then("it extracts the array and parses") {
                val raw = """
                    Some preface text.
                    [
                      {
                        "name": "Reset",
                        "steps": [
                          { "type": "GIVEN", "text": "a user" },
                          { "type": "WHEN", "text": "they request reset" },
                          { "type": "THEN", "text": "they receive email" }
                        ]
                      }
                    ]
                    trailing text
                """.trimIndent()

                val scenarios = ScenarioParser.parse(raw)
                scenarios.first().name shouldBe resetScenarioName
            }
        }
    }
})
