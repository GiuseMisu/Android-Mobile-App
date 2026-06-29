package com.wildtrail.app.ui.tracking

sealed interface TrackingEvent {

    data object ShowEmergencyOverlay : TrackingEvent

    data class EmergencyCallPlaced(val contactName: String) : TrackingEvent
}
