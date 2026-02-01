package dev.agent

fun buildTestPrompt(step: String, context: String? = null): String = """
    Generate a single Kotest BehaviorSpec test block for this BDD step:

    Step: $step
    ${context?.let { "Context:\n$it\n" } ?: ""}
    Return ONLY the given/when/then block. No imports, no class wrapper, no comments.

    Use Kotest matchers like: shouldBe, shouldBeInstanceOf, shouldThrow, shouldContain
    Use MockK for mocks: every { }, verify { }
    Keep the test minimal and focused on the step.

    Example format (adapt to the step):
    given("condition") {
        val setup = ...
        `when`("action") {
            val result = ...
            then("outcome") {
                result shouldBe expected
            }
        }
    }
""".trimIndent()

fun buildImplPrompt(test: String, error: String? = null): String = """
    You are an AI coding agent. Make this test pass with the MINIMAL code change required.
    You may reason through multiple steps, but only output the final code body.
    Do not modify unrelated behavior or add extra features.

    Test:
    $test
    ${error?.let { "\nError:\n$it\n" } ?: ""}
    Return ONLY the function/method body. No class wrapper, no imports.
    Write the simplest code that passes. No over-engineering or extra features.

    Example output (just the body):
    val user = userRepo.findByEmail(email)
    if (user?.passwordExpired == true) return PasswordExpired
    // ...
""".trimIndent()

fun buildScenarioPrompt(featureDescription: String, additionalRequirements: String? = null): String = """
    You are an AI agent. Generate BDD scenarios (Gherkin style) from this feature description.
    Focus on business requirements only, not internal implementation details.

    Feature:
    $featureDescription
    ${additionalRequirements?.let { "\nAdditional Requirements:\n$it\n" } ?: ""}

    Return ONLY valid JSON: an array of scenarios with name and steps.
    Each step must include a type (GIVEN, WHEN, THEN, AND) and text.
    No markdown, no commentary, no code fences.

    Example JSON:
    [
      {
        "name": "Password reset email",
        "steps": [
          { "type": "GIVEN", "text": "a registered user" },
          { "type": "WHEN", "text": "they request a password reset" },
          { "type": "THEN", "text": "they receive a reset email" }
        ]
      }
    ]
""".trimIndent()
