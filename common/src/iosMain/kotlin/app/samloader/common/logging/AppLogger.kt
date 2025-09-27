package app.samloader.common.logging

actual class AppLogger actual constructor(private val tag: String) {
    actual fun d(message: String) {
        println("D/$tag: $message")
    }

    actual fun e(message: String, throwable: Throwable?) {
        println("E/$tag: $message")
        throwable?.printStackTrace()
    }
}
