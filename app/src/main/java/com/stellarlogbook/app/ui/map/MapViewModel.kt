package com.stellarlogbook.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stellarlogbook.app.data.local.entity.JumpEntity
import com.stellarlogbook.app.data.local.entity.SystemEntity
import com.stellarlogbook.app.data.pack.GalaxyPack
import com.stellarlogbook.app.data.pack.PackManager
import com.stellarlogbook.app.data.repo.ExplorerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class MapWindow(val cx: Double, val cz: Double, val spanLy: Float)

/** Points are (x, z) float pairs on the top-down galactic plane. */
class MapViewModel(
    private val repository: ExplorerRepository,
    private val packManager: PackManager
) : ViewModel() {

    val visited: Flow<List<SystemEntity>> = repository.visitedSystems()

    /** Trail: consecutive jump destinations in time order, as (x, z) points. */
    val trail: Flow<List<FloatArray>> = combine(
        repository.recentJumps(5000),
        repository.visitedSystems()
    ) { jumps, systems ->
        val coords = systems.coordsByName()
        jumps.mapNotNull { j -> coords[j.toSystemName]?.let { floatArrayOf(it[0], it[2]) } }
    }

    /** First-discovery bodies positioned by their system, as (x, z) points. */
    val discoveries: Flow<List<FloatArray>> = combine(
        repository.myDiscoveryBodies(),
        repository.visitedSystems()
    ) { bodies, systems ->
        val coords = systems.coordsByName()
        bodies.mapNotNull { b -> coords[b.systemName]?.let { floatArrayOf(it[0], it[2]) } }
    }

    private val _background = MutableStateFlow<List<FloatArray>>(emptyList())
    val background: StateFlow<List<FloatArray>> = _background

    private val _packInstalled = MutableStateFlow(packManager.isInstalled())
    val packInstalled: StateFlow<Boolean> = _packInstalled

    /** Sample known systems from installed pack slices inside the window. */
    fun loadBackground(window: MapWindow) {
        if (!packManager.isInstalled()) return
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val pts = ArrayList<FloatArray>()
                val x0 = (window.cx - window.spanLy).toFloat()
                val x1 = (window.cx + window.spanLy).toFloat()
                val z0 = (window.cz - window.spanLy).toFloat()
                val z1 = (window.cz + window.spanLy).toFloat()
                for (file in packManager.installedSlices()) {
                    GalaxyPack(file).use { pack ->
                        var stride = 0
                        pack.forEachInXRange(x0, x1) { x, _, z ->
                            if (z in z0..z1) {
                                if (stride++ % SAMPLE_EVERY == 0) pts.add(floatArrayOf(x, z))
                            }
                        }
                    }
                }
                _background.value = if (pts.size > MAX_BACKGROUND) {
                    val step = pts.size / MAX_BACKGROUND
                    (0 until MAX_BACKGROUND).map { pts[it * step] }
                } else {
                    pts
                }
            } catch (_: Exception) {
                _background.value = emptyList()
            }
        }
    }

    fun refreshPackStatus() {
        _packInstalled.value = packManager.isInstalled()
    }

    private fun List<SystemEntity>.coordsByName(): Map<String, FloatArray> =
        mapNotNull { s ->
            if (s.x != null && s.y != null && s.z != null) {
                s.name to floatArrayOf(s.x.toFloat(), s.y.toFloat(), s.z.toFloat())
            } else null
        }.toMap()

    companion object {
        const val SAMPLE_EVERY = 200
        const val MAX_BACKGROUND = 4000
    }
}
