package com.wildtrail.app.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Thin starter/stopper for [LocationService] so ViewModels can control the foreground
 * service without holding an Activity context.
 */
class LocationServiceController(context: Context) {

    private val appContext = context.applicationContext

    fun start() {
        val intent = Intent(appContext, LocationService::class.java).apply {
            action = LocationService.ACTION_START
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    fun stop() {
        // Destroys the service (onDestroy cancels the location job; the foreground
        // notification is removed automatically). Safe whether or not it's running.
        runCatching { appContext.stopService(Intent(appContext, LocationService::class.java)) }
    }
}
