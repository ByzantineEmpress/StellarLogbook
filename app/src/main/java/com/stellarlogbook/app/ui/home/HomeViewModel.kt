package com.stellarlogbook.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stellarlogbook.app.data.local.entity.SystemEntity
import com.stellarlogbook.app.data.repo.ExplorerRepository
import com.stellarlogbook.app.data.sync.EdsmSyncEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: ExplorerRepository,
    private val syncEngine: EdsmSyncEngine
) : ViewModel() {

    val latestVisit: Flow<SystemEntity?> = repository.latestVisit()
    val visitedCount: Flow<Int> = repository.visitedCount()
    val discoveryCount: Flow<Int> = repository.discoveryCount()
    val codexCount: Flow<Int> = repository.codexCount()
    val lastSyncAt: Flow<Long> = repository.lastSyncAt()

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus

    init {
        // First run after onboarding: kick off the initial full sync automatically.
        viewModelScope.launch {
            val hasCommander = repository.commanderName().first()?.isNotBlank() == true
            if (hasCommander && repository.lastSyncAt().first() == 0L) syncNow()
        }
    }

    fun syncNow() {
        if (_syncStatus.value is SyncStatus.Running) return
        viewModelScope.launch {
            _syncStatus.value = SyncStatus.Running
            _syncStatus.value = syncEngine.sync().fold(
                onSuccess = {
                    repository.markSynced()
                    SyncStatus.Done(it.entries)
                },
                onFailure = { SyncStatus.Error(it.message ?: "Sync failed") }
            )
        }
    }

    sealed interface SyncStatus {
        data object Idle : SyncStatus
        data object Running : SyncStatus
        data class Done(val entries: Int) : SyncStatus
        data class Error(val message: String) : SyncStatus
    }
}
