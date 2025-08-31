import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.samloader.common.Api
import app.samloader.common.version.VersionFetch
import app.samloader.common.fus.FusClient
import app.samloader.common.download.DownloadManager
import app.samloader.common.prefs.AppPrefs
import app.samloader.common.prefs.AppHistory

@Composable
fun AppIOS() {
    MaterialTheme {
        DuofrostIOSApp()
    }
}

@Composable
private fun DuofrostIOSApp() {
    val tabs = listOf("Check Update", "Download", "Decrypt", "History", "Settings")
    var selectedTab by remember { mutableStateOf(0) }

    // Shared UI state
    var model by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var imei by remember { mutableStateOf("") }
    var fw by remember { mutableStateOf("") }
    var downloadedUrl by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text(Api.appName()) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { i, title ->
                    Tab(selected = selectedTab == i, onClick = { if (!busy) selectedTab = i }, text = { Text(title) })
                }
            }
            when (selectedTab) {
                0 -> TabCheckUpdateIOS(
                    model = model,
                    region = region,
                    imei = imei,
                    onDeviceChanged = { m, r, i -> model = m; region = r; imei = i },
                    onLatestFound = { fw = it },
                    busy = busy,
                    setBusy = { busy = it }
                )
                1 -> TabDownloadIOS(
                    model = model,
                    region = region,
                    imei = imei,
                    fw = fw,
                    onDeviceChanged = { m, r, i -> model = m; region = r; imei = i },
                    onFwChanged = { fw = it },
                    onDownloadedUrl = { downloadedUrl = it },
                    busy = busy,
                    setBusy = { busy = it }
                )
                2 -> TabDecryptIOS(
                    model = model,
                    region = region,
                    imei = imei,
                    fw = fw,
                    defaultInUrl = downloadedUrl,
                    onDeviceChanged = { m, r, i -> model = m; region = r; imei = i },
                    onFwChanged = { fw = it },
                    busy = busy,
                    setBusy = { busy = it }
                )
                3 -> TabHistoryIOS()
                4 -> TabSettingsIOS()
            }
        }
    }
}

@Composable
private fun DeviceInputsIOS(
    model: String,
    region: String,
    imei: String,
    enabled: Boolean = true,
    onChanged: (model: String, region: String, imei: String) -> Unit = { _,_,_ -> }
) {
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        OutlinedTextField(value = model, onValueChange = { onChanged(it, region, imei) }, label = { Text("Model") }, placeholder = { Text("e.g., SM-S918B") }, modifier = Modifier.fillMaxWidth(), enabled = enabled)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = region, onValueChange = { onChanged(model, it.uppercase(), imei) }, label = { Text("Region (CSC)") }, placeholder = { Text("e.g., BTU/ITV/INS") }, modifier = Modifier.fillMaxWidth(), enabled = enabled)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = imei, onValueChange = { onChanged(model, region, it) }, label = { Text("IMEI prefix or serial") }, modifier = Modifier.fillMaxWidth(), enabled = enabled)
    }
}

@Composable
private fun TabCheckUpdateIOS(
    model: String,
    region: String,
    imei: String,
    onDeviceChanged: (String, String, String) -> Unit,
    onLatestFound: (String) -> Unit,
    busy: Boolean,
    setBusy: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    var latestDisplay by remember { mutableStateOf("-") }

    Column(Modifier.fillMaxSize()) {
        DeviceInputsIOS(model = model, region = region, imei = imei, enabled = !busy, onChanged = onDeviceChanged)
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                if (model.isBlank() || region.isBlank()) { latestDisplay = "Missing model/region"; return@Button }
                setBusy(true)
                scope.launch {
                    runCatching { VersionFetch.getLatest(model, region) }
                        .onSuccess { ver ->
                            val norm = VersionFetch.normalize(ver)
                            onLatestFound(norm)
                            latestDisplay = "AP/CSC/CP/Build: $norm"
                        }
                        .onFailure { latestDisplay = "Error: ${it.message ?: "failed"}" }
                    setBusy(false)
                }
            }, enabled = !busy) { Text(if (busy) "Checking…" else "Check latest version") }
            Spacer(Modifier.width(16.dp))
            Text("Latest: $latestDisplay")
        }
    }
}

@Composable
private fun TabDownloadIOS(
    model: String,
    region: String,
    imei: String,
    fw: String,
    onDeviceChanged: (String, String, String) -> Unit,
    onFwChanged: (String) -> Unit,
    onDownloadedUrl: (String) -> Unit,
    busy: Boolean,
    setBusy: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }

    var folderUrl by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var fileSize by remember { mutableStateOf(0L) }
    var progress by remember { mutableStateOf(0f) }
    var downloading by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        DeviceInputsIOS { m, r, i -> model = m; region = r; imei = i }
        Column(Modifier.padding(12.dp)) {
            OutlinedTextField(value = fw, onValueChange = { fw = it }, label = { Text("Firmware version") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = folderUrl, onValueChange = {}, enabled = false, label = { Text("Destination folder URL") }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Button(onClick = { FilePickerIOS.pickFolder { sel -> if (sel != null) folderUrl = sel } }) { Text("Select Folder…") }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    if (fw.isBlank() || model.isBlank() || region.isBlank()) return@Button
                    status = "Preparing…"
                    scope.launch {
                        runCatching {
                            val fus = FusClient(); fus.generateNonce()
                            val info = fus.binaryInform(fw, model, region, imei)
                            fileName = info.filename
                            fileSize = info.size
                            status = "Prepared: ${info.filename} — ${String.format("%.2f", info.size / (1024.0 * 1024.0))} MiB"
                        }.onFailure { status = "Error: ${it.message ?: "failed"}" }
                    }
                }, enabled = !downloading) { Text("Prepare") }
                Spacer(Modifier.width(12.dp))
                Button(onClick = {
                    if (fileName.isBlank() || folderUrl.isBlank()) return@Button
                    downloading = true
                    progress = 0f
                    status = "Downloading…"
                    scope.launch {
                        runCatching {
                            val fus = FusClient(); fus.generateNonce()
                            val outUrl = FileIOIOS.childUrl(folderUrl, fileName) ?: error("Invalid destination")
                            val out = FileIOIOS.openOutput(outUrl, append = false) ?: error("Cannot open output")
                            var done = 0L
                            val startMs = platform.Foundation.NSDate().timeIntervalSince1970 * 1000.0
                            try {
                                DownloadManager.download(
                                    fus,
                                    modelPathAndName = "${'$'}{""}".let { // will be replaced
                                        // Not accessible here; we must obtain path from server again to ensure correctness
                                        // Re-fetch minimal BinaryInform quickly
                                        val info = fus.binaryInform(fw, model, region, imei)
                                        info.path + info.filename
                                    },
                                    start = 0L,
                                    endInclusive = null,
                                    write = { chunk -> FileIOIOS.writeChunk(out.stream, chunk) },
                                    onProgress = { delta ->
                                        done += delta
                                        val total = if (fileSize > 0) fileSize else 1L
                                        progress = (done.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
                                        val nowMs = platform.Foundation.NSDate().timeIntervalSince1970 * 1000.0
                                        val elapsedSec = ((nowMs - startMs).coerceAtLeast(1.0)) / 1000.0
                                        val bps = if (elapsedSec > 0.0) done.toDouble() / elapsedSec else null
                                        val eta = if (bps != null && bps > 0.0) (((total - done).toDouble() / bps)).toLong() else null
                                        val line = app.samloader.common.util.Format.compositeProgress(done, total, bps, eta, 1)
                                        status = "$fileName — $line"
                                    }
                                )
                                status = "Completed: $fileName"
                            } finally {
                                out.stopAccess()
                            }
                        }.onFailure { status = "Error: ${it.message ?: "failed"}" }
                        downloading = false
                    }
                }, enabled = !downloading && fileName.isNotBlank() && folderUrl.isNotBlank()) { Text("Start download") }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
            Text(status)
        }
    }
}

@Composable
private fun TabDecryptIOS(
    model: String,
    region: String,
    imei: String,
    fw: String,
    defaultInUrl: String,
    onDeviceChanged: (String, String, String) -> Unit,
    onFwChanged: (String) -> Unit,
    busy: Boolean,
    setBusy: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    var model by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var imei by remember { mutableStateOf("") }

    var fw by remember { mutableStateOf("") }
    var encVer by remember { mutableStateOf("4") }
    var status by remember { mutableStateOf("") }

    var inUrl by remember { mutableStateOf("") }
    var outFolderUrl by remember { mutableStateOf("") }
    var outName by remember { mutableStateOf("firmware.zip") }
    var progress by remember { mutableStateOf(0f) }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        DeviceInputsIOS { m, r, i -> model = m; region = r; imei = i }
        OutlinedTextField(value = fw, onValueChange = { fw = it }, label = { Text("Firmware version") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enc ver")
            Spacer(Modifier.width(8.dp))
            DropdownSelectorIOS(options = listOf("2","4"), selected = encVer) { encVer = it }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = inUrl, onValueChange = {}, enabled = false, label = { Text("Encrypted file URL") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick = { FilePickerIOS.pickFile(allowedTypes = listOf("enc2","enc4","zip")) { sel -> if (sel != null) inUrl = sel } }) { Text("Select File…") }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = outFolderUrl, onValueChange = {}, enabled = false, label = { Text("Output folder URL") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick = { FilePickerIOS.pickFolder { sel -> if (sel != null) outFolderUrl = sel } }) { Text("Select Folder…") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = outName, onValueChange = { outName = it.ifBlank { "firmware.zip" } }, label = { Text("Output filename") })
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            busy = true
            progress = 0f
            val enc = encVer.toIntOrNull() ?: 4
            scope.launch {
                runCatching {
                    val totalLen = FileIOIOS.fileLength(inUrl) ?: 0L
                    require(totalLen > 0L && totalLen % 16L == 0L) { "Invalid input size" }
                    val input = FileIOIOS.openInput(inUrl) ?: error("Cannot open input")
                    val outUrl = FileIOIOS.childUrl(outFolderUrl, outName) ?: error("Invalid output")
                    val output = FileIOIOS.openOutput(outUrl, append = false) ?: error("Cannot open output")
                    try {
                        val key: ByteArray = if (enc == 2) {
                            app.samloader.common.auth.Auth.v2Key(fw, model, region)
                        } else {
                            val fus = FusClient(); fus.generateNonce(); fus.getV4Key(fw, model, region, imei)
                        }
                        var remaining = totalLen
                        app.samloader.common.crypt.decryptProgress(
                            read = { FileIOIOS.readChunk(input.stream, 4096).also { if (it != null) remaining -= it.size } },
                            write = { chunk -> FileIOIOS.writeChunk(output.stream, chunk) },
                            key = key,
                            totalLen = totalLen,
                            onProgress = { _ ->
                                progress = (1f - (remaining.toDouble() / totalLen.toDouble()).toFloat()).coerceIn(0f, 1f)
                            }
                        )
                        status = "Decryption complete: $outName"
                    } finally {
                        input.stopAccess(); output.stopAccess()
                    }
                }.onFailure { status = "Error: ${it.message ?: "failed"}" }
                busy = false
            }
        }, enabled = !busy && fw.isNotBlank() && inUrl.isNotBlank() && outFolderUrl.isNotBlank() && outName.isNotBlank() && model.isNotBlank() && region.isNotBlank()) { Text("Start decryption") }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
        Text(status)
    }
}

@Composable
private fun TabHistoryIOS() {
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
private fun TabSettingsIOS() {
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
            OutlinedTextField(value = defThreads.toString(), onValueChange = { v -> defThreads = v.toIntOrNull()?.coerceIn(1,10) ?: 1 }, modifier = Modifier.width(80.dp))
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
private fun DropdownSelectorIOS(options: List<String>, selected: String, onSelected: (String) -> Unit) {
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
