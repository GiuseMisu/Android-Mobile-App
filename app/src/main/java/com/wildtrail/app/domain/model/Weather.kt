package com.wildtrail.app.domain.model

data class WeatherPoint(
    val temperatureC: Double,
    val description: String,
    val iconId: String,
)

data class WeatherSnapshot(
    val latitude: Double,
    val longitude: Double,
    val fetchedAtEpochSec: Long,
    val current: WeatherPoint,
    val plus1h: WeatherPoint,
    val plus2h: WeatherPoint,
)

