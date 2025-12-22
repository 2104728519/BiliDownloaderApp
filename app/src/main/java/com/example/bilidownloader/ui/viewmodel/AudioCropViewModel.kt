package com.example.bilidownloader.ui.viewmodel

import android.app.Application
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bilidownloader.core.util.FFmpegHelper
import com.example.bilidownloader.core.util.StorageHelper
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

class AudioCropViewModel(application : Application) : AndroidViewModel(application) {

    // 1. 状态变量
    private val _totalDuration = MutableStateFlow(0L)
    val totalDuration = _totalDuration.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    // 【新增】当前播放进度 (毫秒)
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    // 保存状态：0=空闲, 1=正在保存, 2=成功, 3=失败
    private val _saveState = MutableStateFlow(0)
    val saveState = _saveState.asStateFlow()

    private var sourceFilePath: String? = null

    // 2. 播放器及协程任务
    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null


    /**
     * 【修改】安全地停止音频播放
     * 同时取消协程、暂停播放器并重置进度
     */
    fun stopAudio() {
        playbackJob?.cancel()
        playbackJob = null

        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }

        _isPlaying.value = false
        _currentPosition.value = 0L // 重置进度显示
    }

    /**
     * 加载音频信息
     */
    fun loadAudioInfo(pathStr: String) {
        stopAudio() // 加载前先清理旧状态

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val filePath = URLDecoder.decode(pathStr, "UTF-8")
                    sourceFilePath = filePath
                    val file = File(filePath)
                    if (!file.exists()) return@withContext

                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(file.absolutePath)
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
                    retriever.release()
                    _totalDuration.value = duration

                    mediaPlayer?.release()
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        prepare()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * 【修改】播放指定的音频片段，并实时更新进度
     */
    fun playRegion(startRatio: Float, endRatio: Float) {
        val player = mediaPlayer ?: return
        val total = _totalDuration.value
        if (total == 0L) return

        if (_isPlaying.value) {
            stopAudio()
            return
        }

        val startMs = (total * startRatio).toLong()
        val endMs = (total * endRatio).toLong()

        viewModelScope.launch {
            try {
                player.seekTo(startMs.toInt())
                player.start()
                _isPlaying.value = true

                playbackJob?.cancel()
                playbackJob = launch {
                    while (isActive && player.isPlaying) {
                        val current = player.currentPosition.toLong()
                        _currentPosition.value = current // 【新增】更新播放进度

                        if (current >= endMs) {
                            stopAudio()
                            break
                        }
                        delay(50) // 每 50 毫秒更新一次
                    }
                    _isPlaying.value = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                stopAudio()
            }
        }
    }


    /**
     * 执行裁剪并保存
     */
    fun saveCroppedAudio(fileName: String, startRatio: Float, endRatio: Float) {
        val path = sourceFilePath ?: return
        val total = _totalDuration.value
        if (total == 0L) return

        viewModelScope.launch(Dispatchers.IO) {
            _saveState.value = 1
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
                    _saveState.value = 2
                } else {
                    throw Exception("Failed to save audio to the music library")
                }
                outFile.delete()
            } catch (e: Exception) {
                e.printStackTrace()
                _saveState.value = 3
            }
        }
    }

    fun resetSaveState() {
        _saveState.value = 0
    }

    override fun onCleared() {
        super.onCleared()
        stopAudio()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}