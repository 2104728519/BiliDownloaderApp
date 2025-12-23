package com.example.bilidownloader.features.tools.audiocrop

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

/**
 * 音频裁剪 ViewModel.
 *
 * 负责：
 * 1. **音频加载**：解析文件元数据，获取时长。
 * 2. **播放控制**：管理 MediaPlayer 实例，支持区间循环播放。
 * 3. **裁剪执行**：调用 FFmpeg 进行精确裁剪。
 */
class AudioCropViewModel(application : Application) : AndroidViewModel(application) {

    private val _totalDuration = MutableStateFlow(0L)
    val totalDuration = _totalDuration.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    // 0=Idle, 1=Saving, 2=Success, 3=Error
    private val _saveState = MutableStateFlow(0)
    val saveState = _saveState.asStateFlow()

    private var sourceFilePath: String? = null
    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null

    /**
     * 停止播放并重置播放器状态.
     * 必须在页面销毁或重新加载文件时调用.
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
        _currentPosition.value = 0L
    }

    fun loadAudioInfo(pathStr: String) {
        stopAudio()

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
     * 播放指定区间.
     * 启动一个协程每 50ms 轮询一次进度，用于更新 UI 进度条。
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
                        _currentPosition.value = current

                        if (current >= endMs) {
                            stopAudio()
                            break
                        }
                        delay(50)
                    }
                    _isPlaying.value = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                stopAudio()
            }
        }
    }

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

                // 调用 FFmpeg 进行裁剪 (重编码为 MP3 保证兼容性)
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

    fun resetSaveState() { _saveState.value = 0 }

    override fun onCleared() {
        super.onCleared()
        stopAudio()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}