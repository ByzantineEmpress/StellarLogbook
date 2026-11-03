package com.stellarlogbook.app.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stellarlogbook.app.data.local.entity.SystemEntity
import com.stellarlogbook.app.data.remote.EdsmApi
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
    private val api: EdsmApi
) : ViewModel() {

    val system: Flow<SystemEntity?> = repository.observeSystem(name)

    private val _community = MutableStateFlow<CommunityState>(CommunityState.Idle)
    val community: StateFlow<CommunityState> = _community

    init {
        // Lazy community lookup (EDSM api-v1/system); also fills in missing coords/id64.
        viewModelScope.launch {
            _community.value = CommunityState.Loading
            _community.value = try {
                val info = api.system(name)
                repository.observeSystem(name).first()?.let { sys ->
                    repository.enrichSystem(
                        sys.copy(
                            id64 = sys.id64 ?: info.id64,
                            x = sys.x ?: info.coords?.x,
                            y = sys.y ?: info.coords?.y,
                            z = sys.z ?: info.coords?.z,
                            knownToCommunity = true
                        )
                    )
                }
                CommunityState.Ready(info)
            } catch (e: Exception) {
                CommunityState.Error(e.message ?: "Lookup failed")
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
}
