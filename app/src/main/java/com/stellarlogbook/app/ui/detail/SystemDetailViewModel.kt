package com.stellarlogbook.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stellarlogbook.app.data.local.entity.SystemEntity
import com.stellarlogbook.app.data.remote.EdsmApi
import com.stellarlogbook.app.data.remote.SpanshApi
import com.stellarlogbook.app.data.remote.SpanshBody
import com.stellarlogbook.app.data.remote.SystemInfoDto
import com.stellarlogbook.app.data.repo.ExplorerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SystemDetailViewModel(
    private val name: String,
    private val repository: ExplorerRepository,
    private val api: EdsmApi,
    private val spanshApi: SpanshApi
) : ViewModel() {

    val system: Flow<SystemEntity?> = repository.observeSystem(name)

    private val _community = MutableStateFlow<CommunityState>(CommunityState.Idle)
    val community: StateFlow<CommunityState> = _community

    private val _spansh = MutableStateFlow<SpanshState>(SpanshState.Idle)
    val spansh: StateFlow<SpanshState> = _spansh

    init {
        // Lazy lookups: EDSM api-v1/system (also fills missing coords/id64),
        // then Spansh api/system/<id64> for bodies + estimated values.
        viewModelScope.launch {
            _community.value = CommunityState.Loading
            try {
                val info = api.system(name)
                var sys = repository.observeSystem(name).first()
                if (sys != null) {
                    val enriched = sys.copy(
                        id64 = sys.id64 ?: info.id64,
                        x = sys.x ?: info.coords?.x,
                        y = sys.y ?: info.coords?.y,
                        z = sys.z ?: info.coords?.z,
                        knownToCommunity = true
                    )
                    repository.enrichSystem(enriched)
                    sys = enriched
                }
                _community.value = CommunityState.Ready(info)

                val id64 = sys?.id64 ?: repository.observeSystem(name).first()?.id64
                if (id64 != null) {
                    _spansh.value = SpanshState.Loading
                    _spansh.value = try {
                        val resp = spanshApi.system(id64)
                        SpanshState.Ready(resp.record?.name, resp.record?.bodies.orEmpty())
                    } catch (e: Exception) {
                        SpanshState.Error(e.message ?: "Spansh lookup failed")
                    }
                }
            } catch (e: Exception) {
                _community.value = CommunityState.Error(e.message ?: "Lookup failed")
            }
        }
    }

    fun setBookmarked(value: Boolean) {
        viewModelScope.launch { repository.setBookmarked(name, value) }
    }

    fun saveNotes(notes: String) {
        viewModelScope.launch { repository.setNotes(name, notes.ifBlank { null }) }
    }

    sealed interface CommunityState {
        data object Idle : CommunityState
        data object Loading : CommunityState
        data class Ready(val info: SystemInfoDto) : CommunityState
        data class Error(val message: String) : CommunityState
    }

    sealed interface SpanshState {
        data object Idle : SpanshState
        data object Loading : SpanshState
        data class Ready(val systemName: String?, val bodies: List<SpanshBody>) : SpanshState
        data class Error(val message: String) : SpanshState
    }
}
