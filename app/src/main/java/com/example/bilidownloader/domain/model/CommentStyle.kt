package com.example.bilidownloader.domain.model

/**
 * 评论风格模型 (不再是 Enum)
 * 兼容官方预置和用户自定义
 */
data class CommentStyle(
    val id: Long = 0,           // 数据库ID (预置风格为负数或0)
    val label: String,          // 显示在 Chip 上的文字
    val promptInstruction: String, // 发送给 AI 的指令
    val isBuiltIn: Boolean = false // 是否为内置风格 (内置不可删除)
) {
    companion object {
        // 定义官方预置风格列表
        val BUILT_IN_STYLES = listOf(
            CommentStyle(-1, "幽默玩梗", "请用幽默风趣、带点B站热梗（如：好活当赏、下次一定）的语气进行评论，字数在50字以内。", true),
            CommentStyle(-2, "课代表总结", "请作为'课代表'，用简洁的列表形式总结视频的核心知识点，语气专业且干练。", true),
            CommentStyle(-3, "犀利吐槽", "请用稍微犀利一点、一针见血的语气指出视频的一个槽点或亮点，但不要进行人身攻击。", true),
            CommentStyle(-4, "粉丝夸夸", "请用粉丝的口吻，疯狂夸赞UP主的制作水平或视频内容，多用感叹号和颜文字。", true),
            CommentStyle(-5, "震惊震惊", "请表现出非常震惊、不可思议的语气，仿佛看到了什么不得了的东西。", true),
            CommentStyle(-6, "万能回复", "请用一句通用的、礼貌的、符合B站弹幕氛围的话进行评论，不要太长。", true)
        )
    }
}