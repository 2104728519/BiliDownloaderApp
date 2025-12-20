package com.example.bilidownloader.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CommentedVideoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: CommentedVideoEntity)

    // 检查是否已存在
    @Query("SELECT EXISTS(SELECT 1 FROM commented_videos WHERE bvid = :bvid)")
    suspend fun isProcessed(bvid: String): Boolean

    @Query("SELECT * FROM commented_videos ORDER BY timestamp DESC")
    suspend fun getAll(): List<CommentedVideoEntity>
}