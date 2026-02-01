package dev.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import kotlin.time.Duration as KotlinDuration
import kotlin.time.toKotlinDuration

class OpenCodeAdapter(
    private val model: String? = null,
    private val timeout: Duration = Duration.ofMinutes(5)
) {
    suspend fun chat(prompt: String): String = withContext(Dispatchers.IO) {
        // TODO: Execute: opencode run --format json "<prompt>"
        // TODO: Parse response
        // TODO: Return generated code
        throw NotImplementedError("OpenCode integration not yet implemented")
    }
}
