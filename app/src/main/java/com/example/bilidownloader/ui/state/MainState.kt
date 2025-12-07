package com.example.bilidownloader.ui.state

import com.example.bilidownloader.data.model.VideoDetail // 别忘了导入这个数据模型！

/**
 * 这里定义了 APP 所有的“生存状态”
 * 界面 (UI) 会根据这个状态自动变化
 */
sealed class MainState {

    // 1. 空闲状态：刚打开 APP，或者任务结束了，等待用户输入
    object Idle : MainState()

    // 2. 解析中：用户点击了按钮，正在去 B 站查询视频信息 (转圈圈)
    object Analyzing : MainState()

    // 3. 【修改】选择状态：解析成功了，现在我们拿着详细信息给 UI 展示
    // UI 可以根据 detail 对象显示视频标题、封面、所有分集列表等信息
    data class ChoiceSelect(val detail: VideoDetail) : MainState()

    // 4. 下载/处理中：用户做出了选择，正在干活
    // info: 告诉用户当前在干嘛 (比如 "正在下载视频...", "正在合并...", "正在保存...")
    // progress: 进度条 (0.0 ~ 1.0)
    data class Processing(val info: String, val progress: Float) : MainState()

    // 5. 成功：全部搞定
    data class Success(val message: String) : MainState()

    // 6. 失败：出错了
    data class Error(val errorMsg: String) : MainState()
}