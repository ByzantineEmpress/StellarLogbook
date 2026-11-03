package com.stellarlogbook.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stellarlogbook.app.R
import com.stellarlogbook.app.core.AppContainer
import com.stellarlogbook.app.core.GalaxyMath
import com.stellarlogbook.app.data.local.entity.SystemEntity
import com.stellarlogbook.app.ui.detail.SystemDetailViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun SpanshBodiesCard(
    state: SystemDetailViewModel.SpanshState,
    localBodies: List<com.stellarlogbook.app.data.local.entity.BodyEntity>
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.detail_bodies),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (state) {
                SystemDetailViewModel.SpanshState.Idle -> Text(stringResource(R.string.detail_bodies_hint))
                SystemDetailViewModel.SpanshState.Loading -> CircularProgressIndicator()
                is SystemDetailViewModel.SpanshState.Error ->
                    Text(stringResource(R.string.detail_lookup_error, state.message), color = MaterialTheme.colorScheme.error)
                is SystemDetailViewModel.SpanshState.Ready -> {
                    if (state.bodies.isEmpty()) {
                        Text(stringResource(R.string.detail_bodies_none))
                    } else {
                        state.bodies.take(MAX_BODIES).forEach { body ->
                            val mine = localBodies.any {
                                it.bodyName == body.name && (it.myFirstDiscovery || it.myFirstMapping)
                            }
                            val values = buildList {
                                body.estimatedScanValue?.let { add(stringResource(R.string.detail_scan_value, "%,d".format(it))) }
                                body.estimatedMappingValue?.let { add(stringResource(R.string.detail_map_value, "%,d".format(it))) }
                            }.joinToString(" · ")
                            Column(Modifier.padding(vertical = 2.dp)) {
                                Text(
                                    "${body.name ?: "?"} — ${body.subtype ?: body.type ?: "?"}" +
                                        if (mine) "  ${stringResource(R.string.detail_mine)}" else "",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (values.isNotEmpty()) {
                                    Text(
                                        values,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (state.bodies.size > MAX_BODIES) {
                            Text(
                                stringResource(R.string.detail_bodies_more, state.bodies.size - MAX_BODIES),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val MAX_BODIES = 50

private fun fmt(ts: Long?): String? = ts?.let {
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemDetailScreen(container: AppContainer, name: String, onBack: () -> Unit) {
    val vm: SystemDetailViewModel =
        viewModel(key = "system-$name") {
            SystemDetailViewModel(name, container.repository, container.api, container.spanshApi)
        }
    val system by vm.system.collectAsState(initial = null)
    val community by vm.community.collectAsState()
    val spansh by vm.spansh.collectAsState()
    val localBodies by container.repository.observeBodies(name).collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val sys = system
            if (sys == null) {
                item { Text(stringResource(R.string.detail_not_found)) }
            } else {
                item { LocalInfoCard(sys) }
                item { BookmarkNotesCard(sys, vm) }
            }
            item { CommunityCard(community) }
            item { SpanshBodiesCard(spansh, localBodies) }
        }
    }
}

@Composable
private fun LocalInfoCard(sys: SystemEntity) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.detail_identity),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (sys.id64 != null) {
                Text(stringResource(R.string.detail_id64, sys.id64.toString()))
            }
            if (sys.x != null && sys.y != null && sys.z != null) {
                Text(stringResource(R.string.detail_coords, "%.2f, %.2f, %.2f".format(sys.x, sys.y, sys.z)))
                Text(
                    stringResource(
                        R.string.detail_dist_sol,
                        "%.0f".format(GalaxyMath.distanceFromSol(sys.x, sys.y, sys.z))
                    )
                )
            }
            Text(stringResource(R.string.detail_visits, sys.visitCount))
            Text(stringResource(R.string.detail_first_visit, fmt(sys.firstVisitedAt) ?: "—"))
            Text(stringResource(R.string.detail_last_visit, fmt(sys.lastVisitedAt) ?: "—"))
            if (sys.knownToCommunity) {
                Text(
                    stringResource(R.string.detail_known),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun BookmarkNotesCard(sys: SystemEntity, vm: SystemDetailViewModel) {
    val scope = rememberCoroutineScope()
    var notes by remember(sys.name) { mutableStateOf(sys.notes.orEmpty()) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.detail_bookmark),
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = sys.bookmarked,
                    onCheckedChange = { vm.setBookmarked(it) }
                )
            }
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text(stringResource(R.string.detail_notes)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Button(
                onClick = { scope.launch { vm.saveNotes(notes) } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.detail_save_notes))
            }
        }
    }
}

@Composable
private fun CommunityCard(state: SystemDetailViewModel.CommunityState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.detail_community),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (state) {
                is SystemDetailViewModel.CommunityState.Idle -> Unit
                SystemDetailViewModel.CommunityState.Loading -> CircularProgressIndicator()
                is SystemDetailViewModel.CommunityState.Error ->
                    Text(stringResource(R.string.detail_lookup_error, state.message), color = MaterialTheme.colorScheme.error)
                is SystemDetailViewModel.CommunityState.Ready -> {
                    state.info.primaryStar?.let { star ->
                        Text(stringResource(R.string.detail_primary_star, star.name ?: "?", star.type ?: "?"))
                    }
                    state.info.information?.let { info ->
                        info.allegiance?.let {
                            Text(stringResource(R.string.detail_allegiance, it))
                        }
                        info.population?.let {
                            Text(stringResource(R.string.detail_population, "%,d".format(it)))
                        }
                        if (info.discovered != null || info.discoveredBy != null) {
                            Text(
                                stringResource(
                                    R.string.detail_discovered,
                                    info.discovered ?: "?",
                                    info.discoveredBy ?: "?"
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
