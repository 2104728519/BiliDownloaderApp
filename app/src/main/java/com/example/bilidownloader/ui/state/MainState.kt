package com.example.bilidownloader.ui.state

import com.example.bilidownloader.data.model.VideoDetail

// 新增：用于 UI 显示的格式选项
data class FormatOption(
    val id: Int,          // B站的 qn (如 80, 64)
    val label: String,    // 显示文字 "1080P (AVC) - 150MB"
    val description: String, // 原始描述 "1080P 高清"
    val codecs: String?,  // 编码
    val bandwidth: Long,  // 码率
    val estimatedSize: Long // 预估大小 (字节)
)


/**
 * 这里定义了 APP 所有的“生存状态”
 * 界面 (UI) 会根据这个状态自动变化
 */
sealed class MainState {

    // 1. 空闲状态：刚打开 APP，或者任务结束了，等待用户输入
    object Idle : MainState()

    // 2. 解析中：用户点击了按钮，正在去 B 站查询视频信息 (转圈圈)
    object Analyzing : MainState()

    // 3. 【修改】选择状态：解析成功了，现在我们拿着详细信息和可用格式列表给 UI 展示
    data class ChoiceSelect(
        val detail: VideoDetail,
        val videoFormats: List<FormatOption>, // 可选视频画质
        val audioFormats: List<FormatOption>,  // 可选音频音质
        // 👇 新增字段：用于记录用户当前选中的视频和音频格式
        val selectedVideo: FormatOption?,
        val selectedAudio: FormatOption?
    ) : MainState()

    // 4. 下载/处理中：用户做出了选择，正在干活
    // info: 告诉用户当前在干嘛 (比如 "正在下载视频...", "正在合并...", "正在保存...")
    // progress: 进度条 (0.0 ~ 1.0)
    data class Processing(val info: String, val progress: Float) : MainState()

    // 5. 成功：全部搞定
    data class Success(val message: String) : MainState()

    // 6. 失败：出错了
    data class Error(val errorMsg: String) : MainState()
}