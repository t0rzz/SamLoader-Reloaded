package app.samloader.common.metadata

actual object ChangelogHandler {
    actual suspend fun getChangelog(device: String, region: String): Map<String, Changelog>? = null
}
