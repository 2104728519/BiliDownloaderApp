package com.example.bilidownloader.data.repository

import com.example.bilidownloader.core.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile

/**
 * 负责文件流下载
 * 特性：支持断点续传 (Range)、网络波动自动重试
 */
class DownloadRepository {

    // 使用专门的下载 Client
    private val client = NetworkModule.downloadClient

    /**
     * @param url 下载地址
     * @param file 目标文件
     * @return Flow 输出进度 0.0 ~ 1.0
     */
    fun downloadFile(url: String, file: File): Flow<Float> = flow {
        var currentLength = if (file.exists()) file.length() else 0L
        var totalLength = 0L
        var retryCount = 0
        val maxRetries = 10 // 最大重试次数

        // 循环直到下载完成
        while (true) {
            try {
                // 1. 构建请求，支持断点续传
                val requestBuilder = Request.Builder()
                    .url(url)
                    .addHeader("Referer", "https://www.bilibili.com/")
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

                // 如果本地已有文件，请求剩余部分
                if (currentLength > 0) {
                    requestBuilder.addHeader("Range", "bytes=$currentLength-")
                }

                val response = client.newCall(requestBuilder.build()).execute()
                val body = response.body

                // 处理服务器响应
                if (response.code == 416) {
                    // Range Not Satisfiable: 说明文件已经下载完了
                    response.close()
                    emit(1.0f)
                    return@flow
                }

                if (!response.isSuccessful || body == null) {
                    response.close()
                    throw Exception("HTTP Error: ${response.code}")
                }

                // 计算总大小
                val contentLength = body.contentLength()
                if (contentLength == 0L) {
                    response.close()
                    emit(1.0f)
                    return@flow
                }

                // 如果是 206 (Partial)，总长度 = 已有 + 剩余；如果是 200，说明服务器不支持续传，从头开始
                if (response.code == 206) {
                    totalLength = currentLength + contentLength
                } else {
                    // 服务器不支持续传，必须覆盖重写
                    currentLength = 0
                    totalLength = contentLength
                    // 只有这里需要重置文件
                    if (file.exists()) file.delete()
                    file.createNewFile()
                }

                // 2. 写入文件 (使用 RandomAccessFile 支持追加)
                val inputStream: InputStream = body.byteStream()
                val randomAccessFile = RandomAccessFile(file, "rw")
                randomAccessFile.seek(currentLength) // 移动光标到末尾

                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var bytesSinceEmit = 0L

                // 重置重试计数器，一旦连接成功并开始读数据，就认为之前的重试是值得的
                retryCount = 0

                while (inputStream.read(buffer).also { bytesRead = it } >= 0) {
                    randomAccessFile.write(buffer, 0, bytesRead)
                    currentLength += bytesRead
                    bytesSinceEmit += bytesRead

                    // 降低 emit 频率，每下载 100KB 通知一次，避免 UI 刷新太快卡顿
                    if (bytesSinceEmit > 100 * 1024) {
                        val progress = if (totalLength > 0) currentLength.toFloat() / totalLength.toFloat() else 0f
                        emit(progress)
                        bytesSinceEmit = 0
                    }
                }

                // 正常结束
                randomAccessFile.close()
                response.close()
                emit(1.0f)
                return@flow

            } catch (e: Exception) {
                // 3. 异常处理与重试
                if (retryCount >= maxRetries) {
                    throw Exception("超过最大重试次数: ${e.message}")
                }
                retryCount++
                // 等待一段时间后重试 (指数退避: 1s, 2s, 4s...)
                val waitTime = 1000L * retryCount
                emit(if (totalLength > 0) currentLength.toFloat() / totalLength.toFloat() else 0f) // 保持当前进度显示
                delay(waitTime)
                // 循环继续，下一次 loop 会自动带上新的 Range 头
            }
        }
    }.flowOn(Dispatchers.IO)
}