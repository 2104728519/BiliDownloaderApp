package com.example.bilidownloader.data.api

import com.example.bilidownloader.data.model.TranscriptionRequest
import com.example.bilidownloader.data.model.TranscriptionResponse
import com.example.bilidownloader.data.model.TranscriptionResultData
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Url

interface AliyunApiService {

    // 1. 提交转写任务
    @POST("api/v1/services/audio/asr/transcription")
    suspend fun submitTranscription(
        @Header("Authorization") apiKey: String,
        @Header("X-DashScope-Async") async: String = "enable",
        @Body request: TranscriptionRequest
    ): TranscriptionResponse

    // 2. 查询任务状态
    @GET("api/v1/tasks/{taskId}")
    suspend fun getTaskStatus(
        @Header("Authorization") apiKey: String,
        @Path("taskId") taskId: String
    ): TranscriptionResponse

    // 3. 下载转写结果 (直接访问 URL)
    @GET
    suspend fun downloadTranscript(
        @Url url: String
    ): TranscriptionResultData
}