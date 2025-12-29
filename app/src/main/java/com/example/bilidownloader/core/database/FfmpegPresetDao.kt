// 文件: core/database/FfmpegPresetDao.kt
package com.example.bilidownloader.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FfmpegPresetDao {
    // 按时间倒序排列，最新的在最前
    @Query("SELECT * FROM ffmpeg_presets ORDER BY timestamp DESC")
    fun getAllPresets(): Flow<List<FfmpegPresetEntity>>

    @Insert
    suspend fun insertPreset(preset: FfmpegPresetEntity)

    @Delete
    suspend fun deletePreset(preset: FfmpegPresetEntity)
}