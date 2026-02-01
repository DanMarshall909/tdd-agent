package dev.agent

import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val adapter = OpenCodeAdapter()

    println("╔════════════════════════════════════════╗")
    println("║         TDD Agent CLI v0.1.0           ║")
    println("╚════════════════════════════════════════╝")
    println()

    var testCode: String? = null
    var error: String? = null

    while (true) {
        println("Enter BDD step (or 'quit' to exit):")
        print("> ")
        val step = readLine()?.trim() ?: break

        if (step.lowercase() == "quit") {
            println("Goodbye!")
            break
        }
        if (step.isBlank()) continue

        // Generate Test
        println()
        println("📝 Generating test...")
        testCode = try {
            val prompt = buildTestPrompt(step, error)
            adapter.chat(prompt)
        } catch (e: Exception) {
            println("❌ Error: ${e.message}")
            continue
        }

        println("✓ Test generated:")
        println("───────────────────────────────────────")
        println(testCode)
        println("───────────────────────────────────────")
        println()

        // Verify Test Failed
        println("Did the test fail? (y/n)")
        print("> ")
        val testFailed = readLine()?.trim()?.lowercase() == "y"
        if (!testFailed) {
            println("⚠️  Test should fail first. Try again with better test.")
            println()
            continue
        }

        println()

        // Generate Implementation
        println("💻 Generating implementation...")
        val implCode = try {
            val prompt = buildImplPrompt(testCode!!, error)
            adapter.chat(prompt)
        } catch (e: Exception) {
            println("❌ Error: ${e.message}")
            error = e.message
            continue
        }

        println("✓ Implementation generated:")
        println("───────────────────────────────────────")
        println(implCode)
        println("───────────────────────────────────────")
        println()

        // Verify Tests Pass
        println("Do all tests pass now? (y/n)")
        print("> ")
        val testsPassed = readLine()?.trim()?.lowercase() == "y"
        if (!testsPassed) {
            println("⚠️  Tests should pass. Try again.")
            error = "Implementation did not make tests pass"
            println()
            continue
        }

        println()
        println("✅ Step complete! Ready for next one.")
        println()
        error = null
    }
}
