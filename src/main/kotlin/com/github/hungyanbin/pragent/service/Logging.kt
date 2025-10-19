package com.github.hungyanbin.pragent.service

/**
 * Interface for error logging capabilities.
 *
 * Services that implement this interface can easily log errors with context
 * using the ErrorLogger service.
 *
 * Usage:
 * ```
 * class MyService : Logging {
 *     fun doSomething() {
 *         try {
 *             // ... operation
 *         } catch (e: Exception) {
 *             logError("Failed to do something", e, "doSomething operation")
 *         }
 *     }
 * }
 * ```
 */
interface Logging {

    /**
     * Logs an error with full context to the ErrorLogger service.
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
        ErrorLogger.getInstance().logError(message, throwable, context, metadata)
    }
}