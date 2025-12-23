package com.example.bilidownloader.domain.usecase

import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.data.repository.CommentRepository

/**
 * 评论发送用例.
 *
 * 简单的业务封装，主要负责前置校验（如空内容检查、字数限制）。
 */
class PostCommentUseCase(private val commentRepository: CommentRepository) {

    suspend operator fun invoke(oid: Long, message: String): Resource<String> {
        if (message.isBlank()) {
            return Resource.Error("评论内容不能为空")
        }
        if (message.length > 1000) {
            return Resource.Error("评论字数超出限制 (最大1000字)")
        }
        return commentRepository.postComment(oid, message)
    }
}