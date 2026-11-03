package com.stellarlogbook.app.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stellarlogbook.app.R

/** Phase 3: Compose Canvas scatter plot of visited trail, discoveries and uncharted gaps. */
@Composable
fun MapScreen() {
    Text(
        stringResource(R.string.map_placeholder),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    )
}
