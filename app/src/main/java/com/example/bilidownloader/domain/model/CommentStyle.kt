package com.example.bilidownloader.domain.model

/**
 * AI 评论风格枚举
 * 定义了显示在 UI 上的标签，以及发送给 LLM 的提示词指令
 */
enum class CommentStyle(val label: String, val promptInstruction: String) {
    GENERIC("万能回复", "请用一句通用的、礼貌的、符合B站弹幕氛围的话进行评论，不要太长。"),
    HUMOROUS("幽默玩梗", "请用幽默风趣、带点B站热梗（如：好活当赏、下次一定）的语气进行评论，字数在50字以内。"),
    PROFESSIONAL("课代表总结", "请作为'课代表'，用简洁的列表形式总结视频的核心知识点，语气专业且干练。"),
    CRITICAL("犀利吐槽", "请用稍微犀利一点、一针见血的语气指出视频的一个槽点或亮点，但不要进行人身攻击。"),
    FANBOY("粉丝夸夸", "请用粉丝的口吻，疯狂夸赞UP主的制作水平或视频内容，多用感叹号和颜文字。"),
    SHOCKED("震惊震惊", "请表现出非常震惊、不可思议的语气，仿佛看到了什么不得了的东西。");
}