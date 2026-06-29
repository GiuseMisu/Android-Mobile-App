package com.wildtrail.app.ui.tracking

import com.wildtrail.app.data.local.dao.WeatherDao
import com.wildtrail.app.data.local.entity.WeatherEntity
import com.wildtrail.app.data.remote.WeatherApiService
import com.wildtrail.app.data.remote.dto.WeatherResponseDto
import com.wildtrail.app.data.repository.WeatherRepository
import com.wildtrail.app.domain.model.WeatherPoint
import com.wildtrail.app.domain.model.WeatherSnapshot
import com.wildtrail.app.domain.usecase.GetWeatherUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeRepository: FakeWeatherRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeWeatherRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `cached weather is exposed as success on startup`() = runTest {
        val cached = sampleWeather()
        fakeRepository.cachedWeatherFlow.value = cached

        val viewModel = WeatherViewModel(
            weatherRepository = fakeRepository,
            getWeatherUseCase = GetWeatherUseCase(fakeRepository),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is WeatherUiState.Success)
        state as WeatherUiState.Success
        assertTrue(state.fromCache)
        assertEquals(cached, state.weather)
    }

    @Test
    fun `refresh without coordinates returns error`() = runTest {
        val viewModel = WeatherViewModel(
            weatherRepository = fakeRepository,
            getWeatherUseCase = GetWeatherUseCase(fakeRepository),
        )

        viewModel.refreshWeather()
        val state = viewModel.uiState.value
        assertTrue(state is WeatherUiState.Error)
        state as WeatherUiState.Error
        assertTrue(state.message.contains("GPS fix"))
    }

    @Test
    fun `failed refresh keeps last known weather in error state`() = runTest {
        val cached = sampleWeather()
        fakeRepository.cachedWeatherFlow.value = cached
        fakeRepository.cachedWeather = cached
        fakeRepository.nextRefreshResult = Result.failure(IllegalStateException("No network"))

        val viewModel = WeatherViewModel(
            weatherRepository = fakeRepository,
            getWeatherUseCase = GetWeatherUseCase(fakeRepository),
        )
        viewModel.onCoordinatesUpdated(latitude = 45.0, longitude = 9.0)
        viewModel.refreshWeather()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is WeatherUiState.Error)
        state as WeatherUiState.Error
        assertEquals(cached, state.lastKnown)
        assertEquals("No network", state.message)
    }

    private fun sampleWeather(): WeatherSnapshot = WeatherSnapshot(
        latitude = 45.0,
        longitude = 9.0,
        fetchedAtEpochSec = 1_716_000_000L,
        current = WeatherPoint(temperatureC = 20.2, description = "clear sky", iconId = "01d"),
        plus1h = WeatherPoint(temperatureC = 19.8, description = "few clouds", iconId = "02d"),
        plus2h = WeatherPoint(temperatureC = 19.1, description = "cloudy", iconId = "03d"),
    )
}

private class FakeWeatherRepository : WeatherRepository(
    weatherApiService = object : WeatherApiService {
        override suspend fun getWeather(latitude: Double, longitude: Double): WeatherResponseDto {
            error("Not used in tests")
        }
    },
    weatherDao = object : WeatherDao {
        override suspend fun upsert(weather: WeatherEntity) = Unit
        override suspend fun getCached(cacheId: Int): WeatherEntity? = null
        override fun observeCached(cacheId: Int): Flow<WeatherEntity?> = MutableStateFlow(null)
    },
) {
    val cachedWeatherFlow = MutableStateFlow<WeatherSnapshot?>(null)
    var cachedWeather: WeatherSnapshot? = null
    var nextRefreshResult: Result<WeatherSnapshot> = Result.success(
        WeatherSnapshot(
            latitude = 0.0,
            longitude = 0.0,
            fetchedAtEpochSec = 0L,
            current = WeatherPoint(0.0, "", ""),
            plus1h = WeatherPoint(0.0, "", ""),
            plus2h = WeatherPoint(0.0, "", ""),
        ),
    )

    override fun observeCachedWeather(): Flow<WeatherSnapshot?> = cachedWeatherFlow

    override suspend fun getCachedWeather(): WeatherSnapshot? = cachedWeather

    override suspend fun refreshWeather(latitude: Double, longitude: Double): Result<WeatherSnapshot> =
        nextRefreshResult
}

