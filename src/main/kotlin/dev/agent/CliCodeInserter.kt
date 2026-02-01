package dev.agent

import org.springframework.stereotype.Component

/**
 * CLI implementation of CodeInserter.
 * For now, just prints the code and asks for confirmation.
 * Will be replaced with actual file insertion via IDE.
 */
@Component
class CliCodeInserter : CodeInserter {
    override suspend fun insertTest(testCode: String): Boolean {
        println("Generated test code:")
        println("───────────────────────────────────────")
        println(testCode)
        println("───────────────────────────────────────")
        print("Insert this test? (y/n): ")
        return readLine()?.trim()?.lowercase() == "y"
    }

    override suspend fun insertImplementation(implCode: String): Boolean {
        println("Generated implementation:")
        println("───────────────────────────────────────")
        println(implCode)
        println("───────────────────────────────────────")
        print("Insert this implementation? (y/n): ")
        return readLine()?.trim()?.lowercase() == "y"
    }
}
