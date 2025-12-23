package com.example.bilidownloader.data.model

// region 1. Transcription Request (转写请求)

data class TranscriptionRequest(
    val model: String = "paraformer-v2",
    val input: TranscriptionInput,
    val parameters: TranscriptionParameters = TranscriptionParameters()
)

data class TranscriptionInput(
    val file_urls: List<String>
)

data class TranscriptionParameters(
    val timestamp_alignment_enabled: Boolean = true, // 开启时间戳对齐
    val language_hints: List<String> = listOf("zh")
)

// endregion

// region 2. Transcription Response (转写响应)

data class TranscriptionResponse(
    val output: TranscriptionOutput?,
    val status_code: Int?,
    val code: String?,
    val message: String?
)

data class TranscriptionOutput(
    val task_id: String?,
    val task_status: String?, // 状态枚举: PENDING, RUNNING, SUCCEEDED, FAILED
    val results: List<TranscriptionResult>?,
    val code: String?,
    val message: String?
)

data class TranscriptionResult(
    val transcription_url: String? // 结果文件的临时下载链接
)

// endregion

// region 3. Result Content (结果文件内容)

data class TranscriptionResultData(
    val transcripts: List<TranscriptItem>?
)

data class TranscriptItem(
    val text: String,
    val sentences: List<SentenceItem>?
)

data class SentenceItem(
    val text: String,
    val begin_time: Long,
    val end_time: Long
)

// endregion