package com.example.bilidownloader.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 已处理视频记录 DAO.
 * 用于自动化任务中的去重判断，避免对同一个视频重复生成评论.
 */
@Dao
interface CommentedVideoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CommentedVideoEntity)

    /**
     * 检查视频是否已处理.
     * 利用 SQLite 的 EXISTS 语法提高查询效率.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM commented_videos WHERE bvid = :bvid)")
    suspend fun isProcessed(bvid: String): Boolean

    @Query("SELECT * FROM commented_videos ORDER BY timestamp DESC")
    suspend fun getAll(): List<CommentedVideoEntity>
}