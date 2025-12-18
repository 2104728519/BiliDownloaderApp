package com.example.bilidownloader.data.repository

import com.example.bilidownloader.core.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import kotlinx.coroutines.CancellationException

/**
 * 负责文件流下载
 * 特性：支持断点续传 (Range)、网络波动自动重试、修复连接泄露、支持即时暂停/取消
 */
class DownloadRepository {

    private val client = NetworkModule.downloadClient

    fun downloadFile(url: String, file: File): Flow<Float> = flow {
        var currentLength = if (file.exists()) file.length() else 0L
        var totalLength = 0L
        var retryCount = 0
        val maxRetries = 10

        while (true) {
            // 【核心修改】在 try 块外部声明 response，以便在 finally 中访问
            var response: Response? = null

            try {
                val requestBuilder = Request.Builder()
                    .url(url)
                    .addHeader("Referer", "https://www.bilibili.com/")
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

                if (currentLength > 0) {
                    requestBuilder.addHeader("Range", "bytes=$currentLength-")
                }

                // 执行请求
                response = client.newCall(requestBuilder.build()).execute()
                val body = response.body

                if (response.code == 416) {
                    emit(1.0f)
                    return@flow
                }

                if (!response.isSuccessful || body == null) {
                    throw Exception("HTTP Error: ${response.code}")
                }

                val contentLength = body.contentLength()
                if (contentLength == 0L) {
                    emit(1.0f)
                    return@flow
                }

                if (response.code == 206) {
                    totalLength = currentLength + contentLength
                } else {
                    currentLength = 0
                    totalLength = contentLength
                    if (file.exists()) file.delete()
                    file.createNewFile()
                }

                val inputStream: InputStream = body.byteStream()

                // 使用 use 扩展函数可以自动关闭 RandomAccessFile
                RandomAccessFile(file, "rw").use { raf ->
                    raf.seek(currentLength)

                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var bytesSinceEmit = 0L

                    retryCount = 0

                    while (inputStream.read(buffer).also { bytesRead = it } >= 0) {
                        // 检查协程状态（用于暂停/取消）
                        currentCoroutineContext().ensureActive()

                        raf.write(buffer, 0, bytesRead)
                        currentLength += bytesRead
                        bytesSinceEmit += bytesRead

                        if (bytesSinceEmit > 100 * 1024) {
                            val progress = if (totalLength > 0) currentLength.toFloat() / totalLength.toFloat() else 0f
                            emit(progress)
                            bytesSinceEmit = 0
                        }
                    }
                }

                emit(1.0f)
                return@flow

            } catch (e: Exception) {
                // 如果是协程取消（如点击暂停），直接抛出，不触发重试
                if (e is CancellationException) {
                    throw e
                }

                if (retryCount >= maxRetries) {
                    throw Exception("超过最大重试次数: ${e.message}")
                }
                retryCount++

                val waitTime = 1000L * retryCount
                val progress = if (totalLength > 0) currentLength.toFloat() / totalLength.toFloat() else 0f
                emit(progress)
                delay(waitTime)
            } finally {
                // 【核心修复】无论发生什么，确保关闭 Response 释放网络连接
                try {
                    response?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}