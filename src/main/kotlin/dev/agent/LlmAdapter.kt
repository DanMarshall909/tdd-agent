package dev.agent

/**
 * Abstraction for LLM code generation.
 * Implementations may use OpenCode, direct Claude API, or other providers.
 */
interface LlmAdapter {
    /**
     * Generate code based on a prompt.
     * @param prompt The generation prompt
     * @return Generated code snippet
     */
    suspend fun generate(prompt: String): String
}
