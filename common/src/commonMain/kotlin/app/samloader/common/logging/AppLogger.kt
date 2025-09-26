package app.samloader.common.logging

/**
 * Expected class for platform-specific logging.
 */
expect class AppLogger(tag: String) {
    fun d(message: String)
    fun e(message: String, throwable: Throwable? = null)
    // Add other levels like w, i, v as needed
}
