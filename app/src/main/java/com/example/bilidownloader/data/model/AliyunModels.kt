package com.example.bilidownloader.data.model

// 1. 提交任务请求体
data class TranscriptionRequest(
    val model: String = "paraformer-v2",
    val input: TranscriptionInput,
    val parameters: TranscriptionParameters = TranscriptionParameters()
)

data class TranscriptionInput(
    val file_urls: List<String>
)

data class TranscriptionParameters(
    val timestamp_alignment_enabled: Boolean = true,
    val language_hints: List<String> = listOf("zh")
)

// 2. 提交任务响应 / 查询任务响应
data class TranscriptionResponse(
    val output: TranscriptionOutput?,
    val status_code: Int?,
    val code: String?,
    val message: String?
)

data class TranscriptionOutput(
    val task_id: String?,
    val task_status: String?, // "PENDING", "RUNNING", "SUCCEEDED", "FAILED"
    val results: List<TranscriptionResult>?,

    // 【新增】加上这两个字段，用来接收错误信息
    val code: String?,
    val message: String?
)

data class TranscriptionResult(
    val transcription_url: String? // 转写成功后，结果在这个 URL 里
)

// 3. 最终转写结果 JSON (从 transcription_url 下载的)
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