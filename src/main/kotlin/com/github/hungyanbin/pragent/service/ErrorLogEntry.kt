package com.github.hungyanbin.pragent.service

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Represents a single error log entry with full context.
 */
data class ErrorLogEntry(
    val timestamp: String = Instant.now().toString(),
    val errorMessage: String,
    val stackTrace: String,
    val operationContext: String? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    /**
     * Returns a human-readable formatted timestamp.
     * Example: "Oct 18, 2025 2:30:45 PM"
     */
    fun getFormattedTimestamp(): String {
        return try {
            val instant = Instant.parse(timestamp)
            val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy h:mm:ss a")
                .withZone(ZoneId.systemDefault())
            formatter.format(instant)
        } catch (e: Exception) {
            timestamp // Fallback to raw timestamp if parsing fails
        }
    }

    /**
     * Returns a formatted string of all metadata key-value pairs.
     */
    fun getFormattedMetadata(): String {
        if (metadata.isEmpty()) return ""
        return metadata.entries.joinToString("\n") { "${it.key}: ${it.value}" }
    }
}