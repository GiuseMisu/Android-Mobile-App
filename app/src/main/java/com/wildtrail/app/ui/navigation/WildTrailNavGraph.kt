package com.wildtrail.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.wildtrail.app.ui.achievements.AchievementsRoute
import com.wildtrail.app.ui.auth.LoginRoute
import com.wildtrail.app.ui.explore.ExploreRoute
import com.wildtrail.app.ui.hike.HikeDetailRoute
import com.wildtrail.app.ui.home.HomeRoute
import com.wildtrail.app.ui.liked.LikedHikesRoute
import com.wildtrail.app.ui.profile.ProfileRoute
import com.wildtrail.app.ui.review.SubmitReviewRoute
import com.wildtrail.app.ui.settings.SettingsRoute
import com.wildtrail.app.ui.tracking.TrackingRoute

@Composable
fun WildTrailNavGraph(
    navController: NavHostController,
    startDestination: String,
) {
    val openHike: (String) -> Unit = { hikeId ->
        navController.navigate(Destination.HikeDetail.create(hikeId))
    }
    val openUser: (String) -> Unit = { uid ->
        navController.navigate(Destination.UserProfile.create(uid))
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        navigation(
            route = Destination.AuthGraph.route,
            startDestination = Destination.Login.route,
        ) {
            composable(Destination.Login.route) {
                LoginRoute()
            }
        }

        navigation(
            route = Destination.MainGraph.route,
            startDestination = Destination.Home.route,
        ) {
            composable(Destination.Home.route) {
                HomeRoute(onHikeClick = openHike, onUserClick = openUser)
            }
            composable(Destination.Explore.route) {
                ExploreRoute(onHikeClick = openHike, onUserClick = openUser)
            }
            composable(Destination.Track.route) {
                TrackingRoute()
            }
            composable(Destination.Profile.route) {
                ProfileRoute(
                    targetUid = null,
                    onBack = null,
                    onHikeClick = openHike,
                    onUserClick = openUser,
                    onOpenSettings = {
                        navController.navigate(Destination.Settings.route)
                    },
                    onOpenLiked = {
                        navController.navigate(Destination.LikedHikes.route)
                    },
                    onOpenAchievements = {
                        navController.navigate(Destination.Achievements.route)
                    },
                )
            }
            composable(Destination.Settings.route) {
                SettingsRoute(onBack = { navController.popBackStack() })
            }
            composable(Destination.LikedHikes.route) {
                LikedHikesRoute(
                    onBack = { navController.popBackStack() },
                    onHikeClick = openHike,
                    onUserClick = openUser,
                )
            }
            composable(Destination.Achievements.route) {
                AchievementsRoute(onBack = { navController.popBackStack() })
            }
            composable(
                route = Destination.HikeDetail.route,
                arguments = listOf(
                    navArgument(Destination.HikeDetail.ARG_HIKE_ID) { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val hikeId = backStackEntry.arguments?.getString(Destination.HikeDetail.ARG_HIKE_ID)
                    .orEmpty()
                HikeDetailRoute(
                    hikeId = hikeId,
                    onBack = { navController.popBackStack() },
                    onUserClick = openUser,
                    onAddReview = {
                        navController.navigate(Destination.SubmitReview.create(hikeId))
                    },
                )
            }
            composable(
                route = Destination.SubmitReview.route,
                arguments = listOf(
                    navArgument(Destination.SubmitReview.ARG_HIKE_ID) { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val hikeId = backStackEntry.arguments?.getString(Destination.SubmitReview.ARG_HIKE_ID)
                    .orEmpty()
                SubmitReviewRoute(
                    hikeId = hikeId,
                    onDone = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Destination.UserProfile.route,
                arguments = listOf(
                    navArgument(Destination.UserProfile.ARG_UID) { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                val uid = backStackEntry.arguments?.getString(Destination.UserProfile.ARG_UID)
                    .orEmpty()
                ProfileRoute(
                    targetUid = uid,
                    onBack = { navController.popBackStack() },
                    onHikeClick = openHike,
                    onUserClick = openUser,
                )
            }
        }
    }
}
