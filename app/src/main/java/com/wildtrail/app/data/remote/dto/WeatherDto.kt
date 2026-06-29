package com.wildtrail.app.data.remote.dto

import com.google.gson.annotations.SerializedName
import com.wildtrail.app.domain.model.WeatherPoint
import com.wildtrail.app.domain.model.WeatherSnapshot

data class WeatherPointDto(
    @SerializedName("temperature_c") val temperatureC: Double,
    @SerializedName("description") val description: String,
    @SerializedName("icon_id") val iconId: String,
)

data class WeatherResponseDto(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("fetched_at") val fetchedAtEpochSec: Long,
    @SerializedName("current") val current: WeatherPointDto,
    @SerializedName("plus_1h") val plus1h: WeatherPointDto,
    @SerializedName("plus_2h") val plus2h: WeatherPointDto,
)

fun WeatherResponseDto.toDomain(): WeatherSnapshot = WeatherSnapshot(
    latitude = latitude,
    longitude = longitude,
    fetchedAtEpochSec = fetchedAtEpochSec,
    current = current.toDomain(),
    plus1h = plus1h.toDomain(),
    plus2h = plus2h.toDomain(),
)

private fun WeatherPointDto.toDomain(): WeatherPoint = WeatherPoint(
    temperatureC = temperatureC,
    description = description,
    iconId = iconId,
)

