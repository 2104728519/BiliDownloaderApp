package com.example.bilidownloader.ui.viewmodel

/**
 * AI 评论页面的精细化加载状态
 * 用于区分当前正在执行哪个异步任务
 */
enum class AiCommentLoadingState {
    Idle,                // 空闲状态
    AnalyzingVideo,      // 正在解析视频
    FetchingSubtitle,    // 正在获取字幕
    GeneratingComment,   // 正在生成评论
    SendingComment,       // 正在发送评论
    FetchingRecommendations, // 正在获取推荐流
    AutoRunning       //  自动化运行中
}