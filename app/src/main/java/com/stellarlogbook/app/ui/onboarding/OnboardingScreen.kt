package com.stellarlogbook.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stellarlogbook.app.R
import kotlinx.coroutines.launch

/**
 * Single-screen onboarding, deliberately minimal: commander + API key, then go.
 * A separate guided walkthrough for "how do I get a key" lands in Phase 1
 * (see docs/setup-edsm-walkthrough.md).
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    onSaveCredentials: suspend (commander: String, apiKey: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var commander by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.onboarding_subtitle), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = commander,
            onValueChange = { commander = it },
            label = { Text(stringResource(R.string.onboarding_commander_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text(stringResource(R.string.onboarding_apikey_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.onboarding_how),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = { showHelp = !showHelp }) {
            Text(stringResource(R.string.onboarding_help))
        }
        if (showHelp) {
            Text(
                stringResource(R.string.settings_help_steps),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                scope.launch {
                    saving = true
                    onSaveCredentials(commander, apiKey)
                    onDone()
                }
            },
            enabled = commander.isNotBlank() && apiKey.isNotBlank() && !saving,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(stringResource(R.string.onboarding_start))
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_skip))
        }
    }
}
