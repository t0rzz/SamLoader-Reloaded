package app.samloader.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.samloader.common.Api
import app.samloader.common.download.DownloadManager
import app.samloader.common.fus.FusClient
import app.samloader.common.version.VersionFetch
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.swing.JFileChooser

@Composable
fun DuofrostDesktopApp() {
    val tabs = listOf("Check Update", "Download", "Decrypt", "History", "Settings")
    var selectedTab by remember { mutableStateOf(0) }

    Scaffold(topBar = { TopAppBar(title = { Text(Api.appName()) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { i, title ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(title) })
                }
            }
            when (selectedTab) {
                0 -> TabCheckUpdateDesktop()
                1 -> TabDownloadDesktop()
                2 -> TabDecryptDesktop()
                3 -> TabHistoryDesktop()
                4 -> TabSettingsDesktop()
            }
        }
    }
}

@Composable
private fun DeviceInputs(onChanged: (model: String, region: String, imei: String) -> Unit = { _,_,_ -> }) {
    var model by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var imei by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        OutlinedTextField(value = model, onValueChange = { model = it; onChanged(model, region, imei) }, label = { Text("Model") }, placeholder = { Text("e.g., SM-S918B") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = region, onValueChange = { region = it.uppercase(); onChanged(model, region, imei) }, label = { Text("Region (CSC)") }, placeholder = { Text("e.g., BTU/ITV/INS") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = imei, onValueChange = { imei = it; onChanged(model, region, imei) }, label = { Text("IMEI prefix or serial") }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun TabCheckUpdateDesktop() {
    var latest by remember { mutableStateOf("-") }
    var busy by remember { mutableStateOf(false) }
    var model by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        DeviceInputs { m, r, _ -> model = m; region = r }
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                if (model.isBlank() || region.isBlank()) { latest = "Missing model/region"; return@Button }
                busy = true
                scope.launch {
                    runCatching { VersionFetch.getLatest(model, region) }
                        .onSuccess { latest = it }
                        .onFailure { latest = "Error: ${it.message ?: "failed"}" }
                    busy = false
                }
            }, enabled = !busy) { Text(if (busy) "Checking…" else "Check latest version") }
            Spacer(Modifier.width(16.dp))
            Text("Latest: $latest")
        }
    }
}

@Composable
private fun TabDownloadDesktop() {
    val scope = rememberCoroutineScope()
    var model by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var imei by remember { mutableStateOf("") }
    var fw by remember { mutableStateOf("") }
    var outPath by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }
    var stats by remember { mutableStateOf("") }
    var downloading by remember { mutableStateOf(false) }

    fun browseSaveFile(suggested: String?): String {
        val chooser = JFileChooser()
        chooser.dialogTitle = "Save firmware as…"
        chooser.selectedFile = File(suggested ?: "")
        val ret = chooser.showSaveDialog(null)
        return if (ret == JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath else outPath
    }

    Column(Modifier.fillMaxSize()) {
        DeviceInputs { m, r, i -> model = m; region = r; imei = i }
        Column(Modifier.padding(12.dp)) {
            OutlinedTextField(value = fw, onValueChange = { fw = it }, label = { Text("Firmware version") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = outPath, onValueChange = { outPath = it }, label = { Text("Output file") }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Button(onClick = { outPath = browseSaveFile(outPath.ifBlank { "firmware.enc4" }) }) { Text("Browse…") }
            }
            Spacer(Modifier.height(8.dp))
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
                        val dest = if (outPath.isNotBlank()) outPath else info.filename
                        val size = info.size.coerceAtLeast(1L)
                        stats = "${info.filename} — ${String.format("%.2f", size / (1024.0 * 1024.0))} MiB (server)"
                        val fos = FileOutputStream(dest)
                        var done = 0L
                        DownloadManager.download(
                            fus,
                            info.path + info.filename,
                            start = 0L,
                            endInclusive = null,
                            write = { chunk -> fos.write(chunk) },
                            onProgress = { delta ->
                                done += delta
                                progress = (done.toDouble() / size.toDouble()).toFloat().coerceIn(0f, 1f)
                                stats = String.format("%s — %.2f%%", info.filename, progress * 100f)
                            }
                        )
                        fos.flush(); fos.close()
                        stats = "Completed: ${info.filename} -> $dest"
                    }.onFailure { stats = "Error: ${it.message ?: "failed"}" }
                    downloading = false
                }
            }, enabled = !downloading && fw.isNotBlank() && model.isNotBlank() && region.isNotBlank()) { Text("Start download") }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
            Text(stats)
        }
    }
}

@Composable
private fun TabDecryptDesktop() {
    val scope = rememberCoroutineScope()
    var model by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var imei by remember { mutableStateOf("") }
    var fw by remember { mutableStateOf("") }
    var encVer by remember { mutableStateOf("4") }
    var inPath by remember { mutableStateOf("") }
    var outPath by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf(0f) }
    var busy by remember { mutableStateOf(false) }

    fun browseOpenFile(): String {
        val chooser = JFileChooser()
        chooser.dialogTitle = "Select encrypted file"
        chooser.fileSelectionMode = JFileChooser.FILES_ONLY
        val ret = chooser.showOpenDialog(null)
        return if (ret == JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath else inPath
    }
    fun browseSaveZip(): String {
        val chooser = JFileChooser()
        chooser.dialogTitle = "Select output file"
        chooser.selectedFile = File("firmware.zip")
        val ret = chooser.showSaveDialog(null)
        return if (ret == JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath else outPath
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
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
            OutlinedTextField(value = inPath, onValueChange = { inPath = it }, label = { Text("Encrypted file") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick = { inPath = browseOpenFile() }) { Text("Browse…") }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = outPath, onValueChange = { outPath = it }, label = { Text("Output file") }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Button(onClick = { outPath = browseSaveZip() }) { Text("Browse…") }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            if (fw.isBlank() || inPath.isBlank() || outPath.isBlank()) return@Button
            val enc = encVer.toIntOrNull() ?: 4
            busy = true
            progress = 0f
            scope.launch {
                runCatching {
                    val inFile = File(inPath)
                    val totalLen = inFile.length()
                    require(totalLen > 0 && totalLen % 16L == 0L) { "Invalid input size" }
                    FileInputStream(inFile).use { ins ->
                        FileOutputStream(outPath).use { outs ->
                            val key: ByteArray = if (enc == 2) {
                                app.samloader.common.auth.Auth.v2Key(fw, model, region)
                            } else {
                                val fus = FusClient(); fus.generateNonce(); fus.getV4Key(fw, model, region, imei)
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
                                onProgress = { _ ->
                                    progress = (1f - (remaining.toDouble() / totalLen.toDouble()).toFloat()).coerceIn(0f, 1f)
                                }
                            )
                        }
                    }
                }.onFailure { /* show in UI below */ }
                busy = false
            }
        }, enabled = !busy && fw.isNotBlank() && inPath.isNotBlank() && outPath.isNotBlank() && model.isNotBlank() && region.isNotBlank()) {
            Text("Start decryption")
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
    }
}

@OptIn(androidx.compose.material.ExperimentalMaterialApi::class)
@Composable
private fun TabHistoryDesktop() {
    var items by remember { mutableStateOf(listOf<String>()) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { if (items.isNotEmpty()) items = items.drop(1) }) { Text("Delete Selected (top)") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { items = emptyList() }) { Text("Clear All") }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(items.size) { idx ->
                ListItem(text = { Text(items[idx]) }); Divider()
            }
        }
    }
}

@Composable
private fun TabSettingsDesktop() {
    var defThreads by remember { mutableStateOf(1) }
    var autoDec by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Default threads"); Spacer(Modifier.width(8.dp))
            OutlinedTextField(value = defThreads.toString(), onValueChange = { v -> defThreads = v.toIntOrNull()?.coerceIn(1,10) ?: 1 }, modifier = Modifier.width(80.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = autoDec, onCheckedChange = { autoDec = it })
            Text("Auto-decrypt by default")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { /* could persist later */ }) { Text("Save Settings") }
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
