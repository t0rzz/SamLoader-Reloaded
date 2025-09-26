package app.samloader.common.network

actual fun isHttpDebug(): Boolean {
    // Default to false on Native; can be enhanced to check getenv if needed
    return false
}
