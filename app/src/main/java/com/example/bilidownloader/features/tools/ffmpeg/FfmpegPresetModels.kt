// 文件: features/ffmpeg/FfmpegPresetModels.kt
package com.example.bilidownloader.features.ffmpeg

import com.google.gson.annotations.SerializedName

/**
 * 用于导入/导出的纯数据模型 (JSON 结构)
 */
data class PresetExportModel(
    @SerializedName("name") val name: String,
    @SerializedName("args") val commandArgs: String,
    @SerializedName("ext") val outputExtension: String
)