package com.wildtrail.app.ui.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.wildtrail.app.WildTrailApp
import com.wildtrail.app.data.repository.WeatherRepository
import com.wildtrail.app.domain.model.WeatherSnapshot
import com.wildtrail.app.domain.usecase.GetWeatherUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

sealed interface WeatherUiState {
    data class Loading(val lastKnown: WeatherSnapshot?) : WeatherUiState
    data class Success(val weather: WeatherSnapshot, val fromCache: Boolean) : WeatherUiState
    data class Error(val message: String, val lastKnown: WeatherSnapshot?) : WeatherUiState
}

class WeatherViewModel(
    private val weatherRepository: WeatherRepository,
    private val getWeatherUseCase: GetWeatherUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading(lastKnown = null))
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()

    private var latestCoordinates: Pair<Double, Double>? = null
    private var lastKnownSnapshot: WeatherSnapshot? = null

    init {
        viewModelScope.launch {
            weatherRepository.observeCachedWeather().collect { cached ->
                if (cached == null) return@collect
                lastKnownSnapshot = cached
                val currentState = _uiState.value
                when (currentState) {
                    is WeatherUiState.Loading -> {
                        if (currentState.lastKnown == null) {
                            _uiState.value = WeatherUiState.Success(cached, fromCache = true)
                        }
                    }

                    is WeatherUiState.Success -> {
                        if (currentState.fromCache) {
                            _uiState.value = currentState.copy(weather = cached)
                        }
                    }

                    is WeatherUiState.Error -> {
                        if (currentState.lastKnown == null) {
                            _uiState.value = currentState.copy(lastKnown = cached)
                        }
                    }
                }
            }
        }
    }

    fun onCoordinatesUpdated(latitude: Double, longitude: Double) {
        latestCoordinates = latitude to longitude
    }

    fun refreshWeather() {
        val (latitude, longitude) = latestCoordinates ?: run {
            _uiState.value = WeatherUiState.Error(
                message = "Waiting for GPS fix to fetch weather",
                lastKnown = lastKnownSnapshot,
            )
            return
        }

        _uiState.value = WeatherUiState.Loading(lastKnown = lastKnownSnapshot)
        viewModelScope.launch {
            getWeatherUseCase(latitude = latitude, longitude = longitude)
                .onSuccess { weather ->
                    lastKnownSnapshot = weather
                    _uiState.value = WeatherUiState.Success(weather = weather, fromCache = false)
                }
                .onFailure { err ->
                    _uiState.value = WeatherUiState.Error(
                        message = err.message ?: "Could not fetch weather",
                        lastKnown = lastKnownSnapshot ?: weatherRepository.getCachedWeather(),
                    )
                }
        }
    }

    companion object {
        fun factory(): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WildTrailApp)
                val repository = app.container.weatherRepository
                WeatherViewModel(
                    weatherRepository = repository,
                    getWeatherUseCase = GetWeatherUseCase(repository),
                )
            }
        }
    }
}

