package com.stellarlogbook.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stellarlogbook.app.R
import com.stellarlogbook.app.core.AppContainer
import com.stellarlogbook.app.data.local.entity.DiscoveryEntity
import com.stellarlogbook.app.data.sync.JournalImporter
import com.stellarlogbook.app.ui.import.JournalImportButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** "Which systems/bodies did I discover?" — fed by journal import. */
@Composable
fun DiscoveriesScreen(container: AppContainer) {
    val discoveries by container.repository.discoveries().collectAsState(initial = emptyList())
    var summary by remember { mutableStateOf<JournalImporter.Summary?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        if (discoveries.isEmpty()) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.empty_discoveries))
                JournalImportButton(
                    container = container,
                    onSummary = { summary = it; error = null },
                    onError = { error = it; summary = null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                item {
                    JournalImportButton(
                        container = container,
                        onSummary = { summary = it; error = null },
                        onError = { error = it; summary = null },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
                items(discoveries, key = { it.id }) { d ->
                    ListItem(
                        headlineContent = { Text(d.bodyName) },
                        supportingContent = { Text(supportingText(d)) }
                    )
                }
            }
        }
        summary?.let {
            Text(
                stringResource(
                    R.string.import_summary,
                    it.visits,
                    it.firstDiscoveries,
                    it.firstMappings,
                    it.codex,
                    it.lines
                ),
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
        error?.let {
            Text(
                stringResource(R.string.import_error, it),
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun supportingText(d: DiscoveryEntity): String {
    val date = d.at.let { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(it)) }
    return listOfNotNull(d.kind, d.systemName, date).joinToString(" · ")
}
