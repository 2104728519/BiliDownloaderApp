package com.example.bilidownloader.ui.viewmodel

import android.app.Application
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bilidownloader.utils.FFmpegHelper
import com.example.bilidownloader.utils.StorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder

class AudioCropViewModel(application: Application) : AndroidViewModel(application) {

    // 1. 状态变量
    private val _totalDuration = MutableStateFlow(0L)
    val totalDuration = _totalDuration.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    // 保存状态：0=空闲, 1=正在保存, 2=成功, 3=失败
    private val _saveState = MutableStateFlow(0)
    val saveState = _saveState.asStateFlow()

    private var sourceFilePath: String? = null

    // 2. 播放器及协程任务
    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null

    /**
     * 【新增】安全地停止音频播放
     * 严格遵循“先取消协程，再操作播放器”的原则，防止多线程冲突。
     */
    fun stopAudio() {
        // 1. 立即取消正在运行的播放监控协程
        playbackJob?.cancel()
        playbackJob = null

        // 2. 在 try-catch 块中安全地暂停播放器
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (e: IllegalStateException) {
            // 忽略错误，因为播放器可能已经被释放了，这是预期的行为
            println("MediaPlayer was already released. Safe to ignore.")
            e.printStackTrace()
        }

        // 3. 更新 UI 状态
        _isPlaying.value = false
    }

    /**
     * 加载音频信息
     * @param pathStr 编码后的文件路径
     */
    fun loadAudioInfo(pathStr: String) {
        // 【关键改进】加载新文件前，先调用安全停止方法，彻底清理上一个音频的状态
        stopAudio()

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val filePath = URLDecoder.decode(pathStr, "UTF-8")
                    sourceFilePath = filePath
                    val file = File(filePath)

                    if (!file.exists()) {
                        println("Error: Audio file does not exist at path: $filePath")
                        return@withContext
                    }

                    // 获取音频总时长
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(file.absolutePath)
                    val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    retriever.release()

                    val duration = durationStr?.toLong() ?: 0L
                    _totalDuration.value = duration

                    // 初始化一个新的 MediaPlayer 实例
                    mediaPlayer?.release() // 再次确保旧实例被释放
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        prepare()
                    }
                } catch (e: Exception) {
                    println("Error loading audio info: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * 播放指定的音频片段
     * @param startRatio 开始位置的比例 (0.0 to 1.0)
     * @param endRatio 结束位置的比例 (0.0 to 1.0)
     */
    fun playRegion(startRatio: Float, endRatio: Float) {
        val player = mediaPlayer ?: return
        val total = _totalDuration.value
        if (total == 0L) return

        val startMs = (total * startRatio).toLong()
        val endMs = (total * endRatio).toLong()

        // 如果当前正在播放，则调用安全停止方法；否则，开始播放
        if (_isPlaying.value) {
            stopAudio()
            return
        }

        viewModelScope.launch {
            try {
                player.seekTo(startMs.toInt())
                player.start()
                _isPlaying.value = true

                // 【改进】启动监控协程前，再次确保旧的已被取消
                playbackJob?.cancel()
                playbackJob = launch {
                    // 使用 isActive 检查可以确保协程在被外部取消时能及时退出循环
                    while (isActive && player.isPlaying) {
                        if (player.currentPosition >= endMs) {
                            // 到达终点，自动停止
                            stopAudio()
                            break
                        }
                        delay(100)
                    }
                    // 循环结束后（可能因为播放完毕或出错），确保状态为未播放
                    _isPlaying.value = false
                }
            } catch (e: Exception) {
                println("Error playing audio region: ${e.message}")
                e.printStackTrace()
                stopAudio() // 如果在播放过程中发生任何异常，都强制停止
            }
        }
    }

    /**
     * 执行裁剪并保存到系统音乐库
     * @param fileName 用户指定的输出文件名
     * @param startRatio 裁剪开始比例
     * @param endRatio 裁剪结束比例
     */
    fun saveCroppedAudio(fileName: String, startRatio: Float, endRatio: Float) {
        val path = sourceFilePath ?: return
        val total = _totalDuration.value
        if (total == 0L) return

        viewModelScope.launch(Dispatchers.IO) {
            _saveState.value = 1 // 状态：正在保存
            try {
                val context = getApplication<Application>()
                val cacheDir = context.cacheDir
                val inputFile = File(path)
                val outFile = File(cacheDir, "crop_out_${System.currentTimeMillis()}.mp3")

                val startSec = (total * startRatio) / 1000.0
                val endSec = (total * endRatio) / 1000.0
                val durationSec = endSec - startSec

                val cropSuccess = FFmpegHelper.trimAudio(inputFile, outFile, startSec, durationSec)
                if (!cropSuccess) throw Exception("FFmpeg trimming failed")

                val finalName = if (fileName.endsWith(".mp3", ignoreCase = true)) fileName else "$fileName.mp3"
                val saveSuccess = StorageHelper.saveAudioToMusic(context, outFile, finalName)

                if (saveSuccess) {
                    _saveState.value = 2 // 状态：成功
                } else {
                    throw Exception("Failed to save audio to the music library")
                }

                outFile.delete() // 清理临时文件
            } catch (e: Exception) {
                e.printStackTrace()
                _saveState.value = 3 // 状态：失败
            }
        }
    }

    /**
     * 重置保存状态，以便 UI 可以响应下一次操作
     */
    fun resetSaveState() {
        _saveState.value = 0
    }

    /**
     * ViewModel 销毁时被调用，是释放资源的最后时机
     */
    override fun onCleared() {
        super.onCleared()
        // 【关键改进】在 ViewModel 销毁时，调用安全停止方法并彻底释放播放器
        stopAudio()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}