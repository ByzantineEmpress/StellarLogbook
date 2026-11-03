package com.stellarlogbook.app.ui.import

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stellarlogbook.app.R
import com.stellarlogbook.app.core.AppContainer
import com.stellarlogbook.app.data.sync.JournalImporter
import kotlinx.coroutines.launch

/**
 * One-tap "import journal files" button. Opens the system file picker
 * (multi-select), streams each picked Journal*.log through [JournalImporter]
 * and reports the combined summary (or error) back to the caller.
 */
@Composable
fun JournalImportButton(
    container: AppContainer,
    onSummary: (JournalImporter.Summary) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            try {
                var total = JournalImporter.Summary(0, 0, 0, 0, 0, 0, 0)
                uris.forEach { uri ->
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        total += container.journalImporter.importLines(
                            input.bufferedReader().lineSequence()
                        )
                    }
                }
                onSummary(total)
            } catch (e: Exception) {
                onError(e.message ?: "Import failed")
            } finally {
                busy = false
            }
        }
    }

    Button(
        onClick = { launcher.launch(arrayOf("*/*")) },
        enabled = !busy,
        modifier = modifier
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(stringResource(R.string.import_title))
        }
    }
}
