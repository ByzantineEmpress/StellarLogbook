package com.stellarlogbook.app

import android.app.Application
import com.stellarlogbook.app.core.AppContainer
import com.stellarlogbook.app.data.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class StellarLogbookApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            val hasCommander = container.userPrefs.commanderName.first()?.isNotBlank() == true
            if (hasCommander) {
                val interval = container.userPrefs.syncIntervalHours.first()
                val wifiOnly = container.userPrefs.wifiOnly.first()
                SyncScheduler.ensure(this@StellarLogbookApp, interval, wifiOnly)
            }
        }
    }
}
