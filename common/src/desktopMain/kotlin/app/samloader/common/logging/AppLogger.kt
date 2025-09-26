package app.samloader.common.logging

import java.text.SimpleDateFormat
import java.util.Date

/**
 * Actual implementation of AppLogger for Desktop (JVM).
 */
actual class AppLogger actual constructor(private val tag: String) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")

    actual fun d(message: String) {
        println("${dateFormat.format(Date())} D/$tag: $message")
    }

    actual fun e(message: String, throwable: Throwable?) {
        println("${dateFormat.format(Date())} E/$tag: $message")
        throwable?.printStackTrace(System.out)
    }
    // Implement other levels as needed
}
