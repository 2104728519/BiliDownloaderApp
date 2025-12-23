package com.example.bilidownloader.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 自定义评论风格实体.
 * @property prompt 发送给 LLM 的具体指令内容.
 */
@Entity(tableName = "custom_styles")
data class CustomStyleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val label: String,
    val prompt: String
)