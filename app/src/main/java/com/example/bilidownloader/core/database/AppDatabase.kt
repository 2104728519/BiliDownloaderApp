package com.example.bilidownloader.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room 数据库核心入口.
 *
 * 定义了数据库的表结构 (Entities) 和版本号。
 * 包含历史记录、用户凭证、已处理视频记录以及自定义评论风格表.
 */
@Database(
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
                    // 开发阶段策略：版本升级时若未提供 Migration，则直接清空旧数据重建表
                    // 生产环境应补充 addMigrations() 以保护用户数据
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}