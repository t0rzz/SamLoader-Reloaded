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
        DeviceInputs { m, r, _ -> model = m; region = r }
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                busy = true
                // TODO wire to KMP VersionFetch.getLatest
                // Placeholder just echoes input
                latest = if (model.isBlank() || region.isBlank()) "Missing model/region" else "<latest version here>"
                busy = false
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
            Button(onClick = {
                // TODO wire to KMP downloader; placeholder simulates progress
                downloading = true
                progress = 0f
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
            // TODO: wire to decrypt logic; simulate
            busy = true
            progress = 0f
        }, enabled = !busy && fw.isNotBlank() && inFile.isNotBlank() && outFile.isNotBlank()) { Text("Start decryption") }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())

        LaunchedEffect(busy) {
            if (busy) {
                val total = 100
                for (i in 1..total) {
                    progress = i / total.toFloat()
                    kotlinx.coroutines.delay(20)
                }
                busy = false
            }
        }
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
