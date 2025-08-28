package app.samloader.desktop

import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import app.samloader.common.Api

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = Api.appName()) {
        MaterialTheme {
            App()
        }
    }
}

@Composable
fun App() {
    Text("Duofrost (KMP) — UI to be implemented.\n\nTabs: Check Update, Download, Decrypt, History, Settings")
}
