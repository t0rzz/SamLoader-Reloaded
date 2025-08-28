import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import app.samloader.common.Api

@Composable
fun AppIOS() {
    MaterialTheme {
        Text(Api.appName() + " (iOS) — UI to be implemented.")
    }
}
