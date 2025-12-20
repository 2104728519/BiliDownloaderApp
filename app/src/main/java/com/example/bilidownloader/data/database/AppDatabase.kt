package com.example.bilidownloader.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// [修改 1] version 升级为 3，entities 增加 CommentedVideoEntity
@Database(
    entities = [HistoryEntity::class, UserEntity::class, CommentedVideoEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao
    abstract fun userDao(): UserDao

    // [修改 2] 新增 DAO
    abstract fun commentedVideoDao(): CommentedVideoDao

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
                    // 依然使用破坏性迁移，开发阶段省事
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}