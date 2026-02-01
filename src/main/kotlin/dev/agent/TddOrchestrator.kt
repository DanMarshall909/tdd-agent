package dev.agent

/**
 * Orchestrates the TDD workflow using abstracted components.
 * Controls the state machine for generating tests, running them, and generating implementations.
 */
class TddOrchestrator(
    private val llm: LlmAdapter,
    private val runner: CodeRunner,
    private val inserter: CodeInserter
) {
    data class StepResult(
        val testCode: String?,
        val implCode: String?,
        val success: Boolean,
        val error: String?
    )

    suspend fun executeStep(bddStep: String): StepResult {
        return try {
            // Generate test
            println("📝 Generating test...")
            val testPrompt = buildTestPrompt(bddStep, null)
            val testCode = llm.generate(testPrompt)
            println("✓ Test generated")

            // Insert test
            println("📋 Inserting test into test file...")
            if (!inserter.insertTest(testCode)) {
                return StepResult(
                    testCode = testCode,
                    implCode = null,
                    success = false,
                    error = "Failed to insert test into test file",
                )
            }

            // Run tests and verify they fail
            println("🧪 Running tests (should fail)...")
            if (!runner.verifyTestsFail()) {
                return StepResult(
                    testCode = testCode,
                    implCode = null,
                    success = false,
                    error = "Test should have failed but passed",
                )
            }
            println("✓ Test failed as expected")

            // Generate implementation
            println("💻 Generating implementation...")
            val implPrompt = buildImplPrompt(testCode, null)
            val implCode = llm.generate(implPrompt)
            println("✓ Implementation generated")

            // Insert implementation
            println("📝 Inserting implementation...")
            if (!inserter.insertImplementation(implCode)) {
                return StepResult(
                    testCode = testCode,
                    implCode = implCode,
                    success = false,
                    error = "Failed to insert implementation",
                )
            }

            // Run tests and verify they pass
            println("🧪 Running tests (should pass)...")
            if (!runner.verifyTestsPass()) {
                return StepResult(
                    testCode = testCode,
                    implCode = implCode,
                    success = false,
                    error = "Tests failed after implementation",
                )
            }
            println("✅ Tests passed!")

            StepResult(
                testCode = testCode,
                implCode = implCode,
                success = true,
                error = null,
            )
        } catch (e: Exception) {
            StepResult(
                testCode = null,
                implCode = null,
                success = false,
                error = e.message,
            )
        }
    }
}
