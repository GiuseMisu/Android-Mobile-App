package com.wildtrail.app.domain.usecase

import com.wildtrail.app.data.repository.WeatherRepository
import com.wildtrail.app.domain.model.WeatherSnapshot

class GetWeatherUseCase(
    private val weatherRepository: WeatherRepository,
) {
    suspend operator fun invoke(latitude: Double, longitude: Double): Result<WeatherSnapshot> =
        weatherRepository.refreshWeather(latitude = latitude, longitude = longitude)
}

