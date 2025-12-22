package com.example.bilidownloader.core.util

import android.content.Context
import com.alibaba.sdk.android.oss.ClientConfiguration
import com.alibaba.sdk.android.oss.OSSClient
import com.alibaba.sdk.android.oss.common.auth.OSSPlainTextAKSKCredentialProvider
import com.alibaba.sdk.android.oss.model.PutObjectRequest
import com.example.bilidownloader.BuildConfig // 导入 BuildConfig 类
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OssManager {



    private const val ENDPOINT = "https://oss-cn-beijing.aliyuncs.com" // 你的 Endpoint
    private const val BUCKET_NAME = "2104728519" // 你的 Bucket 名字

    private var ossClient: OSSClient? = null

    private fun getClient(context: Context): OSSClient {
        if (ossClient == null) {
            // 【关键改动 3】从 BuildConfig 中安全地获取密钥
            val credentialProvider = OSSPlainTextAKSKCredentialProvider(
                BuildConfig.OSS_KEY_ID,
                BuildConfig.OSS_KEY_SECRET
            )
            val conf = ClientConfiguration()
            conf.connectionTimeout = 15 * 1000
            conf.socketTimeout = 15 * 1000
            conf.maxConcurrentRequest = 5
            ossClient = OSSClient(context.applicationContext, ENDPOINT, credentialProvider, conf)
        }
        return ossClient!!
    }

    /**
     * 上传文件并获取签名 URL
     */
    suspend fun uploadAndGetUrl(context: Context, file: File): String = withContext(Dispatchers.IO) {
        val client = getClient(context)
        val objectKey = "audio-uploads/${file.name}" // OSS 上的路径

        // 1. 上传
        val put = PutObjectRequest(BUCKET_NAME, objectKey, file.absolutePath)

        suspendCancellableCoroutine<Unit> { continuation ->
            try {
                client.putObject(put) // 同步上传 (因为已经在 IO 线程了)
                continuation.resume(Unit)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }

        // 2. 生成签名 URL (有效期 1 小时，足够转写用了)
        // 这里的 url 是给百炼 API 用的
        val url = client.presignConstrainedObjectURL(BUCKET_NAME, objectKey, 3600)
        return@withContext url
    }

    /**
     * 删除 OSS 文件
     */
    suspend fun deleteFile(context: Context, fileName: String) = withContext(Dispatchers.IO) {
        try {
            val client = getClient(context)
            val objectKey = "audio-uploads/$fileName"
            client.deleteObject(com.alibaba.sdk.android.oss.model.DeleteObjectRequest(BUCKET_NAME, objectKey))
            println("OSS文件删除成功: $objectKey")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}