@file:OptIn(androidx.compose.material.ExperimentalMaterialApi::class)
package dev.t0rzz.samloaderreloaded.ui

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import android.util.Log // Added import for Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.t0rzz.samloaderreloaded.util.SafUtils
import androidx.core.content.ContextCompat
import app.samloader.common.Api
import app.samloader.common.data.Regions
import app.samloader.common.version.VersionFetch
import app.samloader.common.fus.FusClient
import app.samloader.common.download.DownloadManager
import app.samloader.common.prefs.AppPrefs
import app.samloader.common.prefs.AppHistory
import kotlinx.coroutines.launch
import app.samloader.common.util.Format
import kotlin.math.roundToLong
// Compose Material icons (extension properties) imports
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DuofrostApp() {
    val tabs = listOf("Home", "Download", "Decrypter", "History", "More")
    var selectedTab by remember { mutableStateOf(0) }

    // Shared UI state hoisted at top-level to persist across tabs
    var model by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var imei by remember { mutableStateOf("") }
    var fw by remember { mutableStateOf("") } // normalized version prefilled in Download/Decrypt
    var downloadedInUri by remember { mutableStateOf("") } // encrypted file URI after download
    var busy by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val appScope = rememberCoroutineScope()
    Scaffold(topBar = {
        TopAppBar(title = { Text(Api.appName()) })
    }, bottomBar = {
        BottomNavigation {
            tabs.forEachIndexed { i, label ->
                BottomNavigationItem(
                    selected = selectedTab == i,
                    onClick = { if (!busy) selectedTab = i },
                    icon = {
                        val icon = when (i) {
                            0 -> Icons.Filled.Home
                            1 -> Icons.Filled.Download
                            2 -> Icons.Filled.VpnKey
                            3 -> Icons.Filled.History
                            else -> Icons.Filled.MoreHoriz
                        }
                        Icon(icon, contentDescription = null)
                    },
                    label = { Text(label) }
                )
            }
        }
    }, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> dev.t0rzz.samloaderreloaded.ui.downloader.DownloaderScreen(
                    onStartDownload = { m, r, i, f ->
                        model = m; region = r; imei = i; fw = f
                        selectedTab = 1
                    }
                )
                1 -> TabDownload(
                    model = model,
                    region = region,
                    imei = imei,
                    fw = fw,
                    onDeviceChanged = { m, r, i -> model = m; region = r; imei = i },
                    onFwChanged = { fw = it },
                    onDownloadedUri = { downloadedInUri = it },
                    busy = busy,
                    setBusy = { busy = it },
                    reportError = { msg -> appScope.launch { snackbarHostState.showSnackbar(msg.take(300)) } }
                )
                2 -> TabDecrypt(
                    model = model,
                    region = region,
                    imei = imei,
                    fw = fw,
                    defaultInUri = downloadedInUri,
                    onDeviceChanged = { m, r, i -> model = m; region = r; imei = i },
                    onFwChanged = { fw = it },
                    setBusy = { busy = it },
                    busy = busy,
                    reportError = { msg -> appScope.launch { snackbarHostState.showSnackbar(msg.take(300)) } }
                )
                3 -> TabHistory()
                4 -> TabSettings()
            }
        }
    }
}

@Composable
private fun DeviceInputs(
    model: String,
    region: String,
    imei: String,
    enabled: Boolean = true,
    onChanged: (model: String, region: String, imei: String) -> Unit = { _,_,_ -> }
) {
    var regions by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        runCatching { Regions.getRegions().keys.sorted() }.onSuccess { regions = it }
    }

    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        OutlinedTextField(value = model, onValueChange = {
            onChanged(it, region, imei)
        }, label = { Text("Model") }, placeholder = { Text("e.g., SM-S918B") }, modifier = Modifier.fillMaxWidth(), enabled = enabled)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = region, onValueChange = {
            onChanged(model, it.uppercase(), imei)
        }, label = { Text("Region (CSC)") }, placeholder = { Text("e.g., BTU/ITV/INS") }, modifier = Modifier.fillMaxWidth(), enabled = enabled)
        if (regions.isNotEmpty()) {
            Text(text = "Known: ${regions.take(15).joinToString(", ")}…", style = MaterialTheme.typography.caption)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = imei, onValueChange = {
            onChanged(model, region, it)
        }, label = { Text("IMEI prefix or serial") }, modifier = Modifier.fillMaxWidth(), enabled = enabled)
    }
}

@Composable
private fun TabCheckUpdate(
    model: String,
    region: String,
    imei: String,
    onDeviceChanged: (String, String, String) -> Unit,
    onLatestFound: (String) -> Unit,
    busy: Boolean,
    setBusy: (Boolean) -> Unit,
    reportError: (String) -> Unit
) {
    var latestDisplay by remember { mutableStateOf("-") }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        DeviceInputs(model = model, region = region, imei = imei, enabled = !busy, onChanged = onDeviceChanged)
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                if (model.isBlank() || region.isBlank()) {
                    val errorMsg = "Missing model/region"
                    latestDisplay = errorMsg
                    reportError(errorMsg)
                    return@Button
                }
                setBusy(true)
                scope.launch {
                    runCatching { VersionFetch.getLatest(model, region) }
                        .onSuccess { ver ->
                            val norm = VersionFetch.normalize(ver)
                            onLatestFound(norm)
                            latestDisplay = formatLatest(norm)
                        }
                        .onFailure {
                            val errorMsg = "Error: ${it.message ?: "failed"}"
                            latestDisplay = errorMsg
                            reportError(errorMsg)
                        }
                    setBusy(false)
                }
            }, enabled = !busy) {
                Text(if (busy) "Checking…" else "Check latest version")
            }
            Spacer(Modifier.width(16.dp))
            Text("Latest: $latestDisplay")
        }
    }
}

@Composable
private fun TabDownload(
    model: String,
    region: String,
    imei: String,
    fw: String,
    onDeviceChanged: (String, String, String) -> Unit,
    onFwChanged: (String) -> Unit,
    onDownloadedUri: (String) -> Unit,
    busy: Boolean,
    setBusy: (Boolean) -> Unit,
    reportError: (String) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    // Legacy storage permission helper for API <= 32
    var permDeniedMsg by remember { mutableStateOf("") }
    val requestStoragePerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) permDeniedMsg = "Storage permission denied"
    }
    fun ensureLegacyReadPerm(): Boolean {
        if (Build.VERSION.SDK_INT <= 32) {
            val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestStoragePerm.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                return false
            }
        }
        return true
    }
    var outDir by remember { mutableStateOf("") }
    var outDirDisplay by remember { mutableStateOf("") }
    var threads by remember { mutableStateOf(1) }
    var resume by remember { mutableStateOf(false) }
    var autoDec by remember { mutableStateOf(false) }

    var progress by remember { mutableStateOf(0f) }
    var stats by remember { mutableStateOf("") }

    val pickDirLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            SafUtils.persistTreePermission(ctx, uri)
            AppPrefs.setAndroidOutTreeUri(uri.toString())
            outDir = uri.toString()
            outDirDisplay = SafUtils.getReadablePathFromTreeUri(ctx, uri)
        }
    }
    var pendingInfo by remember { mutableStateOf<FusClient.BinaryInfo?>(null) }
    val pickOutFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val info = pendingInfo
        if (uri == null || info == null) {
            setBusy(false)
            return@rememberLauncherForActivityResult
        }
        // Start streaming download to the selected URI
        val resolver = ctx.contentResolver
        stats = "Downloading…"
        val size = info.size.coerceAtLeast(1L)
        val startMs = System.currentTimeMillis()
        scope.launch {
            runCatching {
                resolver.openOutputStream(uri, if (resume) "wa" else "w").use { os ->
                    requireNotNull(os) { "Failed to open output stream" }
                    val fus = FusClient() // regenerate nonce to be safe
                    fus.generateNonce()
                    var done = 0L
                    if (threads > 1) {
                        DownloadManager.downloadWithThreads(
                            fus,
                            info.path + info.filename,
                            size = info.size,
                            threads = threads,
                            write = { chunk -> os.write(chunk) },
                            onProgress = { delta ->
                                done += delta
                                progress = (done.toDouble() / size.toDouble()).toFloat().coerceIn(0f, 1f)
                                val elapsedSec = ((System.currentTimeMillis() - startMs).coerceAtLeast(1)).toDouble() / 1000.0
                                val bps = if (elapsedSec > 0) done.toDouble() / elapsedSec else null
                                val eta = if (bps != null && bps > 0.0) ((size - done).toDouble() / bps).roundToLong() else null
                                val line = Format.compositeProgress(done, size, bps, eta, threads)
                                stats = "${info.filename} — $line"
                            }
                        )
                    } else {
                        DownloadManager.download(
                            fus,
                            info.path + info.filename,
                            start = 0L,
                            endInclusive = null,
                            write = { chunk -> os.write(chunk) },
                            onProgress = { delta ->
                                done += delta
                                progress = (done.toDouble() / size.toDouble()).toFloat().coerceIn(0f, 1f)
                                val elapsedSec = ((System.currentTimeMillis() - startMs).coerceAtLeast(1)).toDouble() / 1000.0
                                val bps = if (elapsedSec > 0) done.toDouble() / elapsedSec else null
                                val eta = if (bps != null && bps > 0.0) ((size - done).toDouble() / bps).roundToLong() else null
                                val line = Format.compositeProgress(done, size, bps, eta, threads)
                                stats = "${info.filename} — $line"
                            }
                        )
                    }
                }
                stats = "Completed: ${info.filename}"
                AppHistory.add("Downloaded ${info.filename} for $model/$region")
                onDownloadedUri(uri.toString())
            }.onFailure {
                val msg = "Download error: ${it.message ?: "failed"}"
                stats = "Error: ${it.message ?: "failed"}"
                reportError(msg)
            }
            setBusy(false)
        }
    }

    // Restore persisted tree selection if available
    LaunchedEffect(Unit) {
        val saved = AppPrefs.getAndroidOutTreeUri()
        if (saved.isNotBlank() && SafUtils.isPersisted(ctx, saved)) {
            outDir = saved
            outDirDisplay = SafUtils.getReadablePathFromTreeUri(ctx, Uri.parse(saved))
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        DeviceInputs(model = model, region = region, imei = imei, enabled = !busy, onChanged = onDeviceChanged)
        Column(Modifier.padding(12.dp)) {
            OutlinedTextField(value = fw, onValueChange = { onFwChanged(it) }, enabled = !busy, label = { Text("Firmware version") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = outDirDisplay.ifBlank { outDir }, onValueChange = {}, enabled = false, label = { Text("Output directory") }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Button(onClick = { if (ensureLegacyReadPerm()) pickDirLauncher.launch(null) else permDeniedMsg = "Storage permission required" }, enabled = !busy) { Text("Browse…") }
            }
            Text("Shown path is derived from selected storage; the app stores the permission (URI) — files are written via Android SAF.", style = MaterialTheme.typography.caption)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Threads")
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = threads.toString(),
                    onValueChange = { v -> threads = v.toIntOrNull()?.coerceIn(1,10) ?: 1 },
                    modifier = Modifier.width(80.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.width(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = resume, onCheckedChange = { resume = it })
                    Text("Resume")
                }
                Spacer(Modifier.width(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = autoDec, onCheckedChange = { autoDec = it })
                    Text("Auto-decrypt after download")
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                if (fw.isBlank() || model.isBlank() || region.isBlank()) return@Button
                setBusy(true)
                progress = 0f
                stats = "Preparing…"
                scope.launch {
                    runCatching {
                        val fus = FusClient()
                        fus.generateNonce()
                        val info = fus.binaryInform(fw, model, region, imei)
                        Log.d("DownloadDebug", "BinaryInfo: path=${info.path}, filename=${info.filename}, size=${info.size}") // Added Log statement
                        pendingInfo = info
                        val sizeMb = (info.size.toDouble() / (1024.0 * 1024.0))
                        stats = "${info.filename} — ${String.format("%.2f", sizeMb)} MiB (server)"
                        if (outDir.isNotBlank() && SafUtils.isPersisted(ctx, outDir)) {
                            // Write directly into selected SAF folder
                            val fileUri = SafUtils.createOrFindFile(ctx, Uri.parse(outDir), "application/octet-stream", info.filename)
                            if (fileUri != null) {
                                // Reuse the CreateDocument result handler path by launching a small block inline
                                pendingInfo = info
                                // Start streaming to created file
                                val resolver = ctx.contentResolver
                                stats = "Downloading…"
                                val size = info.size.coerceAtLeast(1L)
                                val startMs = System.currentTimeMillis()
                                scope.launch {
                                    runCatching {
                                        resolver.openOutputStream(fileUri, if (resume) "wa" else "w").use { os ->
                                            requireNotNull(os) { "Failed to open output stream" }
                                            val fus = FusClient() // regenerate nonce to be safe
                                            fus.generateNonce()
                                            var done = 0L
                                            if (threads > 1) {
                                                DownloadManager.downloadWithThreads(
                                                    fus,
                                                    info.path + info.filename,
                                                    size = info.size,
                                                    threads = threads,
                                                    write = { chunk -> os.write(chunk) },
                                                    onProgress = { delta ->
                                                        done += delta
                                                        progress = (done.toDouble() / size.toDouble()).toFloat().coerceIn(0f, 1f)
                                                        val elapsedSec = ((System.currentTimeMillis() - startMs).coerceAtLeast(1)).toDouble() / 1000.0
                                                        val bps = if (elapsedSec > 0) done.toDouble() / elapsedSec else null
                                                        val eta = if (bps != null && bps > 0.0) ((size - done).toDouble() / bps).roundToLong() else null
                                                        val line = Format.compositeProgress(done, size, bps, eta, threads)
                                                        stats = "${info.filename} — $line"
                                                    }
                                                )
                                            } else {
                                                DownloadManager.download(
                                                    fus,
                                                    info.path + info.filename,
                                                    start = 0L,
                                                    endInclusive = null,
                                                    write = { chunk -> os.write(chunk) },
                                                    onProgress = { delta ->
                                                        done += delta
                                                        progress = (done.toDouble() / size.toDouble()).toFloat().coerceIn(0f, 1f)
                                                        val elapsedSec = ((System.currentTimeMillis() - startMs).coerceAtLeast(1)).toDouble() / 1000.0
                                                        val bps = if (elapsedSec > 0) done.toDouble() / elapsedSec else null
                                                        val eta = if (bps != null && bps > 0.0) ((size - done).toDouble() / bps).roundToLong() else null
                                                        val line = Format.compositeProgress(done, size, bps, eta, threads)
                                                        stats = "${info.filename} — $line"
                                                    }
                                                )
                                            }
                                        }
                                        stats = "Completed: ${info.filename}"
                                        AppHistory.add("Downloaded ${info.filename} for $model/$region")
                                        onDownloadedUri(fileUri.toString())
                                    }.onFailure {
                                        val msg = "Download error: ${it.message ?: "failed"}"
                                        stats = "Error: ${it.message ?: "failed"}"
                                        reportError(msg)
                                    }
                                    setBusy(false)
                                }
                            } else {
                                // Fallback to CreateDocument if create failed
                                pickOutFile.launch(info.filename)
                            }
                        } else {
                            // Ask user for destination file and start streaming
                            pickOutFile.launch(info.filename)
                        }
                    }.onFailure {
                        stats = "Error: ${it.message ?: "failed"}"
                        setBusy(false)
                    }
                }
            }, enabled = !busy && fw.isNotBlank() && model.isNotBlank() && region.isNotBlank()) {
                Text("Start download")
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
            Text(stats)
            if (permDeniedMsg.isNotBlank()) {
                Text(permDeniedMsg)
            }
        }

    }
}

@Composable
private fun TabDecrypt(
    model: String,
    region: String,
    imei: String,
    fw: String,
    defaultInUri: String,
    onDeviceChanged: (String, String, String) -> Unit,
    onFwChanged: (String) -> Unit,
    busy: Boolean,
    setBusy: (Boolean) -> Unit,
    reportError: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    // Legacy storage permission helper for API <= 32
    var permDeniedMsg by remember { mutableStateOf("") }
    val requestStoragePerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) permDeniedMsg = "Storage permission denied"
    }
    fun ensureLegacyReadPerm(): Boolean {
        if (Build.VERSION.SDK_INT <= 32) {
            val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestStoragePerm.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                return false
            }
        }
        return true
    }
    var encVer by remember { mutableStateOf("4") }
    var inFile by remember { mutableStateOf(defaultInUri) }
    var outFile by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }

    val pickIn = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        inFile = uri?.toString() ?: inFile
    }
    val pickOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        outFile = uri?.toString() ?: outFile
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        // Device inputs
        DeviceInputs(model = model, region = region, imei = imei, enabled = !busy, onChanged = onDeviceChanged)
        OutlinedTextField(value = fw, onValueChange = { onFwChanged(it) }, enabled = !busy, label = { Text("Firmware version") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enc ver")
            Spacer(Modifier.width(8.dp))
            DropdownSelector(options = listOf("2","4"), selected = encVer) { encVer = it }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = inFile, onValueChange = {}, enabled = false, label = { Text("Encrypted file (URI)") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick = { if (ensureLegacyReadPerm()) pickIn.launch(arrayOf("*/*")) else permDeniedMsg = "Storage permission required" }, enabled = !busy) { Text("Browse…") }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = outFile, onValueChange = {}, enabled = false, label = { Text("Output file (URI)") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick = { if (ensureLegacyReadPerm()) pickOut.launch("firmware.zip") else permDeniedMsg = "Storage permission required" }) { Text("Browse…") }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            if (!ensureLegacyReadPerm()) { permDeniedMsg = "Storage permission required"; return@Button }
            setBusy(true)
            progress = 0f
            val enc = encVer.toIntOrNull() ?: 4
            val inUri = Uri.parse(inFile)
            val outUri = Uri.parse(outFile)
            val resolver = ctx.contentResolver
            // Obtain total length via AssetFileDescriptor (required for proper PKCS7 handling)
            val afd = resolver.openAssetFileDescriptor(inUri, "r")
            val totalLen = afd?.length ?: -1L
            afd?.close()
            if (totalLen <= 0L || totalLen % 16L != 0L) {
                // Best-effort: cannot proceed without a valid multiple-of-16 length
                setBusy(false)
                 reportError("Invalid input file length for decryption.")
                return@Button
            }
            scope.launch {
                runCatching {
                    resolver.openInputStream(inUri).use { ins ->
                        resolver.openOutputStream(outUri, "w").use { outs ->
                            requireNotNull(ins)
                            requireNotNull(outs)
                            val key: ByteArray = if (enc == 2) {
                                app.samloader.common.auth.Auth.v2Key(fw, model, region)
                            } else {
                                val fus = FusClient()
                                fus.generateNonce()
                                fus.getV4Key(fw, model, region, imei)
                            }
                            var remaining = totalLen
                            val read: () -> ByteArray? = {
                                val toRead = if (remaining >= 4096) 4096 else remaining.toInt()
                                if (toRead <= 0) null else {
                                    val buf = ByteArray(toRead)
                                    val n = ins.read(buf)
                                    if (n <= 0) null else {
                                        remaining -= n
                                        if (n == buf.size) buf else buf.copyOf(n)
                                    }
                                }
                            }
                            val write: (ByteArray) -> Unit = { chunk -> outs.write(chunk); outs.flush() }
                            app.samloader.common.crypt.decryptProgress(
                                read = read,
                                write = write,
                                key = key,
                                totalLen = totalLen,
                                onProgress = { delta ->
                                    progress = (1f - (remaining.toDouble() / totalLen.toDouble()).toFloat()).coerceIn(0f, 1f)
                                }
                            )
                            AppHistory.add("Decrypted to $outFile")
                        }
                    }
                }.onFailure {
                    reportError("Decrypt error: ${it.message ?: "failed"}")
                }
                setBusy(false)
            }
        }, enabled = !busy && fw.isNotBlank() && inFile.isNotBlank() && outFile.isNotBlank() && model.isNotBlank() && region.isNotBlank()) { Text("Start decryption") }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
        if (permDeniedMsg.isNotBlank()) {
            Text(permDeniedMsg)
        }
    }
}

@Composable
private fun TabHistory() {
    var items by remember { mutableStateOf(listOf<String>()) }
    LaunchedEffect(Unit) {
        items = AppHistory.getAll()
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                if (items.isNotEmpty()) {
                    AppHistory.removeFirst()
                    items = AppHistory.getAll()
                }
            }) { Text("Delete Selected (top)") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                AppHistory.clear()
                items = emptyList()
            }) { Text("Clear All") }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(items.size) { idx ->
                ListItem(text = { Text(items[idx]) })
                Divider()
            }
        }
    }
}

@Composable
private fun TabSettings() {
    var defThreads by remember { mutableStateOf(1) }
    var autoDec by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        defThreads = AppPrefs.getDefaultThreads()
        autoDec = AppPrefs.getAutoDecrypt()
    }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Default threads")
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(value = defThreads.toString(), onValueChange = { v -> defThreads = v.toIntOrNull()?.coerceIn(1,10) ?: 1 },
                modifier = Modifier.width(80.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = autoDec, onCheckedChange = { autoDec = it })
            Text("Auto-decrypt by default")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { AppPrefs.setDefaultThreads(defThreads); AppPrefs.setAutoDecrypt(autoDec) }) { Text("Save Settings") }
    }
}

@Composable
private fun DropdownSelector(options: List<String>, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box { 
        OutlinedButton(onClick = { expanded = true }) { Text(selected) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(onClick = { onSelected(opt); expanded = false }) { Text(opt) }
            }
        }
    }
}
