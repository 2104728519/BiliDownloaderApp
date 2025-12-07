package com.example.bilidownloader.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 档案室大楼
 * 负责把表格(Entity)和办事员(Dao)管理起来
 */
@Database(entities = [HistoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    // 招聘办事员
    abstract fun historyDao(): HistoryDao

    // 单例模式：确保整个 APP 只有一个档案室，不然会有冲突
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bili_downloader_db" // 数据库文件的名字
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}