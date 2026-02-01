package dev.agent

import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val adapter = OpenCodeAdapter()

    println("╔════════════════════════════════════════╗")
    println("║         TDD Agent CLI v0.1.0           ║")
    println("╚════════════════════════════════════════╝")
    println()

    // Hardcoded test step for debugging
    val step = "user can login with valid credentials"

    println("📝 Generating test...")
    val prompt = buildTestPrompt(step, null)
    println("Prompt: $prompt")
    println()

    try {
        val testCode = adapter.chat(prompt)
        println("✓ Test generated:")
        println("───────────────────────────────────────")
        println(testCode)
        println("───────────────────────────────────────")
    } catch (e: Exception) {
        println("❌ Error: ${e.message}")
        e.printStackTrace()
    }
}
