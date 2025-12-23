package com.example.bilidownloader.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.example.bilidownloader.data.model.AudioEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本地媒体仓库.
 *
 * 负责通过 ContentResolver 扫描 Android 系统的 MediaStore 数据库，
 * 提取外部存储中的所有音频文件信息。
 */
class MediaRepository(private val context: Context) {

    suspend fun getAllAudio(): List<AudioEntity> = withContext(Dispatchers.IO) {
        val audioList = mutableListOf<AudioEntity>()

        // 1. 确定查询 URI (外部存储音频表)
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        // 2. 定义需要的字段投影
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED
        )

        // 3. 过滤条件：时长 > 0 (排除损坏文件)
        val selection = "${MediaStore.Audio.Media.DURATION} > ?"
        val selectionArgs = arrayOf("0")

        // 4. 排序：按添加时间倒序
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri = ContentUris.withAppendedId(collection, id)

                audioList.add(
                    AudioEntity(
                        id = id,
                        uri = contentUri,
                        title = cursor.getString(nameColumn) ?: "未知音频",
                        duration = cursor.getLong(durationColumn),
                        size = cursor.getLong(sizeColumn),
                        dateAdded = cursor.getLong(dateColumn)
                    )
                )
            }
        }
        return@withContext audioList
    }
}