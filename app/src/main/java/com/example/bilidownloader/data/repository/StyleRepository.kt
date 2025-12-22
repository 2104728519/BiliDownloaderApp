package com.example.bilidownloader.data.repository

import com.example.bilidownloader.data.database.CustomStyleDao
import com.example.bilidownloader.data.database.CustomStyleEntity
import com.example.bilidownloader.domain.model.CommentStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class StyleRepository(private val dao: CustomStyleDao) {

    // 对外暴露的流：实时合并 内置 + 自定义
    val allStyles: Flow<List<CommentStyle>> = dao.getAllStyles().map { entities ->
        // 1. 先放内置风格
        val list = CommentStyle.BUILT_IN_STYLES.toMutableList()

        // 2. 再放用户自定义风格
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