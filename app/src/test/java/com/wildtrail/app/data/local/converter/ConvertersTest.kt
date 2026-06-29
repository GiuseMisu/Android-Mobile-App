package com.wildtrail.app.data.local.converter

import com.wildtrail.app.domain.model.AchievementCategory
import com.wildtrail.app.domain.model.GeoPoint
import com.wildtrail.app.domain.model.SurfaceType
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    @Test
    fun `geoPoint list round-trips through JSON`() {
        val original = listOf(
            GeoPoint(lat = 45.0, lng = 9.0, altitudeM = 100.0, timestamp = 12_345L),
            GeoPoint(lat = 46.0, lng = 10.0, altitudeM = null, timestamp = 67_890L),
        )

        val json = Converters.geoPointListToJson(original)
        val decoded = Converters.jsonToGeoPointList(json)

        assertEquals(original, decoded)
    }

    @Test
    fun `string list round-trips through JSON`() {
        val original = listOf("https://example.com/a.jpg", "https://example.com/b.jpg")
        val decoded = Converters.jsonToStringList(Converters.stringListToJson(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `surface type round-trips`() {
        SurfaceType.values().forEach { value ->
            val s = Converters.surfaceTypeToString(value)
            assertEquals(value, Converters.stringToSurfaceType(s))
        }
    }

    @Test
    fun `unknown surface type string is mapped to OTHER`() {
        assertEquals(SurfaceType.OTHER, Converters.stringToSurfaceType("not-a-real-value"))
    }

    @Test
    fun `achievement category round-trips`() {
        AchievementCategory.values().forEach { value ->
            val s = Converters.achievementCategoryToString(value)
            assertEquals(value, Converters.stringToAchievementCategory(s))
        }
    }
}
