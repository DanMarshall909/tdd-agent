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
    Make this test pass with MINIMAL code.

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
