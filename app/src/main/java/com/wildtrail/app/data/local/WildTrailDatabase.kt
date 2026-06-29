package com.wildtrail.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.wildtrail.app.data.local.converter.Converters
import com.wildtrail.app.data.local.dao.AchievementDao
import com.wildtrail.app.data.local.dao.EmergencyContactDao
import com.wildtrail.app.data.local.dao.FollowedTrailDao
import com.wildtrail.app.data.local.dao.HikeCommentDao
import com.wildtrail.app.data.local.dao.HikeLogDao
import com.wildtrail.app.data.local.dao.LikeDao
import com.wildtrail.app.data.local.dao.TrailReviewDao
import com.wildtrail.app.data.local.dao.UserDao
import com.wildtrail.app.data.local.dao.UserFollowDao
import com.wildtrail.app.data.local.dao.WeatherDao
import com.wildtrail.app.data.local.entity.AchievementDefinitionEntity
import com.wildtrail.app.data.local.entity.EmergencyContactEntity
import com.wildtrail.app.data.local.entity.FollowedTrailEntity
import com.wildtrail.app.data.local.entity.HikeCommentEntity
import com.wildtrail.app.data.local.entity.HikeLogEntity
import com.wildtrail.app.data.local.entity.LikeEntity
import com.wildtrail.app.data.local.entity.TrailReviewEntity
import com.wildtrail.app.data.local.entity.UserAchievementEntity
import com.wildtrail.app.data.local.entity.UserEntity
import com.wildtrail.app.data.local.entity.UserFollowEntity
import com.wildtrail.app.data.local.entity.WeatherEntity

@Database(
    version = WildTrailDatabase.VERSION,
    exportSchema = true,
    entities = [
        UserEntity::class,
        HikeLogEntity::class,
        TrailReviewEntity::class,
        UserFollowEntity::class,
        FollowedTrailEntity::class,
        HikeCommentEntity::class,
        AchievementDefinitionEntity::class,
        UserAchievementEntity::class,
        EmergencyContactEntity::class,
        LikeEntity::class,
        WeatherEntity::class,
    ],
)
@TypeConverters(Converters::class)
abstract class WildTrailDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun hikeLogDao(): HikeLogDao
    abstract fun trailReviewDao(): TrailReviewDao
    abstract fun userFollowDao(): UserFollowDao
    abstract fun followedTrailDao(): FollowedTrailDao
    abstract fun hikeCommentDao(): HikeCommentDao
    abstract fun achievementDao(): AchievementDao
    abstract fun emergencyContactDao(): EmergencyContactDao
    abstract fun likeDao(): LikeDao
    abstract fun weatherDao(): WeatherDao

    companion object {
        const val VERSION = 7
        private const val DB_NAME = "wildtrail.db"

        @Volatile
        private var instance: WildTrailDatabase? = null

        fun getInstance(context: Context): WildTrailDatabase = instance ?: synchronized(this) {
            instance ?: build(context).also { instance = it }
        }

        private fun build(context: Context): WildTrailDatabase = Room.databaseBuilder(
            context.applicationContext,
            WildTrailDatabase::class.java,
            DB_NAME,
        )
            .fallbackToDestructiveMigration()
            .build()
    }
}
