package com.example.bilidownloader.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * 这是一个“搬运工”
 * 专门负责把网上的文件下载到手机本地
 */
class DownloadRepository {

    // 创建一个 HTTP 客户端，用来发请求
    private val client = OkHttpClient()

    /**
     * 下载文件的核心功能
     * @param url: 视频的下载链接
     * @param file: 手机本地要保存的文件位置
     * @return Flow<Float>: 这是一个“水管”，会不断流出当前的下载进度 (0.0 ~ 1.0)
     */
    fun downloadFile(url: String, file: File): Flow<Float> = flow {
        // 1. 准备请求
        // 【关键】必须带上 Referer，假装是从 B 站网页版访问的，否则会被拒绝访问 (403 Forbidden)
        val request = Request.Builder()
            .url(url)
            .addHeader("Referer", "https://www.bilibili.com/")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        // 2. 发起请求
        val response = client.newCall(request).execute()
        val body = response.body

        if (!response.isSuccessful || body == null) {
            throw Exception("下载失败，服务器返回代码: ${response.code}")
        }

        // 3. 拿到文件总大小 (用来算进度)
        val totalLength = body.contentLength()
        var bytesCopied: Long = 0
        val buffer = ByteArray(8 * 1024) // 创建一个 8KB 的小桶，每次搬一点
        var bytes = 0

        // 4. 开始搬运数据
        val inputStream: InputStream = body.byteStream()
        val outputStream = FileOutputStream(file) // 打开本地文件的盖子，准备灌水

        try {
            // 一桶一桶地读
            while (inputStream.read(buffer).also { bytes = it } >= 0) {
                // 写入本地文件
                outputStream.write(buffer, 0, bytes)

                // 更新已搬运的大小
                bytesCopied += bytes

                // 5. 计算进度并汇报
                // bytesCopied / totalLength = 当前百分比 (比如 0.5 就是 50%)
                if (totalLength > 0) {
                    val progress = bytesCopied.toFloat() / totalLength.toFloat()
                    emit(progress) // 通过“水管”把进度发出去
                }
            }
        } catch (e: Exception) {
            throw e // 如果中间出错了，往上报错
        } finally {
            // 6. 干完活记得关门
            outputStream.close()
            inputStream.close()
            response.close()
        }

    }.flowOn(Dispatchers.IO) // 【关键】这段繁重的体力活，强制在 IO 线程（后台）做，不许卡主界面
}