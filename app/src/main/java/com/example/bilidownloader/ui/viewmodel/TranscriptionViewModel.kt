package com.example.bilidownloader.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bilidownloader.data.api.RetrofitClient
import com.example.bilidownloader.data.model.TranscriptionInput
import com.example.bilidownloader.data.model.TranscriptionRequest
import com.example.bilidownloader.utils.OssManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLDecoder

class TranscriptionViewModel(application: Application) : AndroidViewModel(application) {

    // API Key (建议放入 local.properties 或 BuildConfig)
    private val API_KEY = "Bearer sk-1ff9e29f9aa34417826c1974d64fdd96"

    // 状态
    private val _uiState = MutableStateFlow<TransState>(TransState.Idle)
    val uiState = _uiState.asStateFlow()

    sealed class TransState {
        object Idle : TransState() // 初始状态：等待用户点击
        data class Processing(val step: String) : TransState()
        data class Success(val text: String) : TransState()
        data class Error(val msg: String) : TransState()
    }

    // 接收 String 路径 (URL Encoded)
    fun startTranscription(pathStr: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = TransState.Processing("正在准备文件...")
            val context = getApplication<Application>()
            var cacheFile: File? = null
            var ossFileName: String? = null

            try {
                // 1. 解码路径
                val filePath = URLDecoder.decode(pathStr, "UTF-8")
                cacheFile = File(filePath)

                if (!cacheFile.exists()) {
                    throw Exception("文件不存在或已被删除")
                }
                ossFileName = cacheFile.name

                // 2. 上传 OSS
                _uiState.value = TransState.Processing("正在上传音频到云端...")
                val fileUrl = OssManager.uploadAndGetUrl(context, cacheFile)

                // 3. 提交任务
                _uiState.value = TransState.Processing("正在提交转写任务...")
                val request = TranscriptionRequest(input = TranscriptionInput(listOf(fileUrl)))
                val submitResp = RetrofitClient.aliyunService.submitTranscription(API_KEY, request = request)

                val taskId = submitResp.output?.task_id
                if (taskId == null) throw Exception("提交失败: ${submitResp.message}")

                // 4. 轮询结果
                var resultText = ""
                // 轮询时间：最多等待约 5 分钟 (100 * 3s)
                for (i in 1..100) {
                    _uiState.value = TransState.Processing("转写中... (${i * 3}s)")
                    delay(3000)

                    val statusResp = RetrofitClient.aliyunService.getTaskStatus(API_KEY, taskId)
                    val status = statusResp.output?.task_status

                    if (status == "SUCCEEDED") {
                        val resultUrl = statusResp.output.results?.get(0)?.transcription_url
                        if (resultUrl != null) {
                            val transcriptData = RetrofitClient.aliyunService.downloadTranscript(resultUrl)
                            resultText = transcriptData.transcripts?.joinToString("\n") { it.text } ?: "无内容"
                        }
                        break
                    } else if (status == "FAILED") {
                        throw Exception("转写任务失败: ${statusResp.output?.code} - ${statusResp.output?.message}")
                    }
                }

                if (resultText.isNotEmpty()) {
                    _uiState.value = TransState.Success(resultText)

                    // 【修改】成功后才删除 OSS 文件和本地缓存
                    // 这样如果失败了，用户点击重试时文件还在
                    try {
                        if (ossFileName != null) OssManager.deleteFile(context, ossFileName)
                        cacheFile.delete()
                    } catch (e: Exception) { e.printStackTrace() }

                } else {
                    throw Exception("转写超时或无结果")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = TransState.Error(e.message ?: "未知错误")
                // 注意：这里不删除文件，以便重试
            }
        }
    }
}