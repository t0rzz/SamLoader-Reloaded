package app.samloader.common.metadata

// Expect declaration for a platform-specific changelog fetcher.
// JVM/Android will provide a real implementation; other native targets can stub or return null.
expect object ChangelogHandler {
    suspend fun getChangelog(device: String, region: String): Map<String, Changelog>?
}