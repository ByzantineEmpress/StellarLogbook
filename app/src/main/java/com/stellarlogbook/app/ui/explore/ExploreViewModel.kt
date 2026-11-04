package com.stellarlogbook.app.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stellarlogbook.app.core.GalaxyMath
import com.stellarlogbook.app.data.local.entity.SystemEntity
import com.stellarlogbook.app.data.pack.GalaxyPack
import com.stellarlogbook.app.data.pack.PackDownloader
import com.stellarlogbook.app.data.pack.PackManager
import com.stellarlogbook.app.data.remote.EdsmApi
import com.stellarlogbook.app.data.remote.SphereSystemDto
import com.stellarlogbook.app.data.repo.ExplorerRepository
import com.stellarlogbook.app.data.target.TargetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class ExploreViewModel(
    private val repository: ExplorerRepository,
    private val api: EdsmApi,
    private val packManager: PackManager,
    private val packDownloader: PackDownloader,
    private val targetManager: TargetManager
) : ViewModel() {

    val latestVisit: Flow<SystemEntity?> = repository.latestVisit()

    private val _packInstalled = MutableStateFlow(packManager.isInstalled())
    val packInstalled: StateFlow<Boolean> = _packInstalled

    private val _unvisited = MutableStateFlow<ResultsState<SphereSystemDto>>(ResultsState.Idle)
    val unvisited: StateFlow<ResultsState<SphereSystemDto>> = _unvisited

    private val _gaps = MutableStateFlow<ResultsState<GalaxyPack.Gap>>(ResultsState.Idle)
    val gaps: StateFlow<ResultsState<GalaxyPack.Gap>> = _gaps

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download: StateFlow<DownloadState> = _download

    /** Known systems within radius of my position that I haven't visited. */
    fun findUnvisited(radiusLy: Float) {
        if (_busy.value) return
        _busy.value = true
        _unvisited.value = ResultsState.Loading
        viewModelScope.launch {
            try {
                val position = position()
                if (position == null) {
                    _unvisited.value = ResultsState.Error("No position yet — sync or import first")
                } else {
                    val visited = repository.visitedSystems().first().map { it.name }.toHashSet()
                    val sphere = api.sphereSystems(
                        x = position.x,
                        y = position.y,
                        z = position.z,
                        radius = radiusLy.toInt()
                    )
                    _unvisited.value = ResultsState.Ready(sphere.filter { it.name !in visited })
                }
            } catch (e: Exception) {
                _unvisited.value = ResultsState.Error(e.message ?: "Sphere search failed")
            } finally {
                _busy.value = false
            }
        }
    }

    /** Nearest locations with no community-reported systems (Uncharted Pack). */
    fun findUncharted(searchRadiusLy: Float, maxDistanceLy: Float, maxResults: Int = 10) {
        if (_busy.value) return
        _busy.value = true
        _gaps.value = ResultsState.Loading
        viewModelScope.launch {
            try {
                val position = position()
                if (position == null) {
                    _gaps.value = ResultsState.Error("No position yet — sync or import first")
                } else {
                    val pack = packManager.open()
                    if (pack == null) {
                        _gaps.value = ResultsState.Error("Uncharted Pack not installed — download it first")
                    } else {
                        val gaps = withContext(Dispatchers.Default) {
                            pack.use {
                                it.findNearestGaps(
                                    originX = position.x!!.toFloat(),
                                    originY = position.y!!.toFloat(),
                                    originZ = position.z!!.toFloat(),
                                    searchRadiusLy = searchRadiusLy,
                                    maxDistanceLy = maxDistanceLy,
                                    maxResults = maxResults
                                )
                            }
                        }
                        _gaps.value = ResultsState.Ready(gaps)
                    }
                }
            } catch (e: Exception) {
                _gaps.value = ResultsState.Error(e.message ?: "Gap search failed")
            } finally {
                _busy.value = false
            }
        }
    }

    /** Download the pack slice covering my current position. */
    fun downloadPack() {
        if (_download.value is DownloadState.Running) return
        _download.value = DownloadState.Running(0f, "Connecting…")
        viewModelScope.launch {
            try {
                val position = position()
                if (position == null) {
                    _download.value = DownloadState.Error("No position yet — sync or import first")
                    return@launch
                }
                packDownloader.downloadSliceFor(position.x!!.toFloat()) { progress ->
                    val frac = if (progress.totalBytes > 0) {
                        progress.doneBytes.toFloat() / progress.totalBytes
                    } else 0f
                    _download.value = DownloadState.Running(frac, progress.currentFile)
                }.fold(
                    onSuccess = {
                        _packInstalled.value = packManager.isInstalled()
                        _download.value = DownloadState.Done
                    },
                    onFailure = { e ->
                        _download.value = DownloadState.Error(e.message ?: "Download failed")
                    }
                )
            } catch (e: Exception) {
                _download.value = DownloadState.Error(e.message ?: "Download failed")
            }
        }
    }

    fun refreshPackStatus() {
        _packInstalled.value = packManager.isInstalled()
    }

    /** Set a known system as the current target (persistent notification). */
    fun setTargetFor(sys: SphereSystemDto) {
        val name = sys.name ?: return
        val c = sys.coords ?: return
        if (c.x == null || c.y == null || c.z == null) return
        viewModelScope.launch {
            val dist = distanceFromHere(c.x, c.y, c.z)
            targetManager.setTarget(name, c.x.toFloat(), c.y.toFloat(), c.z.toFloat(), dist)
        }
    }

    /** Set an uncharted gap as the current target. */
    fun setTargetForGap(gap: GalaxyPack.Gap) {
        val name = "Uncharted %.0f, %.0f, %.0f".format(Locale.getDefault(), gap.x, gap.y, gap.z)
        viewModelScope.launch {
            val dist = distanceFromHere(gap.x.toDouble(), gap.y.toDouble(), gap.z.toDouble())
            targetManager.setTarget(name, gap.x, gap.y, gap.z, dist)
        }
    }

    private suspend fun distanceFromHere(x: Double, y: Double, z: Double): Double? {
        val sys = repository.latestVisit().first() ?: return null
        if (sys.x == null || sys.y == null || sys.z == null) return null
        return GalaxyMath.distance(sys.x, sys.y, sys.z, x, y, z)
    }

    private suspend fun position(): SystemEntity? {
        val sys = repository.latestVisit().first() ?: return null
        if (sys.x == null || sys.y == null || sys.z == null) return null
        return sys
    }

    sealed interface ResultsState<out T> {
        data object Idle : ResultsState<Nothing>
        data object Loading : ResultsState<Nothing>
        data class Ready<T>(val items: List<T>) : ResultsState<T>
        data class Error(val message: String) : ResultsState<Nothing>
    }

    sealed interface DownloadState {
        data object Idle : DownloadState
        data class Running(val progress: Float, val currentFile: String) : DownloadState
        data object Done : DownloadState
        data class Error(val message: String) : DownloadState
    }
}
