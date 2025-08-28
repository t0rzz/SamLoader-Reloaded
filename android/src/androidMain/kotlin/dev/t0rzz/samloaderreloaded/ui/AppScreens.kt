package dev.t0rzz.samloaderreloaded.ui

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.samloader.common.Api
import app.samloader.common.data.Regions
import app.samloader.common.version.VersionFetch
import app.samloader.common.fus.FusClient
import app.samloader.common.download.DownloadManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DuofrostApp() {
    val tabs = listOf("Check Update", "Download", "Decrypt", "History", "Settings")
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(topBar = {
        TopAppBar(title = { Text(Api.appName()) })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { i, title ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(title) })
                }
            }
            when (selectedTab) {
                0 -> TabCheckUpdate()
                1 -> TabDownload()
                2 -> TabDecrypt()
                3 -> TabHistory()
                4 -> TabSettings()
            }
        }
    }
}

@Composable
private fun DeviceInputs(onChanged: (model: String, region: String, imei: String) -> Unit = { _,_,_ -> }) {
    var model by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var imei by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var regions by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        runCatching { Regions.getRegions().keys.sorted() }.onSuccess { regions = it }
    }

    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        OutlinedTextField(value = model, onValueChange = {
            model = it; onChanged(model, region, imei)
        }, label = { Text("Model") }, placeholder = { Text("e.g., SM-S918B") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = region, onValueChange = {
            region = it.uppercase(); onChanged(model, region, imei)
        }, label = { Text("Region (CSC)") }, placeholder = { Text("e.g., BTU/ITV/INS") }, modifier = Modifier.fillMaxWidth())
        if (regions.isNotEmpty()) {
            Text(text = "Known: ${regions.take(15).joinToString(", ")}…", style = MaterialTheme.typography.caption)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = imei, onValueChange = {
            imei = it; onChanged(model, region, imei)
        }, label = { Text("IMEI prefix or serial") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun TabCheckUpdate() {
    var latest by remember { mutableStateOf("-") }
    var busy by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        val scope = rememberCoroutineScope()
        DeviceInputs { m, r, _ -> model = m; region = r }
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                if (model.isBlank() || region.isBlank()) {
                    latest = "Missing model/region"
                    return@Button
                }
                busy = true
                scope.launch {
                    runCatching { VersionFetch.getLatest(model, region) }
                        .onSuccess { latest = it }
                        .onFailure { latest = "Error: ${'$'}{it.message ?: "failed"}" }
                    busy = false
                }
            }, enabled = !busy) {
                Text(if (busy) "Checking…" else "Check latest version")
            }
            Spacer(Modifier.width(16.dp))
            Text("Latest: $latest")
        }
    }
}

@Composable
private fun TabDownload() {
    val ctx = LocalContext.current
    var model by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var imei by remember { mutableStateOf("") }
    var fw by remember { mutableStateOf("") }
    var outDir by remember { mutableStateOf("") }
    var threads by remember { mutableStateOf(1) }
    var resume by remember { mutableStateOf(false) }
    var autoDec by remember { mutableStateOf(false) }

    var progress by remember { mutableStateOf(0f) }
    var stats by remember { mutableStateOf("") }
    var downloading by remember { mutableStateOf(false) }

    val pickDirLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        outDir = uri?.toString() ?: outDir
    }
    var pendingInfo by remember { mutableStateOf<FusClient.BinaryInfo?>(null) }
    val pickOutFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        val info = pendingInfo
        if (uri == null || info == null) {
            downloading = false
            return@rememberLauncherForActivityResult
        }
        // Start streaming download to the selected URI
        val resolver = ctx.contentResolver
        stats = "Downloading…"
        val size = info.size.coerceAtLeast(1L)
        scope.launch {
            runCatching {
                resolver.openOutputStream(uri, if (resume) "wa" else "w").use { os ->
                    requireNotNull(os) { "Failed to open output stream" }
                    val fus = FusClient() // regenerate nonce to be safe
                    fus.generateNonce()
                    var done = 0L
                    DownloadManager.download(
                        fus,
                        info.path + info.filename,
                        start = 0L,
                        endInclusive = null,
                        write = { chunk -> os.write(chunk) },
                        onProgress = { delta ->
                            done += delta
                            progress = (done.toDouble() / size.toDouble()).toFloat().coerceIn(0f, 1f)
                            stats = String.format("%s — %.2f%%", info.filename, progress * 100f)
                        }
                    )
                }
                stats = "Completed: ${info.filename}"
            }.onFailure {
                stats = "Error: ${it.message ?: "failed"}"
            }
            downloading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        DeviceInputs { m, r, i -> model = m; region = r; imei = i }
        Column(Modifier.padding(12.dp)) {
            OutlinedTextField(value = fw, onValueChange = { fw = it }, label = { Text("Firmware version") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = outDir, onValueChange = {}, enabled = false, label = { Text("Output directory (SAF URI)") }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Button(onClick = { pickDirLauncher.launch(null) }) { Text("Browse…") }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Threads")
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(value = threads.toString(), onValueChange = { v -> threads = v.toIntOrNull()?.coerceIn(1,10) ?: 1 },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(80.dp))
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
            val scope = rememberCoroutineScope()
            Button(onClick = {
                if (fw.isBlank() || model.isBlank() || region.isBlank()) return@Button
                downloading = true
                progress = 0f
                stats = "Preparing…"
                scope.launch {
                    runCatching {
                        val fus = FusClient()
                        fus.generateNonce()
                        val info = fus.binaryInform(fw, model, region, imei)
                        pendingInfo = info
                        val sizeMb = (info.size.toDouble() / (1024.0 * 1024.0))
                        stats = "${'$'}{info.filename} — ${'$'}{String.format("%.2f", sizeMb)} MiB (server)"
                        // Ask user for destination file and start streaming
                        pickOutFile.launch(info.filename)
                    }.onFailure {
                        stats = "Error: ${'$'}{it.message ?: "failed"}"
                        downloading = false
                    }
                }
            }, enabled = !downloading && fw.isNotBlank() && model.isNotBlank() && region.isNotBlank()) {
                Text("Start download")
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
            Text(stats)
        }

        // Simple progress simulation so UI is usable now
        LaunchedEffect(downloading) {
            if (downloading) {
                val total = 100
                for (i in 1..total) {
                    progress = i / total.toFloat()
                    stats = "$i% — simulated"
                    kotlinx.coroutines.delay(30)
                }
                downloading = false
            }
        }
    }
}

@Composable
private fun TabDecrypt() {
    val ctx = LocalContext.current
    // Collect device inputs required for key generation (like Python GUI)
    var model by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var imei by remember { mutableStateOf("") }

    var fw by remember { mutableStateOf("") }
    var encVer by remember { mutableStateOf("4") }
    var inFile by remember { mutableStateOf("") }
    var outFile by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }
    var busy by remember { mutableStateOf(false) }

    val pickIn = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        inFile = uri?.toString() ?: inFile
    }
    val pickOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        outFile = uri?.toString() ?: outFile
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        // Device inputs
        DeviceInputs { m, r, i -> model = m; region = r; imei = i }
        OutlinedTextField(value = fw, onValueChange = { fw = it }, label = { Text("Firmware version") }, modifier = Modifier.fillMaxWidth())
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
            Button(onClick = { pickIn.launch(arrayOf("*/*")) }) { Text("Browse…") }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = outFile, onValueChange = {}, enabled = false, label = { Text("Output file (URI)") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick = { pickOut.launch("firmware.zip") }) { Text("Browse…") }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            busy = true
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
                busy = false
                return@Button
            }
            val scope = rememberCoroutineScope()
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
                        }
                    }
                }.onFailure {
                    // TODO: expose error to user via Snackbar/Toast if desired
                }
                busy = false
            }
        }, enabled = !busy && fw.isNotBlank() && inFile.isNotBlank() && outFile.isNotBlank() && model.isNotBlank() && region.isNotBlank()) { Text("Start decryption") }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun TabHistory() {
    // Minimal in-memory list now; TODO persist to file like Python GUI
    var items by remember { mutableStateOf(listOf<String>()) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { if (items.isNotEmpty()) items = items.drop(1) }) { Text("Delete Selected (top)") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { items = emptyList() }) { Text("Clear All") }
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
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Default threads")
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(value = defThreads.toString(), onValueChange = { v -> defThreads = v.toIntOrNull()?.coerceIn(1,10) ?: 1 },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(80.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = autoDec, onCheckedChange = { autoDec = it })
            Text("Auto-decrypt by default")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { /* TODO persist via DataStore */ }) { Text("Save Settings") }
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
