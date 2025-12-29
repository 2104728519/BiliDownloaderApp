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
 * 3. 已处理视频记录 (CommentedVideo)
 * 4. 自定义评论风格 (CustomStyle)
 * 5. FFmpeg 指令预设 (FfmpegPreset) - [新增]
 */
@Database(
    entities = [
        HistoryEntity::class,
        UserEntity::class,
        CommentedVideoEntity::class,
        CustomStyleEntity::class,
        FfmpegPresetEntity::class // [新增] 注册 FFmpeg 预设表
    ],
    version = 5, // [修改] 版本号从 4 升级至 5，触发 destructive migration
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao
    abstract fun userDao(): UserDao
    abstract fun commentedVideoDao(): CommentedVideoDao
    abstract fun customStyleDao(): CustomStyleDao

    /**
     * [新增] 获取 FFmpeg 预设指令的 DAO
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
                    // 开发阶段策略：版本升级时若未提供 Migration，则直接清空旧数据重建表
                    // 警告：这会导致 version 4 中的所有本地数据丢失
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}