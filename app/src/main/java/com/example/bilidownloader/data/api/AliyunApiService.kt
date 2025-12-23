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

/**
 * 阿里云百炼/听悟 API 接口定义.
 * 负责音频转写服务的异步任务提交、轮询和结果获取.
 */
interface AliyunApiService {

    /**
     * 提交转写任务 (异步).
     * @param apiKey 需要添加 "Bearer " 前缀.
     */
    @POST("api/v1/services/audio/asr/transcription")
    suspend fun submitTranscription(
        @Header("Authorization") apiKey: String,
        @Header("X-DashScope-Async") async: String = "enable",
        @Body request: TranscriptionRequest
    ): TranscriptionResponse

    /**
     * 查询任务处理状态.
     */
    @GET("api/v1/tasks/{taskId}")
    suspend fun getTaskStatus(
        @Header("Authorization") apiKey: String,
        @Path("taskId") taskId: String
    ): TranscriptionResponse

    /**
     * 下载最终的转写结果 JSON.
     * 使用 @Url 直接访问任务结果返回的临时下载链接.
     */
    @GET
    suspend fun downloadTranscript(
        @Url url: String
    ): TranscriptionResultData
}