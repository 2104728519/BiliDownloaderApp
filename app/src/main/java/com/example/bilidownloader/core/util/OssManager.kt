package com.example.bilidownloader.core.util

import android.content.Context
import com.alibaba.sdk.android.oss.ClientConfiguration
import com.alibaba.sdk.android.oss.OSSClient
import com.alibaba.sdk.android.oss.common.auth.OSSPlainTextAKSKCredentialProvider
import com.alibaba.sdk.android.oss.model.PutObjectRequest
import com.example.bilidownloader.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 阿里云 OSS (对象存储) 管理器.
 *
 * 负责将本地音频文件临时上传至 OSS，以供阿里云听悟 (ASR) 服务进行转写。
 * 包含上传、生成签名 URL 和删除文件功能。
 */
object OssManager {

    // OSS 存储桶配置
    private const val ENDPOINT = "https://oss-cn-beijing.aliyuncs.com"
    private const val BUCKET_NAME = "2104728519"

    private var ossClient: OSSClient? = null

    /**
     * 初始化 OSS 客户端 (单例模式).
     * 从 BuildConfig 读取 AK/SK，确保密钥不硬编码在源码中。
     */
    private fun getClient(context: Context): OSSClient {
        if (ossClient == null) {
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
     * 上传文件并获取带签名的访问 URL.
     *
     * @return 有效期 1 小时的签名 URL，供 ASR 服务下载使用.
     */
    suspend fun uploadAndGetUrl(context: Context, file: File): String = withContext(Dispatchers.IO) {
        val client = getClient(context)
        val objectKey = "audio-uploads/${file.name}" // 存储路径

        // 1. 同步上传
        val put = PutObjectRequest(BUCKET_NAME, objectKey, file.absolutePath)

        suspendCancellableCoroutine<Unit> { continuation ->
            try {
                client.putObject(put)
                continuation.resume(Unit)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }

        // 2. 生成签名 URL (过期时间 3600秒)
        val url = client.presignConstrainedObjectURL(BUCKET_NAME, objectKey, 3600)
        return@withContext url
    }

    /**
     * 删除 OSS 上的临时文件.
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