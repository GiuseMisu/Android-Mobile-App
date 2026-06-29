package com.wildtrail.app.data.local.converter

import androidx.room.TypeConverter
import com.wildtrail.app.domain.model.AchievementCategory
import com.wildtrail.app.domain.model.GeoPoint
import com.wildtrail.app.domain.model.HikeMediaItem
import com.wildtrail.app.domain.model.Sex
import com.wildtrail.app.domain.model.SurfaceType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

object Converters {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    @JvmStatic
    fun geoPointListToJson(value: List<GeoPoint>): String =
        json.encodeToString(ListSerializer(GeoPoint.serializer()), value)

    @TypeConverter
    @JvmStatic
    fun jsonToGeoPointList(value: String): List<GeoPoint> =
        json.decodeFromString(ListSerializer(GeoPoint.serializer()), value)

    @TypeConverter
    @JvmStatic
    fun mediaItemListToJson(value: List<HikeMediaItem>): String =
        json.encodeToString(ListSerializer(HikeMediaItem.serializer()), value)

    @TypeConverter
    @JvmStatic
    fun jsonToMediaItemList(value: String): List<HikeMediaItem> =
        runCatching {
            json.decodeFromString(ListSerializer(HikeMediaItem.serializer()), value)
        }.getOrDefault(emptyList())

    @TypeConverter
    @JvmStatic
    fun stringListToJson(value: List<String>): String =
        json.encodeToString(ListSerializer(String.serializer()), value)

    @TypeConverter
    @JvmStatic
    fun jsonToStringList(value: String): List<String> =
        json.decodeFromString(ListSerializer(String.serializer()), value)

    // enums stored by name so reordering the constants doesn't require a migration
    @TypeConverter
    @JvmStatic
    fun surfaceTypeToString(value: SurfaceType): String = value.name

    @TypeConverter
    @JvmStatic
    fun stringToSurfaceType(value: String): SurfaceType =
        runCatching { SurfaceType.valueOf(value) }.getOrDefault(SurfaceType.OTHER)

    @TypeConverter
    @JvmStatic
    fun achievementCategoryToString(value: AchievementCategory): String = value.name

    @TypeConverter
    @JvmStatic
    fun stringToAchievementCategory(value: String): AchievementCategory =
        runCatching { AchievementCategory.valueOf(value) }.getOrDefault(AchievementCategory.OTHER)

    @TypeConverter
    @JvmStatic
    fun sexToString(value: Sex?): String? = value?.name

    @TypeConverter
    @JvmStatic
    fun stringToSex(value: String?): Sex? = value?.let {
        runCatching { Sex.valueOf(it) }.getOrNull()
    }
}
