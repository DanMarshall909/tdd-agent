package dev.agent

import kotlinx.coroutines.runBlocking
import org.springframework.boot.runApplication

fun main(args: Array<String>) = runBlocking {
    val context = runApplication<TddApplication>(*args)
    val orchestrator = context.getBean(TddOrchestrator::class.java)

    println("╔════════════════════════════════════════╗")
    println("║         TDD Agent CLI v0.1.0           ║")
    println("╚════════════════════════════════════════╝")
    println()

    while (true) {
        println("Enter BDD step (or 'quit' to exit):")
        print("> ")
        val step = readLine()?.trim() ?: break

        if (step.lowercase() == "quit") {
            println("Goodbye!")
            break
        }
        if (step.isBlank()) continue

        println()
        val result = orchestrator.executeStep(step)
        println()

        when {
            result.success -> {
                println("✅ Step complete!")
                println("  Test: ${result.testCode?.take(50)}...")
                println("  Impl: ${result.implCode?.take(50)}...")
            }
            result.error != null -> {
                println("❌ Error: ${result.error}")
            }
        }
        println()
    }
}
