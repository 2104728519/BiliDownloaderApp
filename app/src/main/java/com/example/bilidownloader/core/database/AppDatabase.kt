package com.example.bilidownloader.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room 数据库核心入口.
 *
 * 定义了数据库的表结构 (Entities) 和版本号。
 * 包含：
 * 1. 历史记录 (History)
 * 2. 用户凭证 (User)
 * 3. FFmpeg 指令预设 (FfmpegPreset)
 */
@Database(
    entities = [
        HistoryEntity::class,
        UserEntity::class,
        FfmpegPresetEntity::class
    ],
    version = 6, // 升级版本号以应用结构变更
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao
    abstract fun userDao(): UserDao

    /**
     * 获取 FFmpeg 预设指令的 DAO
     */
    abstract fun ffmpegPresetDao(): FfmpegPresetDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bili_downloader_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}