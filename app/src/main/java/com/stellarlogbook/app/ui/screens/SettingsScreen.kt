package com.stellarlogbook.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.stellarlogbook.app.BuildConfig
import com.stellarlogbook.app.R
import com.stellarlogbook.app.core.AppContainer
import com.stellarlogbook.app.data.prefs.UserPrefs
import com.stellarlogbook.app.data.sync.EdsmDiagnostics
import com.stellarlogbook.app.data.sync.JournalImporter
import com.stellarlogbook.app.data.sync.SyncScheduler
import com.stellarlogbook.app.data.update.UpdateManager
import com.stellarlogbook.app.ui.import.JournalImportButton
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val commander by container.repository.commanderName().collectAsState(initial = null)
    val intervalPref by container.userPrefs.syncIntervalHours.collectAsState(initial = UserPrefs.DEFAULT_INTERVAL)
    val wifiPref by container.userPrefs.wifiOnly.collectAsState(initial = true)

    var commanderInput by remember { mutableStateOf("") }
    var apiKeyInput by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var intervalSel by remember { mutableStateOf(intervalPref) }
    var wifiSel by remember { mutableStateOf(wifiPref) }
    var summary by remember { mutableStateOf<JournalImporter.Summary?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }

    var diag by remember { mutableStateOf<EdsmDiagnostics.Report?>(null) }
    var diagRunning by remember { mutableStateOf(false) }

    var updateInfo by remember { mutableStateOf<UpdateManager.UpdateInfo?>(null) }
    var updateChecking by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableStateOf<UpdateManager.Progress?>(null) }
    var updateReady by remember { mutableStateOf<File?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(intervalPref) { intervalSel = intervalPref }
    LaunchedEffect(wifiPref) { wifiSel = wifiPref }
    if (!initialized && commander != null) {
        commanderInput = commander.orEmpty()
        initialized = true
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    stringResource(R.string.settings_commander),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            item {
                OutlinedTextField(
                    value = commanderInput,
                    onValueChange = { commanderInput = it },
                    label = { Text(stringResource(R.string.onboarding_commander_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text(stringResource(R.string.onboarding_apikey_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Button(
                    onClick = {
                        scope.launch {
                            container.repository.setCredentials(commanderInput, apiKeyInput)
                            SyncScheduler.ensure(context, intervalSel, wifiSel)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_save))
                }
            }
            item {
                TextButton(onClick = { showHelp = !showHelp }) {
                    Text(stringResource(R.string.settings_help_title))
                }
            }
            if (showHelp) {
                item {
                    Text(
                        stringResource(R.string.settings_help_steps),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // EDSM diagnostics
            item { HorizontalDivider() }
            item {
                Text(
                    stringResource(R.string.settings_edsm_diag),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            item {
                Button(
                    onClick = {
                        scope.launch {
                            diagRunning = true
                            diag = container.diagnostics.run()
                            diagRunning = false
                        }
                    },
                    enabled = !diagRunning,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_edsm_test))
                }
            }
            diag?.let { r ->
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!r.configured) {
                            Text(stringResource(R.string.settings_edsm_not_configured), color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Position: msgnum ${r.positionMsgnum} · ${r.positionSystem ?: r.positionMsg ?: "—"}")
                            Text("Flight logs (last 30 d): msgnum ${r.logsMsgnum} · ${r.logsCount ?: "?"} entries · ${r.logsMsg.orEmpty()}")
                            r.newestEntry?.let { Text("Newest entry: $it") }
                            if (r.logsMsgnum == 203) {
                                Text(stringResource(R.string.settings_edsm_badkey), color = MaterialTheme.colorScheme.error)
                            } else if (r.logsMsgnum == 100 && (r.logsCount ?: 0) == 0) {
                                Text(
                                    stringResource(R.string.settings_edsm_empty_hint),
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }
            }

            // Sync options
            item { HorizontalDivider() }
            item {
                Text(
                    stringResource(R.string.settings_sync_options),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            item { Text(stringResource(R.string.settings_interval)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to stringResource(R.string.settings_off), 1 to "1 h", 6 to "6 h", 24 to "24 h")
                        .forEach { (value, label) ->
                            FilterChip(
                                selected = intervalSel == value,
                                onClick = { intervalSel = value },
                                label = { Text(label) }
                            )
                        }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.settings_wifi_only), modifier = Modifier.weight(1f))
                    Switch(checked = wifiSel, onCheckedChange = { wifiSel = it })
                }
            }
            item {
                Button(
                    onClick = {
                        scope.launch {
                            container.userPrefs.setSyncPreferences(intervalSel, wifiSel)
                            SyncScheduler.ensure(context, intervalSel, wifiSel)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_save))
                }
            }

            // Journal import
            item { HorizontalDivider() }
            item {
                JournalImportButton(
                    container = container,
                    onSummary = { summary = it; importError = null },
                    onError = { importError = it; summary = null },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            summary?.let {
                item {
                    Text(
                        stringResource(
                            R.string.import_summary,
                            it.visits,
                            it.firstDiscoveries,
                            it.firstMappings,
                            it.codex,
                            it.lines
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            importError?.let {
                item {
                    Text(
                        stringResource(R.string.import_error, it),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // App updates
            item { HorizontalDivider() }
            item {
                Text(
                    stringResource(R.string.settings_updates),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            item {
                Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME))
            }
            item {
                Button(
                    onClick = {
                        scope.launch {
                            updateChecking = true
                            updateError = null
                            updateInfo = null
                            val info = container.updateManager.check()
                            updateInfo = info
                            updateChecking = false
                            if (info == null) updateError = context.getString(R.string.settings_update_none)
                        }
                    },
                    enabled = !updateChecking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_update_check))
                }
            }
            updateInfo?.let { info ->
                item {
                    Text(stringResource(R.string.settings_update_available, info.versionName))
                }
                item {
                    Button(
                        onClick = {
                            scope.launch {
                                updateError = null
                                updateProgress = UpdateManager.Progress(0, 1)
                                container.updateManager.download(info) { p -> updateProgress = p }
                                    .fold(
                                        onSuccess = { file -> updateReady = file },
                                        onFailure = { e -> updateError = e.message }
                                    )
                                updateProgress = null
                            }
                        },
                        enabled = updateReady == null && updateProgress == null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_update_download))
                    }
                }
                updateProgress?.let { p ->
                    val frac = if (p.totalBytes > 0) p.doneBytes.toFloat() / p.totalBytes else 0f
                    item { LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth()) }
                }
            }
            updateReady?.let { file ->
                item {
                    Button(
                        onClick = { installApk(context, file) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_update_install))
                    }
                }
            }
            updateError?.let {
                item {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }

            // About
            item { HorizontalDivider() }
            item {
                Text(
                    stringResource(R.string.settings_about),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            item {
                Text(stringResource(R.string.settings_about_text), style = MaterialTheme.typography.bodySmall)
            }
            item {
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL))
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_privacy))
                }
            }
        }
    }
}

private const val PRIVACY_URL =
    "https://github.com/ByzantineEmpress/StellarLogbook/blob/main/docs/PRIVACY.md"

private fun installApk(context: Context, file: File) {
    if (!context.packageManager.canRequestPackageInstalls()) {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
