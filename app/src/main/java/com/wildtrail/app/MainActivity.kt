package com.wildtrail.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wildtrail.app.ui.WildTrailRoot
import com.wildtrail.app.ui.theme.WildTrailTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as WildTrailApp
        val authRepo = app.container.authRepository
        splash.setKeepOnScreenCondition { !authRepo.isInitialised.value }

        setContent {
            WildTrailTheme {
                val authState by authRepo.authState.collectAsStateWithLifecycle()
                WildTrailRoot(
                    appContainer = app.container,
                    authState = authState,
                )
            }
        }
    }
}
