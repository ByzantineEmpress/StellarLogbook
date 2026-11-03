package com.stellarlogbook.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stellarlogbook.app.core.AppContainer
import com.stellarlogbook.app.core.GalaxyMath
import com.stellarlogbook.app.data.remote.SphereSystemDto
import com.stellarlogbook.app.ui.explore.ExploreViewModel
import java.util.Locale

/**
 * The killer tab: "what haven't I seen?" — Unvisited near me (EDSM sphere
 * search minus my visits) and Uncharted near me (local pack gap search).
 */
@Composable
fun ExploreScreen(container: AppContainer) {
    val vm: ExploreViewModel = viewModel {
        ExploreViewModel(container.repository, container.api, container.packManager, container.packDownloader)
    }
    val latest by vm.latestVisit.collectAsState(initial = null)
    val busy by vm.busy.collectAsState()
    val unvisited by vm.unvisited.collectAsState()
    val gaps by vm.gaps.collectAsState()
    val packInstalled by vm.packInstalled.collectAsState()
    val download by vm.download.collectAsState()
    val clipboard = LocalClipboardManager.current

    var radius by remember { mutableFloatStateOf(30f) }

    LaunchedEffect(Unit) { vm.refreshPackStatus() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Position
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Position",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(latest?.name ?: "—", style = MaterialTheme.typography.headlineSmall)
                    latest?.let { sys ->
                        if (sys.x != null && sys.y != null && sys.z != null) {
                            Text(
                                "%.0f ly from Sol".format(
                                    Locale.getDefault(),
                                    GalaxyMath.distanceFromSol(sys.x, sys.y, sys.z)
                                )
                            )
                        }
                    }
                }
            }
        }

        // Radius + actions
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Search radius: ${radius.toInt()} ly")
                    Slider(value = radius, onValueChange = { radius = it }, valueRange = 10f..100f)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { vm.findUnvisited(radius) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Unvisited near me")
                        }
                        Button(
                            onClick = { vm.findUncharted(15f, 2000f) },
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Uncharted near me")
                        }
                    }
                    if (busy) {
                        CircularProgressIndicator(Modifier.padding(top = 8.dp))
                    }
                }
            }
        }

        // Pack status + download
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Uncharted Pack",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    when (val d = download) {
                        is ExploreViewModel.DownloadState.Running -> {
                            LinearProgressIndicator(progress = { d.progress }, modifier = Modifier.fillMaxWidth())
                            Text("Downloading ${d.currentFile}…", style = MaterialTheme.typography.bodySmall)
                        }
                        ExploreViewModel.DownloadState.Done -> Text("Pack installed ✓")
                        is ExploreViewModel.DownloadState.Error -> Text(d.message, color = MaterialTheme.colorScheme.error)
                        ExploreViewModel.DownloadState.Idle -> {
                            Text(
                                if (packInstalled) "Installed ✓ — uncharted search works offline."
                                else "Not installed. Download the slice for your region (~50-90 MB, Wi-Fi recommended) to search uncharted space."
                            )
                            if (!packInstalled) {
                                Button(onClick = { vm.downloadPack() }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Download pack")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Unvisited results
        when (val u = unvisited) {
            is ExploreViewModel.ResultsState.Ready -> item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Unvisited within ${radius.toInt()} ly: ${u.items.size}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        u.items.take(25).forEach { sys ->
                            ListItem(
                                headlineContent = { Text(sys.name ?: "?") },
                                supportingContent = { Text(sphereSupport(sys)) },
                                modifier = Modifier.clickable {
                                    clipboard.setText(AnnotatedString(sys.name ?: ""))
                                }
                            )
                        }
                    }
                }
            }
            is ExploreViewModel.ResultsState.Error -> item {
                Text(u.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp))
            }
            ExploreViewModel.ResultsState.Loading -> Unit
            ExploreViewModel.ResultsState.Idle -> Unit
        }

        // Uncharted results
        when (val g = gaps) {
            is ExploreViewModel.ResultsState.Ready -> item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            if (g.items.isEmpty()) "No uncharted gaps found nearby — try a larger area later."
                            else "Nearest spots with no known reports (tap to copy):",
                            style = MaterialTheme.typography.titleMedium
                        )
                        g.items.forEach { gap ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        "%.0f, %.0f, %.0f".format(
                                            Locale.getDefault(), gap.x, gap.y, gap.z
                                        )
                                    )
                                },
                                supportingContent = {
                                    Text("%.0f ly away".format(Locale.getDefault(), gap.distanceLy))
                                },
                                modifier = Modifier.clickable {
                                    clipboard.setText(
                                        AnnotatedString("%.0f %.0f %.0f".format(Locale.getDefault(), gap.x, gap.y, gap.z))
                                    )
                                }
                            )
                        }
                    }
                }
            }
            is ExploreViewModel.ResultsState.Error -> item {
                Text(g.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp))
            }
            ExploreViewModel.ResultsState.Loading -> Unit
            ExploreViewModel.ResultsState.Idle -> Unit
        }
    }
}

private fun sphereSupport(sys: SphereSystemDto): String {
    val parts = mutableListOf<String>()
    sys.distance?.let { parts.add("%.0f ly".format(Locale.getDefault(), it)) }
    sys.primaryStar?.type?.let { parts.add(it) }
    return parts.joinToString(" · ")
}
