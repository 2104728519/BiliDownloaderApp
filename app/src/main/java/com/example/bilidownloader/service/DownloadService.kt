package com.example.bilidownloader.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.bilidownloader.MyApplication
import com.example.bilidownloader.R
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.data.repository.DownloadSession
import com.example.bilidownloader.domain.usecase.DownloadVideoUseCase
import com.example.bilidownloader.ui.state.FormatOption
import com.google.gson.Gson
import kotlinx.coroutines.*

/**
 * 视频下载前台服务.
 *
 * 职责：
 * 1. **进程保活**：通过 `startForeground` 将服务提升为前台服务，防止 App 切后台时被系统杀进程。
 * 2. **唤醒锁 (WakeLock)**：申请 `PARTIAL_WAKE_LOCK`，确保屏幕关闭时 CPU 继续运行以下载文件。
 * 3. **状态广播**：通过全局单例 [DownloadSession] 向 UI 层实时发送进度和状态。
 * 4. **通知栏控制**：在通知栏显示实时进度，并提供简单的交互入口。
 */
class DownloadService : Service() {

    // 独立的协程作用域，生命周期跟随 Service
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notificationManager: NotificationManager
    private lateinit var wakeLock: PowerManager.WakeLock

    private var downloadJob: Job? = null
    private var lastNotifyTime = 0L // 用于限制通知刷新频率

    companion object {
        const val ACTION_START = "action_start"
        const val ACTION_PAUSE = "action_pause"
        const val ACTION_RESUME = "action_resume"
        const val ACTION_CANCEL = "action_cancel"

        const val EXTRA_BVID = "extra_bvid"
        const val EXTRA_CID = "extra_cid"
        const val EXTRA_VIDEO_OPT = "extra_video_opt"
        const val EXTRA_AUDIO_OPT = "extra_audio_opt"
        const val EXTRA_AUDIO_ONLY = "extra_audio_only"

        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "download_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 申请唤醒锁 (超时时间 10 分钟，防止意外耗电)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BiliDownloader:DownloadService")
        wakeLock.acquire(10 * 60 * 1000L)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START, ACTION_RESUME -> {
                if (downloadJob?.isActive == true) return START_NOT_STICKY

                val bvid = intent.getStringExtra(EXTRA_BVID) ?: ""
                val cid = intent.getLongExtra(EXTRA_CID, 0L)
                val vOptJson = intent.getStringExtra(EXTRA_VIDEO_OPT)
                val aOptJson = intent.getStringExtra(EXTRA_AUDIO_OPT)
                val audioOnly = intent.getBooleanExtra(EXTRA_AUDIO_ONLY, false)

                if (bvid.isNotEmpty()) {
                    val gson = Gson()
                    val vOpt = if (vOptJson != null) gson.fromJson(vOptJson, FormatOption::class.java) else null
                    val aOpt = gson.fromJson(aOptJson, FormatOption::class.java)

                    val params = DownloadVideoUseCase.Params(bvid, cid, vOpt, aOpt, audioOnly)
                    startDownload(params)
                }
            }
            ACTION_PAUSE -> pauseDownload()
            ACTION_CANCEL -> cancelDownload()
        }
        // 如果服务被系统杀掉，不自动重启 (START_NOT_STICKY)，因为下载参数会丢失
        return START_NOT_STICKY
    }

    private fun startDownload(params: DownloadVideoUseCase.Params) {
        // 立即启动前台服务，避免 Android 12+ 的 ForegroundServiceStartNotAllowedException
        val notification = buildNotification("正在准备下载...", 0, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val appContainer = (application as MyApplication).container
        val downloadUseCase = appContainer.downloadVideoUseCase

        downloadJob = serviceScope.launch {
            try {
                downloadUseCase(params).collect { resource ->
                    val currentTime = System.currentTimeMillis()
                    // 限制通知刷新频率 (每秒最多一次)，防止系统丢弃更新或导致卡顿
                    var shouldUpdateNotif = true
                    if (resource is Resource.Loading && (currentTime - lastNotifyTime < 1000 && resource.progress > 0.01f)) {
                        shouldUpdateNotif = false
                    }

                    if (shouldUpdateNotif) {
                        val (message, progress, isIndeterminate) = when (resource) {
                            is Resource.Loading -> Triple(resource.data ?: "下载中...", (resource.progress * 100).toInt(), false)
                            is Resource.Success -> Triple("下载完成", 100, false)
                            is Resource.Error -> Triple("出错: ${resource.message}", 0, false)
                        }
                        updateNotification(message, progress, isIndeterminate)
                        if (resource is Resource.Loading) lastNotifyTime = currentTime
                    }

                    // 广播状态给 UI
                    DownloadSession.updateState(resource)

                    when (resource) {
                        is Resource.Success -> {
                            launch {
                                // 下载成功：移除前台状态但保留通知，5秒后自动关闭服务
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    stopForeground(STOP_FOREGROUND_DETACH)
                                } else {
                                    @Suppress("DEPRECATION")
                                    stopForeground(false)
                                }
                                delay(5000)
                                notificationManager.cancel(NOTIFICATION_ID)
                                stopSelf()
                            }
                        }
                        is Resource.Error -> {
                            // 下载失败：保持前台服务状态，让用户知道出错了
                            stopForeground(true)
                        }
                        else -> { /* Loading... */ }
                    }
                }
            } catch (e: CancellationException) {
                // 协程被取消 (暂停或停止)
            } catch (e: Exception) {
                val errorResource = Resource.Error<String>(e.message ?: "未知错误")
                DownloadSession.updateState(errorResource)
                updateNotification(errorResource.message ?: "未知错误", 0, false)
                stopForeground(true)
            }
        }
    }

    private fun pauseDownload() {
        if (downloadJob?.isActive == true) {
            downloadJob?.cancel()
            downloadJob = null
            updateNotification("下载已暂停", 0, false)
            serviceScope.launch {
                DownloadSession.updateState(Resource.Error("PAUSED"))
            }
        }
    }

    private fun cancelDownload() {
        if (downloadJob?.isActive == true) {
            downloadJob?.cancel()
            downloadJob = null
        }
        serviceScope.launch {
            DownloadSession.updateState(Resource.Error("CANCELED"))
        }
        notificationManager.cancel(NOTIFICATION_ID)
        stopForeground(true)
        stopSelf()
    }

    private fun updateNotification(content: String, progress: Int, indeterminate: Boolean = false) {
        val notification = buildNotification(content, progress, indeterminate)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(content: String, progress: Int, indeterminate: Boolean): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("B 站视频下载中")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setProgress(100, progress, indeterminate)
            .setOngoing(progress < 100)
            .setOnlyAlertOnce(true) // 避免每次更新进度都发出声音/震动
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "视频下载",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        // 兜底逻辑：如果服务销毁时任务仍在运行（例如被系统强制回收）
        // 发送 CANCELED 信号防止 UI 无限转圈
        if (downloadJob?.isActive == true) {
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch {
                DownloadSession.updateState(Resource.Error("CANCELED"))
            }
        }

        super.onDestroy()
        serviceScope.cancel()

        // 释放唤醒锁
        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}