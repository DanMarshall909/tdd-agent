package dev.agent

import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println("TDD Agent CLI")
    println("Enter BDD step (or 'quit' to exit):")

    while (true) {
        print("> ")
        val input = readLine() ?: break

        if (input.lowercase() == "quit") break
        if (input.isBlank()) continue

        println("TODO: Generate test for: $input")
    }
}
