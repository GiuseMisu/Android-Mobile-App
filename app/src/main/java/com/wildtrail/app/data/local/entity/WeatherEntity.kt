package com.wildtrail.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.wildtrail.app.domain.model.WeatherPoint
import com.wildtrail.app.domain.model.WeatherSnapshot

@Entity(tableName = "weather_cache")
data class WeatherEntity(
    @PrimaryKey val cacheId: Int = CACHE_ID,
    val latitude: Double,
    val longitude: Double,
    val fetchedAtEpochSec: Long,
    val currentTemperatureC: Double,
    val currentDescription: String,
    val currentIconId: String,
    val plus1hTemperatureC: Double,
    val plus1hDescription: String,
    val plus1hIconId: String,
    val plus2hTemperatureC: Double,
    val plus2hDescription: String,
    val plus2hIconId: String,
) {
    companion object {
        const val CACHE_ID = 1
    }
}

fun WeatherEntity.toDomain(): WeatherSnapshot = WeatherSnapshot(
    latitude = latitude,
    longitude = longitude,
    fetchedAtEpochSec = fetchedAtEpochSec,
    current = WeatherPoint(
        temperatureC = currentTemperatureC,
        description = currentDescription,
        iconId = currentIconId,
    ),
    plus1h = WeatherPoint(
        temperatureC = plus1hTemperatureC,
        description = plus1hDescription,
        iconId = plus1hIconId,
    ),
    plus2h = WeatherPoint(
        temperatureC = plus2hTemperatureC,
        description = plus2hDescription,
        iconId = plus2hIconId,
    ),
)

fun WeatherSnapshot.toEntity(): WeatherEntity = WeatherEntity(
    cacheId = WeatherEntity.CACHE_ID,
    latitude = latitude,
    longitude = longitude,
    fetchedAtEpochSec = fetchedAtEpochSec,
    currentTemperatureC = current.temperatureC,
    currentDescription = current.description,
    currentIconId = current.iconId,
    plus1hTemperatureC = plus1h.temperatureC,
    plus1hDescription = plus1h.description,
    plus1hIconId = plus1h.iconId,
    plus2hTemperatureC = plus2h.temperatureC,
    plus2hDescription = plus2h.description,
    plus2hIconId = plus2h.iconId,
)

