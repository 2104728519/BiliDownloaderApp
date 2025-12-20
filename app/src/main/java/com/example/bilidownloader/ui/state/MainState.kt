package com.example.bilidownloader.ui.state

import com.example.bilidownloader.data.model.ConclusionData
import com.example.bilidownloader.data.model.VideoDetail

data class FormatOption(
    val id: Int,
    val label: String,
    val description: String,
    val codecs: String?,
    val bandwidth: Long,
    val estimatedSize: Long
)

sealed class MainState {
    object Idle : MainState()
    object Analyzing : MainState()

    data class ChoiceSelect(
        val detail: VideoDetail,
        val videoFormats: List<FormatOption>,
        val audioFormats: List<FormatOption>,
        val selectedVideo: FormatOption?,
        val selectedAudio: FormatOption?,

        // --- 【新增】字幕相关状态 ---
        val isSubtitleLoading: Boolean = false, // 是否正在请求字幕
        val subtitleData: ConclusionData? = null, // 字幕原始数据
        val selectedSubtitleIndex: Int = 0, // 当前选中的字幕索引 (因为可能有多个版本的字幕)
        val isTimestampEnabled: Boolean = false, // 是否开启时间戳显示
        val subtitleContent: String = "" // 当前预览/编辑框中的文本内容
    ) : MainState()

    data class Processing(val info: String, val progress: Float) : MainState()
    data class Success(val message: String) : MainState()
    data class Error(val errorMsg: String) : MainState()
}