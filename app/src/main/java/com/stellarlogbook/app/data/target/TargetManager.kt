package com.stellarlogbook.app.data.target

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.stellarlogbook.app.MainActivity
import com.stellarlogbook.app.R
import com.stellarlogbook.app.data.prefs.UserPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * The "current target" feature: pick a system in Explore, get a persistent
 * notification with the name + distance so it survives while you're in game.
 */
class TargetManager(
    context: Context,
    private val prefs: UserPrefs
) {

    data class Target(val name: String, val x: Float, val y: Float, val z: Float)

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val target: Flow<Target?> = combine(
        prefs.targetName, prefs.targetX, prefs.targetY, prefs.targetZ
    ) { name, x, y, z ->
        if (name.isNullOrBlank() || x == null || y == null || z == null) null
        else Target(name, x, y, z)
    }

    suspend fun setTarget(name: String, x: Float, y: Float, z: Float, distanceLy: Double? = null) {
        prefs.setTarget(name, x, y, z)
        val text = distanceLy?.let {
            appContext.getString(R.string.target_notification_text_dist, "%.0f".format(Locale.getDefault(), it))
        } ?: appContext.getString(R.string.target_notification_text)
        showNotification(name, text)
    }

    fun clear() {
        scope.launch {
            prefs.clearTarget()
            NotificationManagerCompat.from(appContext).cancel(NOTIFICATION_ID)
        }
    }

    private fun showNotification(title: String, text: String) {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, appContext.getString(R.string.target_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val contentIntent = PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(false)
            .build()
        try {
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted (API 33+): the target still works in-app.
        }
    }

    companion object {
        const val CHANNEL_ID = "target"
        const val NOTIFICATION_ID = 42
    }
}
