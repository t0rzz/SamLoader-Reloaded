package app.samloader.common.downloader

import app.samloader.common.metadata.Changelog
import app.samloader.common.metadata.ChangelogHandler
import app.samloader.common.version.VersionFetch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Simple KMP-friendly ViewModel-like class (no AndroidX dependency). */
class DownloaderViewModel(
    private val lookup: LookupService = DefaultLookupService(),
) {
    interface LookupService {
        suspend fun latestFirmware(model: String, region: String): String
        suspend fun changelogs(model: String, region: String): Map<String, Changelog>?
    }

    class DefaultLookupService : LookupService {
        override suspend fun latestFirmware(model: String, region: String): String = VersionFetch.getLatest(model, region)
        override suspend fun changelogs(model: String, region: String): Map<String, Changelog>? = ChangelogHandler.getChangelog(model, region)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _model = MutableStateFlow("")
    val model: StateFlow<String> = _model

    private val _region = MutableStateFlow("")
    val region: StateFlow<String> = _region

    private val _firmware = MutableStateFlow("")
    val firmware: StateFlow<String> = _firmware

    private val _imeis = MutableStateFlow("")
    val imeis: StateFlow<String> = _imeis

    private val _osVersion = MutableStateFlow("")
    val osVersion: StateFlow<String> = _osVersion

    private val _changelog = MutableStateFlow<Changelog?>(null)
    val changelog: StateFlow<Changelog?> = _changelog

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun setModel(value: String) { _model.value = value }
    fun setRegion(value: String) { _region.value = value }
    fun setImeis(value: String) { _imeis.value = value }

    fun validateModel(): String? {
        val v = _model.value.trim().uppercase()
        return if (!Regex("^SM-[A-Z0-9]+$").matches(v)) "Invalid model format (expected SM-XXXX)" else null
    }

    fun validateImeis(): String? {
        val parts = _imeis.value.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val bad = parts.firstOrNull { !it.all { ch -> ch.isDigit() } || it.length !in 8..17 }
        return bad?.let { "Invalid IMEI/Serial: $it" }
    }

    fun refresh() {
        if (_loading.value) return
        val model = _model.value.trim().uppercase()
        val region = _region.value.trim().uppercase()
        if (model.isBlank() || region.isBlank()) { _error.value = "Model/Region required"; return }
        validateModel()?.let { _error.value = it; return }
        _loading.value = true
        _error.value = null
        scope.launch {
            runCatching {
                val latest = lookup.latestFirmware(model, region)
                _firmware.value = latest
                val logs = lookup.changelogs(model, region)
                val thisLog = logs?.get(latest.split('/').firstOrNull() ?: latest)
                    ?: logs?.values?.firstOrNull()
                _changelog.value = thisLog
                _osVersion.value = thisLog?.androidVer ?: ""
            }.onFailure { t ->
                _error.value = t.message ?: "Failed to refresh"
            }
            _loading.value = false
        }
    }
}