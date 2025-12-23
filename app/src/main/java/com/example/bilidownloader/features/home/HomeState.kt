package com.example.bilidownloader.features.home

import com.example.bilidownloader.core.model.ConclusionData
import com.example.bilidownloader.core.model.VideoDetail

/**
 * 视频流格式选项模型.
 * 用于 UI 展示供用户选择 (如 "1080P 高清 - 50MB")
 */
data class FormatOption(
    val id: Int,
    val label: String,
    val description: String,
    val codecs: String?,
    val bandwidth: Long,
    val estimatedSize: Long
)

/**
 * 首页 UI 状态密封类.
 * 替代原有的 MainState.
 */
sealed class HomeState {
    // 1. 空闲状态 (显示输入框和历史记录)
    object Idle : HomeState()

    // 2. 解析中 (显示转圈)
    object Analyzing : HomeState()

    // 3. 选择模式 (显示视频详情、格式选择器、下载按钮、字幕按钮)
    data class ChoiceSelect(
        val detail: VideoDetail,
        val videoFormats: List<FormatOption>,
        val audioFormats: List<FormatOption>,
        val selectedVideo: FormatOption?,
        val selectedAudio: FormatOption?,

        // --- 字幕与 AI 摘要相关状态 ---
        val isSubtitleLoading: Boolean = false, // 是否正在请求字幕
        val subtitleData: ConclusionData? = null, // 字幕原始数据
        val selectedSubtitleIndex: Int = 0, // 当前选中的字幕索引
        val isTimestampEnabled: Boolean = false, // 是否开启时间戳显示
        val subtitleContent: String = "" // 当前预览/编辑框中的文本内容
    ) : HomeState()

    // 4. 处理中 (下载、合并、转码进度)
    data class Processing(val info: String, val progress: Float) : HomeState()

    // 5. 成功提示
    data class Success(val message: String) : HomeState()

    // 6. 错误提示
    data class Error(val errorMsg: String) : HomeState()
}