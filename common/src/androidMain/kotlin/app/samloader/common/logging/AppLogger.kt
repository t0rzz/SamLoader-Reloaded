package app.samloader.common.logging

import android.util.Log

/**
 * Actual implementation of AppLogger for Android.
 */
actual class AppLogger actual constructor(private val tag: String) {
    actual fun d(message: String) {
        Log.d(tag, message)
    }

    actual fun e(message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
    // Implement other levels as needed
}
