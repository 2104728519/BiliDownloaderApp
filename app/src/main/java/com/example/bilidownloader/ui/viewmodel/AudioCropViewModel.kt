package com.example.bilidownloader.ui.viewmodel

import android.app.Application
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bilidownloader.utils.FFmpegHelper // 【导入】
import com.example.bilidownloader.utils.StorageHelper // 【导入】
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.io.File

class AudioCropViewModel(application: Application) : AndroidViewModel(application) {

    // 1. 音频信息状态
    private val _totalDuration = MutableStateFlow(0L) // 总时长 (毫秒)
    val totalDuration = _totalDuration.asStateFlow()

    private val _isPlaying = MutableStateFlow(false) // 是否正在播放
    val isPlaying = _isPlaying.asStateFlow()

    // 【新增】保存状态：0=空闲, 1=正在保存, 2=成功, 3=失败
    private val _saveState = MutableStateFlow(0)
    val saveState = _saveState.asStateFlow()

    // 2. 播放器工具
    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null // 这是一个协程任务，负责“盯着”进度

    // 【新增】也就是当前的源文件路径（我们复制到缓存里的那个文件）
    private var sourceFilePath: String? = null

    /**
     * 加载音频信息 (终极版：直接读文件)
     * @param pathStr: 从上个页面传过来的编码后的本地文件绝对路径
     */
    fun loadAudioInfo(pathStr: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // 1. 解码路径
                    val filePath = java.net.URLDecoder.decode(pathStr, "UTF-8")
                    sourceFilePath = filePath // 【修改】记住源文件路径
                    val file = File(filePath)

                    println("正在加载缓存文件: $filePath")

                    if (!file.exists()) {
                        println("文件不存在！")
                        return@withContext
                    }

                    // 2. 读取时长 (直接传路径)
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(file.absolutePath)
                    val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    retriever.release()

                    val duration = durationStr?.toLong() ?: 0L
                    _totalDuration.value = duration

                    // 3. 初始化播放器 (直接传路径)
                    mediaPlayer?.release()
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        prepare()
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    println("加载音频失败: ${e.message}")
                }
            }
        }
    }

    /**
     * 播放选中的片段
     * @param startRatio: 开始比例 (0.0 ~ 1.0)
     * @param endRatio: 结束比例 (0.0 ~ 1.0)
     */
    fun playRegion(startRatio: Float, endRatio: Float) {
        val player = mediaPlayer ?: return
        val total = _totalDuration.value
        if (total == 0L) return

        // 算出具体的毫秒数
        val startMs = (total * startRatio).toLong()
        val endMs = (total * endRatio).toLong()

        // 1. 如果正在播，就先暂停
        if (player.isPlaying) {
            player.pause()
            _isPlaying.value = false
            playbackJob?.cancel() // 停止“盯着”进度的任务
            return
        }

        // 2. 开始播放
        viewModelScope.launch {
            player.seekTo(startMs.toInt()) // 跳到开始点
            player.start()
            _isPlaying.value = true

            // 3. 启动一个任务，每 100 毫秒检查一次进度
            playbackJob = launch {
                while (player.isPlaying) {
                    if (player.currentPosition >= endMs) {
                        // 到了结束点，自动暂停
                        player.pause()
                        _isPlaying.value = false
                        break
                    }
                    delay(100) // 休息 100 毫秒再检查
                }
            }
        }
    }

    /**
     * 【新增】执行裁剪并保存
     * @param fileName: 用户输入的文件名
     * @param startRatio: 开始比例
     * @param endRatio: 结束比例
     */
    fun saveCroppedAudio(fileName: String, startRatio: Float, endRatio: Float) {
        // 检查源文件路径和总时长是否有效
        val path = sourceFilePath ?: return
        val total = _totalDuration.value
        if (total == 0L) return

        // 切换到 IO 线程执行耗时操作
        viewModelScope.launch(Dispatchers.IO) {
            _saveState.value = 1 // 正在保存

            try {
                val context = getApplication<Application>()
                val cacheDir = context.cacheDir
                val inputFile = File(path)

                // 创建 FFmpeg 输出的临时文件
                val outFile = File(cacheDir, "crop_out_${System.currentTimeMillis()}.mp3")

                // 1. 计算时间 (单位：秒)
                val startSec = (total * startRatio) / 1000.0 // 开始时间 (毫秒 -> 秒)
                val endSec = (total * endRatio) / 1000.0
                val durationSec = endSec - startSec // 持续时长 (秒)

                // 2. 调用 FFmpeg 剪辑师执行裁剪
                val cropSuccess = FFmpegHelper.trimAudio(inputFile, outFile, startSec, durationSec)
                if (!cropSuccess) throw Exception("裁剪失败")

                // 3. 调用 StorageHelper 仓库管理员保存到音乐库
                // 自动补全 .mp3 后缀
                val finalName = if (fileName.endsWith(".mp3", ignoreCase = true)) fileName else "$fileName.mp3"
                // StorageHelper.saveAudioToMusic 是一个 suspend 函数，它自己会处理 IO 线程
                val saveSuccess = StorageHelper.saveAudioToMusic(context, outFile, finalName)

                if (saveSuccess) {
                    _saveState.value = 2 // 成功
                    println("音频保存成功: $finalName")
                } else {
                    throw Exception("保存到音乐库失败")
                }

                // 4. 清理 FFmpeg 生成的临时文件
                outFile.delete()

            } catch (e: Exception) {
                e.printStackTrace()
                println("保存音频时发生错误: ${e.message}")
                _saveState.value = 3 // 失败
            }
        }
    }

    /**
     * 重置保存状态 (方便 UI 隐藏提示或进行下次保存)
     */
    fun resetSaveState() {
        _saveState.value = 0
    }

    /**
     * 页面关闭时，释放资源 (非常重要，否则会内存泄漏)
     */
    override fun onCleared() {
        super.onCleared()
        mediaPlayer?.release()
        mediaPlayer = null
        playbackJob?.cancel()

        // 【可选清理】如果 sourceFilePath 是指向缓存文件，可以在这里尝试删除，但一般不急于删除
        // sourceFilePath?.let { path -> File(path).delete() }
    }
}