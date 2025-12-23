package com.example.bilidownloader.ui.viewmodel

/**
 * AI 评论助手加载状态枚举.
 *
 * 用于细粒度控制 UI 的加载反馈。相比简单的 Boolean (isLoading)，
 * 枚举能让 UI 明确当前是正在解析视频、获取字幕还是生成评论，从而展示不同的提示文案或进度动画。
 */
enum class AiCommentLoadingState {
    Idle,                    // 空闲 / 就绪
    AnalyzingVideo,          // 正在解析视频链接
    FetchingSubtitle,        // 正在获取 AI 摘要或字幕
    GeneratingComment,       // 正在调用 LLM 生成评论
    SendingComment,          // 正在向 B 站发送评论
    FetchingRecommendations, // 正在刷新首页推荐流
    AutoRunning              // 自动化任务正在后台循环运行
}