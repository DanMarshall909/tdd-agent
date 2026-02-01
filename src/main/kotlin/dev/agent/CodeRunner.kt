package dev.agent

/**
 * Abstraction for running and verifying code execution.
 * Implementations may use IDE runners, CLI execution, or test frameworks.
 */
interface CodeRunner {
    data class Result(
        val success: Boolean,
        val output: String,
        val error: String?
    )

    /**
     * Run tests and return results.
     * @return Test execution result
     */
    suspend fun runTests(): Result

    /**
     * Run tests and verify they fail (for TDD).
     * @return True if tests failed as expected
     */
    suspend fun verifyTestsFail(): Boolean {
        val result = runTests()
        return !result.success
    }

    /**
     * Run tests and verify they pass.
     * @return True if all tests passed
     */
    suspend fun verifyTestsPass(): Boolean {
        val result = runTests()
        return result.success
    }
}
