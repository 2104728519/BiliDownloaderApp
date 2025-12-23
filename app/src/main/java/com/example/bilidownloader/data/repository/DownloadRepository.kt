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
 * 文件下载核心仓库.
 *
 * 实现了基于 HTTP Range 头的断点续传下载器。
 * 特性：
 * 1. **断点续传**：检查本地文件大小，自动设置 Range 头从中断处继续下载。
 * 2. **协程支持**：支持取消 (Cancellation)，并能正确释放资源。
 * 3. **自动重试**：针对网络波动提供指数退避重试机制。
 */
class DownloadRepository {

    private val client = NetworkModule.downloadClient

    fun downloadFile(url: String, file: File): Flow<Float> = flow {
        var currentLength = if (file.exists()) file.length() else 0L
        var totalLength = 0L
        var retryCount = 0
        val maxRetries = 10

        while (true) {
            var response: Response? = null

            try {
                // 1. 构建请求，注入防盗链 Header
                val requestBuilder = Request.Builder()
                    .url(url)
                    .addHeader("Referer", "https://www.bilibili.com/")
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

                // 2. 设置断点 (Range: bytes=xxx-)
                if (currentLength > 0) {
                    requestBuilder.addHeader("Range", "bytes=$currentLength-")
                }

                // 3. 执行请求
                response = client.newCall(requestBuilder.build()).execute()
                val body = response.body

                // 416 Range Not Satisfiable: 说明文件已下载完成
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

                // 206 Partial Content: 续传成功
                if (response.code == 206) {
                    totalLength = currentLength + contentLength
                } else {
                    // 200 OK: 服务器不支持续传或文件被重置
                    currentLength = 0
                    totalLength = contentLength
                    if (file.exists()) file.delete()
                    file.createNewFile()
                }

                val inputStream: InputStream = body.byteStream()

                // 使用 RandomAccessFile 实现从指定位置写入
                RandomAccessFile(file, "rw").use { raf ->
                    raf.seek(currentLength)

                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var bytesSinceEmit = 0L

                    retryCount = 0 // 连接成功，重置重试计数器

                    while (inputStream.read(buffer).also { bytesRead = it } >= 0) {
                        // 响应协程取消信号
                        currentCoroutineContext().ensureActive()

                        raf.write(buffer, 0, bytesRead)
                        currentLength += bytesRead
                        bytesSinceEmit += bytesRead

                        // 减少 emit 频率，避免 UI 刷新过快
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
                // 若为用户主动取消，则不重试
                if (e is CancellationException) {
                    throw e
                }

                if (retryCount >= maxRetries) {
                    throw Exception("超过最大重试次数: ${e.message}")
                }
                retryCount++

                val waitTime = 1000L * retryCount
                val progress = if (totalLength > 0) currentLength.toFloat() / totalLength.toFloat() else 0f
                emit(progress) // 发送旧进度以维持 UI 状态
                delay(waitTime)
            } finally {
                // 确保释放网络连接
                try {
                    response?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }.flowOn(Dispatchers.IO)
}