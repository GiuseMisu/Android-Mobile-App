package com.wildtrail.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wildtrail.app.data.local.entity.WeatherEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(weather: WeatherEntity)

    @Query("SELECT * FROM weather_cache WHERE cacheId = :cacheId LIMIT 1")
    suspend fun getCached(cacheId: Int = WeatherEntity.CACHE_ID): WeatherEntity?

    @Query("SELECT * FROM weather_cache WHERE cacheId = :cacheId LIMIT 1")
    fun observeCached(cacheId: Int = WeatherEntity.CACHE_ID): Flow<WeatherEntity?>
}

