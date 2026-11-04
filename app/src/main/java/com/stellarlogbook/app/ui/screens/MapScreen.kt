package com.stellarlogbook.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stellarlogbook.app.R
import com.stellarlogbook.app.core.AppContainer
import com.stellarlogbook.app.ui.map.MapViewModel
import com.stellarlogbook.app.ui.map.MapWindow
import kotlin.math.max
import kotlin.math.min

/**
 * Top-down galaxy map (X/Z plane): known-systems sample from the pack,
 * visited systems as dots, the jump trail as a line, first discoveries as
 * gold markers. Drag to pan; buttons to zoom/reset.
 */
@Composable
fun MapScreen(container: AppContainer) {
    val vm: MapViewModel = viewModel { MapViewModel(container.repository, container.packManager) }
    val visited by vm.visited.collectAsState(initial = emptyList())
    val trail by vm.trail.collectAsState(initial = emptyList())
    val discoveries by vm.discoveries.collectAsState(initial = emptyList())
    val background by vm.background.collectAsState()
    val packInstalled by vm.packInstalled.collectAsState()

    var window by remember { mutableStateOf<MapWindow?>(null) }
    var resetKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(visited, resetKey) {
        if (visited.isNotEmpty()) window = fitWindow(visited)
    }
    LaunchedEffect(window?.cx, window?.cz, window?.spanLy) {
        window?.let { vm.loadBackground(it) }
    }

    if (visited.isEmpty()) {
        Text(
            stringResource(R.string.empty_visited),
            modifier = Modifier.padding(16.dp)
        )
        return
    }

    val colors = MaterialTheme.colorScheme
    val bgColor = colors.surfaceVariant.copy(alpha = 0.45f)
    val trailColor = colors.primary
    val dotColor = colors.onSurfaceVariant
    val goldColor = Color(0xFFFFC107)
    val ringColor = Color(0xFF4FC3F7)

    Column(Modifier.fillMaxSize()) {
        val w = window
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(w) {
                    if (w == null) return@pointerInput
                    detectDragGestures { change, drag ->
                        change.consume()
                        val scale = w.spanLy / size.width
                        window = w.copy(
                            cx = w.cx - drag.x * scale,
                            cz = w.cz - drag.y * scale
                        )
                    }
                }
        ) {
            if (w == null) return@Canvas
            fun px(x: Double): Float = (((x - w.cx) / w.spanLy) * size.width + size.width / 2).toFloat()
            fun py(z: Double): Float = (((z - w.cz) / w.spanLy) * size.height + size.height / 2).toFloat()

            background.forEach { p ->
                drawCircle(bgColor, 1.0.dp.toPx(), Offset(px(p[0].toDouble()), py(p[1].toDouble())))
            }
            if (trail.size > 1) {
                val path = Path()
                path.moveTo(px(trail[0][0].toDouble()), py(trail[0][1].toDouble()))
                for (i in 1 until trail.size) {
                    path.lineTo(px(trail[i][0].toDouble()), py(trail[i][1].toDouble()))
                }
                drawPath(path, trailColor, style = Stroke(1.5.dp.toPx()))
            }
            visited.forEach { s ->
                if (s.x != null && s.z != null) {
                    drawCircle(dotColor, 2.2.dp.toPx(), Offset(px(s.x), py(s.z)))
                }
            }
            discoveries.forEach { p ->
                drawCircle(goldColor, 3.4.dp.toPx(), Offset(px(p[0].toDouble()), py(p[1].toDouble())))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { w?.let { window = it.copy(spanLy = max(it.spanLy / 1.6f, 25f)) } }) {
                Text(stringResource(R.string.map_zoom_in))
            }
            Button(onClick = { w?.let { window = it.copy(spanLy = min(it.spanLy * 1.6f, 60000f)) } }) {
                Text(stringResource(R.string.map_zoom_out))
            }
            Button(onClick = { resetKey++ }) {
                Text(stringResource(R.string.map_reset))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendDot(goldColor) { Text(stringResource(R.string.map_legend_discoveries)) }
            LegendDot(trailColor) { Text(stringResource(R.string.map_legend_trail)) }
            LegendDot(dotColor) { Text(stringResource(R.string.map_legend_visited)) }
            if (packInstalled) {
                LegendDot(bgColor) { Text(stringResource(R.string.map_legend_known)) }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
        label()
    }
}

private fun fitWindow(visited: List<com.stellarlogbook.app.data.local.entity.SystemEntity>): MapWindow {
    val pts = visited.mapNotNull { s ->
        if (s.x != null && s.z != null) s.x to s.z else null
    }
    if (pts.isEmpty()) return MapWindow(0.0, 0.0, 200f)
    val minX = pts.minOf { it.first }
    val maxX = pts.maxOf { it.first }
    val minZ = pts.minOf { it.second }
    val maxZ = pts.maxOf { it.second }
    val span = max(max(maxX - minX, maxZ - minZ) * 1.15, 60.0)
    return MapWindow((minX + maxX) / 2.0, (minZ + maxZ) / 2.0, span.toFloat())
}
