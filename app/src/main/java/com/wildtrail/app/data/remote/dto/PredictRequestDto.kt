package com.wildtrail.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class PredictRequestDto(
    val user: UserFeaturesDto,
    val hike: HikeFeaturesDto,
)

data class UserFeaturesDto(
    @SerializedName("xp_points")  val xpPoints: Int,
    @SerializedName("eta")        val eta: Int,
    @SerializedName("past_hikes") val pastHikes: Int,
    @SerializedName("avg_speed")  val avgSpeed: Double,
)

data class HikeFeaturesDto(
    @SerializedName("lunghezza")       val lunghezza: Double,
    @SerializedName("elevation_gain")  val elevationGain: Int,
    @SerializedName("surface_type")    val surfaceType: String,
    @SerializedName("difficulty")      val difficulty: Int,
)
