package com.stellarlogbook.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stellarlogbook.app.R
import com.stellarlogbook.app.core.AppContainer
import com.stellarlogbook.app.core.GalaxyMath
import com.stellarlogbook.app.data.local.entity.SystemEntity
import com.stellarlogbook.app.ui.home.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(container: AppContainer, onOpenSettings: () -> Unit) {
    val vm: HomeViewModel = viewModel { HomeViewModel(container.repository, container.syncEngine) }
    val latest by vm.latestVisit.collectAsState(initial = null)
    val visited by vm.visitedCount.collectAsState(initial = 0)
    val discoveries by vm.discoveryCount.collectAsState(initial = 0)
    val codex by vm.codexCount.collectAsState(initial = 0)
    val lastSync by vm.lastSyncAt.collectAsState(initial = 0L)
    val syncStatus by vm.syncStatus.collectAsState()

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings_title))
                    }
                }
            )
        }
        item { CurrentSystemCard(latest) }
        item { TotalsCard(visited, discoveries, codex) }
        item {
            SyncCard(
                lastSync = lastSync,
                status = syncStatus,
                onSync = vm::syncNow
            )
        }
    }
}

@Composable
private fun CurrentSystemCard(latest: SystemEntity?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.home_current_system),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                latest?.name ?: "—",
                style = MaterialTheme.typography.headlineMedium
            )
            latest?.let { sys ->
                if (sys.x != null && sys.y != null && sys.z != null) {
                    Text(
                        "%.0f ly from Sol".format(GalaxyMath.distanceFromSol(sys.x, sys.y, sys.z)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                sys.lastVisitedAt?.let { ts ->
                    Text(
                        "Last visit: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TotalsCard(visited: Int, discoveries: Int, codex: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.home_totals),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(stringResource(R.string.home_visited_count), style = MaterialTheme.typography.bodyLarge)
            Text("$visited", style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.home_discovery_count), style = MaterialTheme.typography.bodyLarge)
            Text("$discoveries", style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.home_codex_count), style = MaterialTheme.typography.bodyLarge)
            Text("$codex", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun SyncCard(
    lastSync: Long,
    status: HomeViewModel.SyncStatus,
    onSync: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.home_sync),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (status) {
                is HomeViewModel.SyncStatus.Running -> CircularProgressIndicator()
                is HomeViewModel.SyncStatus.Done ->
                    if (status.entries == 0) {
                        Text(
                            stringResource(R.string.home_sync_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    } else {
                        Text("Synced ${status.entries} entries", style = MaterialTheme.typography.bodyMedium)
                    }
                is HomeViewModel.SyncStatus.Error ->
                    Text(status.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                HomeViewModel.SyncStatus.Idle -> Text(
                    if (lastSync == 0L) stringResource(R.string.home_sync_never)
                    else "Last sync: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastSync))}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(onClick = onSync, enabled = status !is HomeViewModel.SyncStatus.Running, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.home_sync_now))
            }
        }
    }
}
