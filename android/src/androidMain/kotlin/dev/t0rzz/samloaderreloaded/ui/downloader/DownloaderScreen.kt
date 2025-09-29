package dev.t0rzz.samloaderreloaded.ui.downloader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.outlined.Block
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.TextView
import android.text.method.LinkMovementMethod
import androidx.core.text.HtmlCompat
import app.samloader.common.downloader.DownloaderViewModel

@Composable
fun DownloaderScreen(
    vm: DownloaderViewModel = remember { DownloaderViewModel() },
    onStartDownload: (model: String, region: String, imeis: String, firmware: String) -> Unit,
) {
    val model by vm.model.collectAsState()
    val region by vm.region.collectAsState()
    val firmware by vm.firmware.collectAsState()
    val imeis by vm.imeis.collectAsState()
    val osVersion by vm.osVersion.collectAsState()
    val changelog by vm.changelog.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()

    var manual by remember { mutableStateOf(false) }
    var showImeiEditor by remember { mutableStateOf(false) }
    var showImeiInfo by remember { mutableStateOf(false) }
    var showCscPicker by remember { mutableStateOf(false) }
    var clExpanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        // Icon row
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = {
                if (!loading) onStartDownload(model.trim(), region.trim(), imeis.trim(), firmware.trim())
            }, enabled = !loading) {
                Icon(Icons.Default.Download, contentDescription = "Download")
            }
            Box {
                IconButton(onClick = { if (!loading) vm.refresh() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
                if (loading) {
                    CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.Center))
                }
            }
            IconButton(onClick = { /* disabled action placeholder */ }, enabled = false) {
                Icon(Icons.Outlined.Block, contentDescription = "Disabled")
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = manual, onCheckedChange = { manual = it })
                Text("Manual")
            }
        }
        Spacer(Modifier.height(8.dp))

        // Form fields
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                OutlinedTextField(
                    value = model,
                    onValueChange = { vm.setModel(it) },
                    label = { Text("Model (e.g. SM-N986U1)") },
                    placeholder = { Text("SM-S918U1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = vm.validateModel() != null && model.isNotBlank()
                )
                vm.validateModel()?.let { if (model.isNotBlank()) Text(it, color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption) }
            }
            item {
                OutlinedTextField(
                    value = region,
                    onValueChange = { vm.setRegion(it.uppercase().take(3)) },
                    label = { Text("Region (e.g. XAA)") },
                    placeholder = { Text("TMB") },
                    trailingIcon = {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Filled.List,
                            contentDescription = "Pick region",
                            modifier = Modifier.clickable { showCscPicker = true }.padding(8.dp)
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = firmware,
                    onValueChange = { /* usually auto-filled from refresh */ },
                    label = { Text("Firmware") },
                    placeholder = { Text("S918U1UES2BWL9/S918U1OYM2BWL9/S918U1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    readOnly = true,
                    maxLines = 1
                )
            }
            item {
                OutlinedTextField(
                    value = imeis,
                    onValueChange = { vm.setImeis(it) },
                    label = { Text("IMEI/Serial") },
                    placeholder = { Text("357644121234565;35313860123456") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = vm.validateImeis() != null,
                    trailingIcon = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit IMEI", modifier = Modifier.clickable { showImeiEditor = true }.padding(4.dp))
                            Icon(Icons.Default.Info, contentDescription = "IMEI Info", modifier = Modifier.clickable { showImeiInfo = true }.padding(4.dp))
                        }
                    }
                )
                vm.validateImeis()?.let { Text(it, color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption) }
                Spacer(Modifier.height(4.dp))
                Text(text = "OS Version: $osVersion")
            }
            item {
                Surface(shape = MaterialTheme.shapes.small, elevation = 0.dp, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().clickable { clExpanded = !clExpanded }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Changelog", style = MaterialTheme.typography.subtitle1)
                            Spacer(Modifier.weight(1f))
                            Text(if (clExpanded) "▲" else "▼")
                        }
                        if (clExpanded) {
                            if (changelog == null) {
                                Text("No changelog available.")
                            } else {
                                val meta = listOfNotNull(
                                    changelog?.relDate?.let { "Release: $it" },
                                    changelog?.secPatch?.let { "Security: $it" }
                                ).joinToString(" • ")
                                if (meta.isNotBlank()) Text(meta)
                                Spacer(Modifier.height(6.dp))
                                HtmlText(html = changelog?.notes ?: "", modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
            item {
                if (!error.isNullOrEmpty()) {
                    Text(error ?: "", color = MaterialTheme.colors.error)
                }
            }
        }
    }

    if (showImeiEditor) ImeiEditorDialog(
        initial = imeis,
        onDismiss = { showImeiEditor = false },
        onSave = { vm.setImeis(it); showImeiEditor = false }
    )

    if (showImeiInfo) AlertDialog(
        onDismissRequest = { showImeiInfo = false },
        confirmButton = { TextButton(onClick = { showImeiInfo = false }) { Text("OK") } },
        title = { Text("IMEI/Serial Info") },
        text = { Text("Multiple IMEIs/serials separated by semicolons. IMEI should be numeric, 8-17 digits. Some regions require IMEI prefix (>= 8 digits).") }
    )

    if (showCscPicker) CscPickerDialog(
        onDismiss = { showCscPicker = false },
        onSelected = { code -> vm.setRegion(code); showCscPicker = false }
    )
}

@Composable
private fun ImeiEditorDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit IMEIs") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("3576...;3531...;") }
            )
        },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CscPickerDialog(onDismiss: () -> Unit, onSelected: (String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var query by remember { mutableStateOf("") }
    val all = remember { CscLoader.load(ctx) }
    val filtered = remember(query, all) { all.filter { it.code.contains(query, true) || (it.countries?.contains(query, true) == true) || (it.carriers?.contains(query, true) == true) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Region/CSC") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                val items = filtered.take(100) // limit for dialog
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(items.size) { idx ->
                        val item = items[idx]
                        Row(Modifier.fillMaxWidth().clickable { onSelected(item.code) }.padding(vertical = 8.dp)) {
                            Text(item.code)
                            Spacer(Modifier.width(8.dp))
                            Text(item.countries ?: "")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}


@Composable
private fun HtmlText(html: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { tv ->
            tv.text = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT)
        }
    )
}
