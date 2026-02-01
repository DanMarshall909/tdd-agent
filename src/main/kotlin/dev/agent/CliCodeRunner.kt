package dev.agent

/**
 * CLI implementation of CodeRunner.
 * For now, returns mock results for testing the flow.
 * Will be replaced with actual test execution via Gradle.
 */
class CliCodeRunner : CodeRunner {
    override suspend fun runTests(): CodeRunner.Result {
        // TODO: Implement actual test execution via Gradle
        // For now, simulate user interaction
        println("Running tests...")
        print("Did tests pass? (y/n): ")
        val passed = readLine()?.trim()?.lowercase() == "y"

        return CodeRunner.Result(
            success = passed,
            output = "Tests executed",
            error = if (!passed) "Some tests failed" else null,
        )
    }
}
