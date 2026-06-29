package com.wildtrail.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Destination(val route: String) {

    data object AuthGraph : Destination("auth_graph")
    data object Login : Destination("login")

    data object MainGraph : Destination("main_graph")
    data object Home : Destination("home")
    data object Explore : Destination("explore")
    data object Track : Destination("track")
    data object Profile : Destination("profile")

    data object Settings : Destination("settings")

    data object LikedHikes : Destination("liked_hikes")

    data object Achievements : Destination("achievements")

    data object HikeDetail : Destination("hike_detail/{hikeId}") {
        fun create(hikeId: String) = "hike_detail/$hikeId"
        const val ARG_HIKE_ID = "hikeId"
    }

    data object SubmitReview : Destination("submit_review/{hikeId}") {
        fun create(hikeId: String) = "submit_review/$hikeId"
        const val ARG_HIKE_ID = "hikeId"
    }

    data object UserProfile : Destination("profile/{uid}") {
        fun create(uid: String) = "profile/$uid"
        const val ARG_UID = "uid"
    }
}

data class BottomNavItem(
    val destination: Destination,
    val icon: ImageVector,
    val label: String,
)

val bottomNavItems: List<BottomNavItem> = listOf(
    BottomNavItem(Destination.Home, Icons.Filled.Home, "Home"),
    BottomNavItem(Destination.Explore, Icons.Filled.Search, "Explore"),
    BottomNavItem(Destination.Track, Icons.Filled.PlayArrow, "Track"),
    BottomNavItem(Destination.Profile, Icons.Filled.Person, "Profile"),
)
