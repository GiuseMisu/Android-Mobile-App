package com.wildtrail.app.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.wildtrail.app.domain.model.GeoPoint
import com.wildtrail.app.domain.model.HikeMediaItem
import com.wildtrail.app.domain.model.HikeMediaType

@Composable
fun RouteMap(
    points: List<GeoPoint>,
    modifier: Modifier = Modifier,
    follow: Boolean = false,
    showCurrentMarker: Boolean = false,
    currentLocation: GeoPoint? = null,
    mediaItems: List<HikeMediaItem> = emptyList(),
) {
    if (points.isEmpty() && currentLocation == null) {
        androidx.compose.foundation.layout.Box(
            modifier = modifier,
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Text(
                "Waiting for GPS…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val latLngs = remember(points) { points.map { LatLng(it.lat, it.lng) } }
    val currentLatLng = currentLocation?.let { LatLng(it.lat, it.lng) }
    val focus = latLngs.firstOrNull() ?: currentLatLng!!
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(focus, 16f)
    }

    if (latLngs.isEmpty()) {
        LaunchedEffect(currentLatLng) {
            currentLatLng?.let {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(it, 16f))
            }
        }
    } else if (follow) {
        LaunchedEffect(latLngs.lastOrNull()) {
            latLngs.lastOrNull()?.let { newest ->
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(newest, 17f),
                )
            }
        }
    } else {
        LaunchedEffect(latLngs.size) {
            if (latLngs.size >= 2) {
                val bounds = LatLngBounds.Builder().apply {
                    latLngs.forEach { include(it) }
                }.build()
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(bounds, 80),
                )
            }
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = false),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = !follow,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false,
        ),
    ) {
        if (latLngs.isNotEmpty()) {
            Polyline(
                points = latLngs,
                color = androidx.compose.ui.graphics.Color(0xFF2E5D3A),
                width = 10f,
            )
        }
        if (showCurrentMarker) {
            (latLngs.lastOrNull() ?: currentLatLng)?.let { current ->
                Marker(state = MarkerState(position = current), title = "You")
            }
        }
        val orderedMedia = remember(mediaItems) { mediaItems.sortedBy { it.timestamp } }
        var photoCount = 0
        var audioCount = 0
        orderedMedia.forEach { media ->
            val n = when (media.type) {
                HikeMediaType.PHOTO -> ++photoCount
                HikeMediaType.AUDIO -> ++audioCount
            }
            val hue = when (media.type) {
                HikeMediaType.PHOTO -> BitmapDescriptorFactory.HUE_AZURE
                HikeMediaType.AUDIO -> BitmapDescriptorFactory.HUE_ORANGE
            }
            val label = when (media.type) {
                HikeMediaType.PHOTO -> "Photo $n"
                HikeMediaType.AUDIO -> "Voice $n"
            }
            Marker(
                state = MarkerState(position = LatLng(media.lat, media.lng)),
                title = label,
                icon = BitmapDescriptorFactory.defaultMarker(hue),
            )
        }
    }
}
