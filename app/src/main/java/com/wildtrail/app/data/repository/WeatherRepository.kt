package com.wildtrail.app.data.repository

import android.util.Log
import com.wildtrail.app.data.local.dao.WeatherDao
import com.wildtrail.app.data.local.entity.toDomain
import com.wildtrail.app.data.local.entity.toEntity
import com.wildtrail.app.data.remote.WeatherApiService
import com.wildtrail.app.data.remote.dto.toDomain
import com.wildtrail.app.domain.model.WeatherSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

open class WeatherRepository(
    private val weatherApiService: WeatherApiService,
    private val weatherDao: WeatherDao,
) {

    open fun observeCachedWeather(): Flow<WeatherSnapshot?> =
        weatherDao.observeCached().map { it?.toDomain() }

    open suspend fun getCachedWeather(): WeatherSnapshot? =
        weatherDao.getCached()?.toDomain()

    open suspend fun refreshWeather(latitude: Double, longitude: Double): Result<WeatherSnapshot> =
        runCatching {
            val remote = weatherApiService.getWeather(latitude = latitude, longitude = longitude).toDomain()
            weatherDao.upsert(remote.toEntity())
            remote
        }.onFailure { Log.w(TAG, "Weather refresh failed", it) }

    private companion object {
        const val TAG = "WeatherRepository"
    }
}

