package com.example.bilidownloader.domain.usecase

import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.data.repository.CommentRepository

/**
 * 业务逻辑：发送评论
 * 非常简单，主要是为了架构的分层统一
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