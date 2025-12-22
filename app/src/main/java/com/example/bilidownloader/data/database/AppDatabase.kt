package com.example.bilidownloader.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 数据库定义
 * 版本 3 -> 4: 增加了 CustomStyleEntity 用于存储用户自定义的 AI 评论风格
 */
@Database(
    // [修改 1] entities 增加 CustomStyleEntity，version 升级为 4
    entities = [
        HistoryEntity::class,
        UserEntity::class,
        CommentedVideoEntity::class,
        CustomStyleEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao
    abstract fun userDao(): UserDao
    abstract fun commentedVideoDao(): CommentedVideoDao

    // [修改 2] 注册自定义风格 DAO
    abstract fun customStyleDao(): CustomStyleDao

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
                    // 升级策略：检测到版本不匹配时，销毁并重建所有表
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}