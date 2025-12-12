package com.example.bilidownloader.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 档案室大楼
 * 负责把表格(Entity)和办事员(Dao)管理起来
 */
@Database(entities = [HistoryEntity::class, UserEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    // 招聘办事员：负责历史记录
    abstract fun historyDao(): HistoryDao

    // 【新增】招聘办事员：负责用户账号管理
    abstract fun userDao(): UserDao

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
                )
                    // 【新增】允许破坏性迁移
                    // 当版本号从 1 升到 2 时，如果找不到迁移规则，直接清空旧数据重新建表
                    // 防止 APP 崩溃
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}