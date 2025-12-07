package com.example.bilidownloader.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.example.bilidownloader.data.model.AudioEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaRepository(private val context: Context) {

    /**
     * 开始搜查！
     * @return 找到的所有音频文件列表
     */
    suspend fun getAllAudio(): List<AudioEntity> = withContext(Dispatchers.IO) {
        val audioList = mutableListOf<AudioEntity>()

        // 1. 我们要查哪张表？ -> 外部存储里的音频表
        val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        // 2. 我们要看哪些列的数据？(ID, 标题, 时长, 大小, 时间)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED
        )

        // 3. 筛选条件：我们要时长大于 0 的 (防止搜到 0秒的损坏文件)
        val selection = "${MediaStore.Audio.Media.DURATION} > ?"
        val selectionArgs = arrayOf("0")

        // 4. 排序：按添加时间倒序 (最新的在最上面)
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        // 5. 开始查询 (query)
        // contentResolver 是系统的数据库管理员
        context.contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor -> // use 方法会自动关闭 cursor，防止内存泄漏

            // 找到每一列对应的索引 (第几列是 ID，第几列是标题...)
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

            // 6. 一行一行读数据
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "未知音频"
                val duration = cursor.getLong(durationColumn)
                val size = cursor.getLong(sizeColumn)
                val date = cursor.getLong(dateColumn)

                // 拼装 URI (货架号)
                val contentUri = ContentUris.withAppendedId(collection, id)

                // 填卡片
                audioList.add(
                    AudioEntity(
                        id = id,
                        uri = contentUri,
                        title = name,
                        duration = duration,
                        size = size,
                        dateAdded = date
                    )
                )
            }
        }

        // 返回结果
        return@withContext audioList
    }
}