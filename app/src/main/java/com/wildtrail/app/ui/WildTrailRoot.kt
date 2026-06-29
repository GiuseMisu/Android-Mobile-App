package com.wildtrail.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wildtrail.app.data.repository.AuthState
import com.wildtrail.app.di.AppContainer
import com.wildtrail.app.ui.components.WildTrailBottomBar
import com.wildtrail.app.ui.navigation.Destination
import com.wildtrail.app.ui.navigation.WildTrailNavGraph
import com.wildtrail.app.ui.navigation.bottomNavItems

@Composable
fun WildTrailRoot(
    appContainer: AppContainer,
    authState: AuthState,
) {
    val navController = rememberNavController()

    val startDestination = when (authState) {
        is AuthState.SignedIn -> Destination.MainGraph.route
        else -> Destination.AuthGraph.route
    }

    var lastHandledSignedIn by rememberSaveable {
        mutableStateOf<Boolean?>(null)
    }

    LaunchedEffect(authState) {
        val signedInNow = authState is AuthState.SignedIn
        val isLoading = authState is AuthState.Loading
        if (isLoading) return@LaunchedEffect
        if (lastHandledSignedIn == signedInNow) return@LaunchedEffect
        lastHandledSignedIn = signedInNow
        if (signedInNow) {
            navController.navigate(Destination.MainGraph.route) {
                popUpTo(Destination.AuthGraph.route) { inclusive = true }
                launchSingleTop = true
            }
        } else {
            navController.navigate(Destination.AuthGraph.route) {
                popUpTo(Destination.MainGraph.route) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomNavItems.map { it.destination.route }

    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
    ) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) WildTrailBottomBar(navController)
            },
        ) { padding: PaddingValues ->
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                WildTrailNavGraph(
                    navController = navController,
                    startDestination = startDestination,
                )
            }
        }
    }
}
