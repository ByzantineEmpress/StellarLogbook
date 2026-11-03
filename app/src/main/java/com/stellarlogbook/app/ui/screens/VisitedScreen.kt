package com.stellarlogbook.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
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
import com.stellarlogbook.app.core.GalaxyMath
import com.stellarlogbook.app.data.local.entity.SystemEntity
import java.util.Locale

/** "Which systems have I visited?" — search-first list, newest first, tap for detail. */
@Composable
fun VisitedScreen(container: AppContainer, onOpenSystem: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val systemsFlow = remember(query) { container.repository.visitedSystems(query) }
    val systems by systemsFlow.collectAsState(initial = emptyList())

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true
        )
        if (systems.isEmpty()) {
            Text(
                stringResource(R.string.empty_visited),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
            LazyColumn {
                items(systems, key = { it.name }) { sys ->
                    ListItem(
                        headlineContent = { Text(sys.name) },
                        supportingContent = { Text(supportingText(sys)) },
                        modifier = Modifier.clickable { onOpenSystem(sys.name) }
                    )
                }
            }
        }
    }
}

private fun supportingText(sys: SystemEntity): String {
    val parts = mutableListOf<String>()
    parts.add("${sys.visitCount} visit${if (sys.visitCount == 1) "" else "s"}")
    if (sys.x != null && sys.y != null && sys.z != null) {
        parts.add("%.0f ly from Sol".format(Locale.getDefault(), GalaxyMath.distanceFromSol(sys.x, sys.y, sys.z)))
    }
    return parts.joinToString(" · ")
}
