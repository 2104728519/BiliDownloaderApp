package com.example.bilidownloader.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bilidownloader.data.model.TranscriptionInput
import com.example.bilidownloader.data.model.TranscriptionRequest
import com.example.bilidownloader.core.manager.CookieManager
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.core.util.ConsoleScraper
import com.example.bilidownloader.core.util.OssManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLDecoder

/**
 * 音频转写 ViewModel.
 *
 * 核心流程：
 * 1. **文件上传**：因为阿里云听悟 API 仅支持公网 URL，需先将本地文件上传至 OSS 获取签名链接。
 * 2. **异步提交**：调用 API 提交转写任务。
 * 3. **状态轮询**：循环检查任务状态 (PENDING -> RUNNING -> SUCCEEDED)。
 * 4. **配额爬取**：利用 [ConsoleScraper] 爬取用户剩余免费额度。
 */
class TranscriptionViewModel(application: Application) : AndroidViewModel(application) {

    private val API_KEY = "Bearer sk-xxxxxxxxxxxxxxxxxxxx" // 实际使用请配置在 local.properties

    private val _uiState = MutableStateFlow<TransState>(TransState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _usageState = MutableStateFlow<UsageState>(UsageState.Loading)
    val usageState = _usageState.asStateFlow()

    sealed class TransState {
        object Idle : TransState()
        data class Processing(val step: String) : TransState()
        data class Success(val text: String) : TransState()
        data class Error(val msg: String) : TransState()
    }

    sealed class UsageState {
        object Loading : UsageState()
        object Idle : UsageState() // 未配置凭证
        data class Success(val usedMinutes: Double, val totalMinutes: Double = 600.0) : UsageState()
        data class Error(val msg: String) : UsageState()
    }

    init {
        loadUsage()
    }

    fun loadUsage() {
        viewModelScope.launch {
            _usageState.value = UsageState.Loading
            val context = getApplication<Application>()
            val cookie = CookieManager.getAliyunConsoleCookie(context)
            val secToken = CookieManager.getAliyunConsoleSecToken(context)

            if (cookie.isNullOrBlank() || secToken.isNullOrBlank()) {
                _usageState.value = UsageState.Idle
                return@launch
            }

            val totalSeconds = ConsoleScraper.getTotalUsageInSeconds(cookie, secToken)
            if (totalSeconds != null) {
                _usageState.value = UsageState.Success(usedMinutes = totalSeconds / 60)
            } else {
                _usageState.value = UsageState.Error("查询失败，请检查凭证是否过期")
            }
        }
    }

    fun startTranscription(pathStr: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = TransState.Processing("正在准备文件...")
            val context = getApplication<Application>()
            var cacheFile: File? = null
            var ossFileName: String? = null

            try {
                val filePath = URLDecoder.decode(pathStr, "UTF-8")
                cacheFile = File(filePath)
                if (!cacheFile.exists()) throw Exception("文件不存在")
                ossFileName = cacheFile.name

                // 1. 上传 OSS
                _uiState.value = TransState.Processing("正在上传音频到云端...")
                val fileUrl = OssManager.uploadAndGetUrl(context, cacheFile)

                // 2. 提交任务
                _uiState.value = TransState.Processing("正在提交转写任务...")
                val request = TranscriptionRequest(input = TranscriptionInput(listOf(fileUrl)))
                val submitResp = NetworkModule.aliyunService.submitTranscription(API_KEY, request = request)
                val taskId = submitResp.output?.task_id ?: throw Exception("提交失败: ${submitResp.message}")

                // 3. 轮询结果 (最多等待 5 分钟)
                var resultText = ""
                for (i in 1..100) {
                    _uiState.value = TransState.Processing("转写中... (${i * 3}s)")
                    delay(3000)

                    val statusResp = NetworkModule.aliyunService.getTaskStatus(API_KEY, taskId)
                    val status = statusResp.output?.task_status

                    if (status == "SUCCEEDED") {
                        val resultUrl = statusResp.output.results?.get(0)?.transcription_url
                        if (resultUrl != null) {
                            val transcriptData = NetworkModule.aliyunService.downloadTranscript(resultUrl)
                            resultText = transcriptData.transcripts?.joinToString("\n") { it.text } ?: "无内容"
                        }
                        break
                    } else if (status == "FAILED") {
                        throw Exception("任务失败: ${statusResp.output?.message}")
                    }
                }

                if (resultText.isNotEmpty()) {
                    _uiState.value = TransState.Success(resultText)
                    // 清理云端和本地临时文件
                    try {
                        if (ossFileName != null) OssManager.deleteFile(context, ossFileName)
                        cacheFile.delete()
                    } catch (e: Exception) { e.printStackTrace() }
                } else {
                    throw Exception("转写超时")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = TransState.Error(e.message ?: "未知错误")
            }
        }
    }
}