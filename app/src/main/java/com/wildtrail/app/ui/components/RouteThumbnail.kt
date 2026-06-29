package com.wildtrail.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Polyline
import com.wildtrail.app.domain.model.GeoPoint
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

@Composable
fun RouteThumbnail(
    points: List<GeoPoint>,
    modifier: Modifier = Modifier,
) {
    val brand = Color(0xFF2E5D3A)
    val backdrop = MaterialTheme.colorScheme.surfaceVariant

    BoxWithConstraints(modifier = modifier.clip(RoundedCornerShape(8.dp))) {
        val latLngs = remember(points) { points.map { LatLng(it.lat, it.lng) } }

        if (latLngs.size < 2 || !constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
            Box(Modifier.fillMaxSize().background(backdrop))
            return@BoxWithConstraints
        }

        // Map zoom levels are defined in density-independent pixels, but Compose constraints
        // are in physical pixels — convert, or we over-zoom on high-density screens and clip.
        val density = LocalDensity.current.density
        val widthDp = constraints.maxWidth / density
        val heightDp = constraints.maxHeight / density

        // Lite-mode maps render a static snapshot from the *initial* camera position and ignore
        // later camera changes, so the correct fit must be computed up front, not after load.
        val camera = remember(latLngs, widthDp, heightDp) {
            val bounds = LatLngBounds.Builder().apply { latLngs.forEach { include(it) } }.build()
            CameraPosition.fromLatLngZoom(bounds.center, boundsZoom(bounds, widthDp, heightDp))
        }

        val cameraPositionState = remember(camera) { CameraPositionState(position = camera) }

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
            cameraPositionState = cameraPositionState,
            properties = MapProperties(mapType = MapType.NORMAL),
            uiSettings = NonInteractiveUiSettings,
        ) {
            Polyline(points = latLngs, color = Color.White, width = 10f)
            Polyline(points = latLngs, color = brand, width = 6f)
        }
    }
}

private val NonInteractiveUiSettings = MapUiSettings(
    compassEnabled = false,
    indoorLevelPickerEnabled = false,
    mapToolbarEnabled = false,
    myLocationButtonEnabled = false,
    rotationGesturesEnabled = false,
    scrollGesturesEnabled = false,
    scrollGesturesEnabledDuringRotateOrZoom = false,
    tiltGesturesEnabled = false,
    zoomGesturesEnabled = false,
    zoomControlsEnabled = false,
)

private const val WORLD_DP = 256.0
private const val MAX_ZOOM = 19.0

// Fraction of each axis the route should occupy, leaving ~8% margin per side.
private const val FIT_FRACTION = 0.84

private fun boundsZoom(bounds: LatLngBounds, widthDp: Float, heightDp: Float): Float {
    val ne = bounds.northeast
    val sw = bounds.southwest

    val latFraction = (latRad(ne.latitude) - latRad(sw.latitude)) / PI
    val lngDiff = ne.longitude - sw.longitude
    val lngFraction = (if (lngDiff < 0) lngDiff + 360.0 else lngDiff) / 360.0

    val latZoom = if (latFraction <= 0.0) MAX_ZOOM else zoomFor(heightDp * FIT_FRACTION, latFraction)
    val lngZoom = if (lngFraction <= 0.0) MAX_ZOOM else zoomFor(widthDp * FIT_FRACTION, lngFraction)

    return minOf(latZoom, lngZoom, MAX_ZOOM).coerceIn(2.0, MAX_ZOOM).toFloat()
}

private fun zoomFor(mapDp: Double, fraction: Double): Double =
    ln(mapDp / WORLD_DP / fraction) / ln(2.0)

private fun latRad(latDeg: Double): Double {
    val s = sin(latDeg * PI / 180.0)
    val radX2 = ln((1 + s) / (1 - s)) / 2.0
    return max(min(radX2, PI), -PI) / 2.0
}
