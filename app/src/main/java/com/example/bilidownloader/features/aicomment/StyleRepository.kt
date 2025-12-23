package com.example.bilidownloader.features.aicomment

import com.example.bilidownloader.core.database.CustomStyleDao
import com.example.bilidownloader.core.database.CustomStyleEntity
import com.example.bilidownloader.domain.model.CommentStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 评论风格仓库.
 *
 * 负责合并【内置硬编码风格】与【数据库用户自定义风格】。
 * 对外暴露统一的 Flow 数据流，UI 层无需关心风格的来源。
 */
class StyleRepository(private val dao: CustomStyleDao) {

    /**
     * 实时合并风格流.
     * 每次数据库更新时，重新组合内置列表和自定义列表。
     */
    val allStyles: Flow<List<CommentStyle>> = dao.getAllStyles().map { entities ->
        val list = CommentStyle.BUILT_IN_STYLES.toMutableList()

        val customList = entities.map { entity ->
            CommentStyle(
                id = entity.id,
                label = entity.label,
                promptInstruction = entity.prompt,
                isBuiltIn = false
            )
        }
        list.addAll(customList)
        list
    }

    suspend fun addStyle(label: String, prompt: String) {
        dao.insert(CustomStyleEntity(label = label, prompt = prompt))
    }

    suspend fun deleteStyle(style: CommentStyle) {
        if (!style.isBuiltIn) {
            dao.delete(CustomStyleEntity(id = style.id, label = style.label, prompt = style.promptInstruction))
        }
    }
}