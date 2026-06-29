package com.wildtrail.app

import android.app.Application
import com.wildtrail.app.di.AppContainer
import com.wildtrail.app.di.DefaultAppContainer

class WildTrailApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
