package dev.agent.workflow

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

object ScenarioParser {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parse(raw: String): List<Scenario> {
        val jsonText = extractJsonArray(raw)
        return json.decodeFromString(jsonText)
    }

    private fun extractJsonArray(raw: String): String {
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        if (start == -1 || end == -1 || end < start) {
            throw IllegalArgumentException("Expected a JSON array of scenarios")
        }
        return raw.substring(start, end + 1)
    }
}
