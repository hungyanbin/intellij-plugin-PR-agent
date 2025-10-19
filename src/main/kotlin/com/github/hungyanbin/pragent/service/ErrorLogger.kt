package com.github.hungyanbin.pragent.service

import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.collections.emptyList

/**
 * Listener interface for error log changes.
 */
interface ErrorLogListener {
    /**
     * Called when the error log list changes (errors added or cleared).
     */
    fun onErrorLogsChanged()
}

/**
 * Application-level service for logging and persisting errors.
 *
 * This service wraps IntelliJ's Logger and provides persistent storage
 * for error logs with full context (timestamp, stack trace, metadata).
 *
 * Usage:
 * ```
 * val errorLogger = ErrorLogger.getInstance()
 * errorLogger.logError("Failed to get branch", exception, "Getting current branch", mapOf("branch" to "main"))
 * ```
 */
@Service
@State(
    name = "ErrorLoggerState",
    storages = [Storage("prAgentErrorLogs.xml")]
)
class ErrorLogger : SerializablePersistentStateComponent<ErrorLoggerState>(ErrorLoggerState()) {

    private val logger = Logger.getInstance(ErrorLogger::class.java)
    private val listeners = mutableListOf<ErrorLogListener>()

    companion object {
        private const val MAX_ERROR_LOGS = 100

        /**
         * Gets the application-level instance of ErrorLogger.
         */
        fun getInstance(): ErrorLogger {
            return com.intellij.openapi.application.ApplicationManager.getApplication()
                .getService(ErrorLogger::class.java)
        }
    }

    /**
     * Adds a listener to be notified when error logs change.
     */
    fun addListener(listener: ErrorLogListener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    /**
     * Removes a previously registered listener.
     */
    fun removeListener(listener: ErrorLogListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    /**
     * Logs an error with full context and persists it.
     *
     * @param message Human-readable error message
     * @param throwable The exception that occurred (optional)
     * @param context Description of what operation was being performed (optional)
     * @param metadata Additional key-value context information (optional)
     */
    fun logError(
        message: String,
        throwable: Throwable? = null,
        context: String? = null,
        metadata: Map<String, String>? = null
    ) {
        // Log to IntelliJ's logger for immediate visibility in IDE logs
        if (throwable != null) {
            logger.error(buildLogMessage(message, context), throwable)
        } else {
            logger.error(buildLogMessage(message, context))
        }

        // Create error log entry
        val stackTrace = throwable?.let { getStackTraceAsString(it) } ?: "No stack trace available"
        val entry = ErrorLogEntry(
            errorMessage = message,
            stackTrace = stackTrace,
            operationContext = context,
            metadata = metadata ?: emptyMap()
        )

        // Add to persistent storage using copy-on-write pattern (FIFO - remove oldest if limit exceeded)
        updateState { currentState ->
            val newLogs = currentState.errorLogs.toMutableList().apply {
                add(entry)
                if (size > MAX_ERROR_LOGS) {
                    removeAt(0)
                }
            }
            currentState.copy(errorLogs = newLogs)
        }

        // Notify listeners
        notifyListeners()
    }

    /**
     * Gets all error logs in chronological order (oldest first).
     */
    fun getErrorLogs(): List<ErrorLogEntry> {
        return state.errorLogs
    }

    /**
     * Gets the total count of logged errors.
     */
    fun getErrorCount(): Int {
        return state.errorLogs.size
    }

    /**
     * Gets a specific error log by index (0-based).
     * Returns null if index is out of bounds.
     */
    fun getErrorById(index: Int): ErrorLogEntry? {
        return state.errorLogs.getOrNull(index)
    }

    /**
     * Clears all error logs.
     */
    fun clearAllLogs() {
        updateState { it.copy(errorLogs = emptyList()) }
        logger.info("All error logs cleared")
        notifyListeners()
    }

    /**
     * Notifies all registered listeners that error logs have changed.
     */
    private fun notifyListeners() {
        synchronized(listeners) {
            listeners.toList() // Create a copy to avoid concurrent modification
        }.forEach { listener ->
            try {
                listener.onErrorLogsChanged()
            } catch (e: Exception) {
                logger.error("Error notifying listener", e)
            }
        }
    }

    private fun buildLogMessage(message: String, context: String?): String {
        return if (context != null) {
            "[$context] $message"
        } else {
            message
        }
    }

    private fun getStackTraceAsString(throwable: Throwable): String {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        return sw.toString()
    }
}