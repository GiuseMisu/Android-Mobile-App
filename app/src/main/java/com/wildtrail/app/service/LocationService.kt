package com.wildtrail.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.wildtrail.app.MainActivity
import com.wildtrail.app.WildTrailApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps GPS flowing while the screen is off / the app is backgrounded.
 *
 * It owns the single FusedLocationProvider subscription (via [com.wildtrail.app.util.LocationTracker])
 * and republishes every fix into LocationTracker.liveLocations, which the tracking ViewModel observes.
 * Because Android only delivers continuous background location to apps with a running
 * foreground service of type `location`, this is what fixes the "straight line while screen off" bug.
 */
class LocationService : android.app.Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTracking()
            return START_NOT_STICKY
        }

        startAsForeground()

        if (job?.isActive != true) {
            val tracker = (application as WildTrailApp).container.locationTracker
            job = scope.launch {
                runCatching {
                    tracker.observeLocation(intervalMs = 2_000L).collect { point ->
                        tracker.publishLocation(point)
                    }
                }
            }
        }
        // STICKY so the OS restarts the service (and tracking) if it gets killed mid-hike.
        return START_STICKY
    }

    private fun startAsForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(), type)
    }

    private fun stopTracking() {
        job?.cancel()
        job = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording hike")
            .setContentText("Tracking your route — even with the screen off.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(openApp)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Hike tracking",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows while a hike is being recorded so GPS keeps running."
            setShowBadge(false)
        }
        mgr.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.wildtrail.app.action.START_LOCATION"
        const val ACTION_STOP = "com.wildtrail.app.action.STOP_LOCATION"
        private const val CHANNEL_ID = "hike_tracking"
        private const val NOTIF_ID = 4711
    }
}
