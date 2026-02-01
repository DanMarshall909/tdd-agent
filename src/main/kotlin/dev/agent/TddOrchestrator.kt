package dev.agent

import com.intellij.openapi.diagnostic.Logger

/**
 * Orchestrates the TDD workflow using abstracted components.
 * Controls the state machine for generating tests, running them, and generating implementations.
 */
class TddOrchestrator(
    private val llm: LlmAdapter,
    private val runner: CodeRunner,
    private val inserter: CodeInserter
) {
    companion object {
        private val LOG = Logger.getInstance(TddOrchestrator::class.java)
    }

    data class StepResult(
        val testCode: String?,
        val implCode: String?,
        val success: Boolean,
        val error: String?
    )

    suspend fun generateTestCode(bddStep: String): String {
        val prompt = buildTestPrompt(bddStep, null)
        return llm.generate(prompt)
    }

    suspend fun generateImplementationCode(testCode: String): String {
        val prompt = buildImplPrompt(testCode, null)
        return llm.generate(prompt)
    }

    suspend fun executeStep(bddStep: String): StepResult {
        return try {
            // Step 1: Generate test
            LOG.info("Step 1/5: Generating test code")
            val testPrompt = buildTestPrompt(bddStep, null)
            val testCode = llm.generate(testPrompt)
            LOG.info("Step 1/5 complete: Test code generated (${testCode.length} chars)")

            // Step 2: Insert test
            LOG.info("Step 2/5: Inserting test into test file")
            if (!inserter.insertTest(testCode)) {
                LOG.warn("Step 2/5 failed: Could not insert test")
                return StepResult(
                    testCode = testCode,
                    implCode = null,
                    success = false,
                    error = "Failed to insert test into test file",
                )
            }
            LOG.info("Step 2/5 complete: Test inserted")

            // Step 3: Run tests and verify they fail
            LOG.info("Step 3/5: Running tests (should fail)")
            if (!runner.verifyTestsFail()) {
                LOG.warn("Step 3/5 failed: Test should have failed but passed")
                return StepResult(
                    testCode = testCode,
                    implCode = null,
                    success = false,
                    error = "Test should have failed but passed",
                )
            }
            LOG.info("Step 3/5 complete: Test failed as expected")

            // Step 4: Generate implementation
            LOG.info("Step 4/5: Generating implementation code")
            val implPrompt = buildImplPrompt(testCode, null)
            val implCode = llm.generate(implPrompt)
            LOG.info("Step 4/5 complete: Implementation code generated (${implCode.length} chars)")

            // Step 5a: Insert implementation
            LOG.info("Step 5/5: Inserting implementation")
            if (!inserter.insertImplementation(implCode)) {
                LOG.warn("Step 5/5 failed: Could not insert implementation")
                return StepResult(
                    testCode = testCode,
                    implCode = implCode,
                    success = false,
                    error = "Failed to insert implementation",
                )
            }
            LOG.info("Step 5/5a complete: Implementation inserted")

            // Step 5b: Run tests and verify they pass
            LOG.info("Step 5/5b: Running tests (should pass)")
            if (!runner.verifyTestsPass()) {
                LOG.warn("Step 5/5b failed: Tests failed after implementation")
                return StepResult(
                    testCode = testCode,
                    implCode = implCode,
                    success = false,
                    error = "Tests failed after implementation",
                )
            }
            LOG.info("Step 5/5b complete: Tests passed")

            LOG.info("TDD cycle complete: All steps successful")
            StepResult(
                testCode = testCode,
                implCode = implCode,
                success = true,
                error = null,
            )
        } catch (e: Exception) {
            LOG.error("TDD cycle failed with exception", e)
            StepResult(
                testCode = null,
                implCode = null,
                success = false,
                error = e.message,
            )
        }
    }
}
