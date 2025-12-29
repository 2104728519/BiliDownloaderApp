// 文件: features/ffmpeg/FfmpegService.kt
package com.example.bilidownloader.features.ffmpeg

import android.app.Notification
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
import com.example.bilidownloader.R // 确保导入你的 R 文件
import kotlinx.coroutines.*

/**
 * FFmpeg 后台服务.
 *
 * 职责：
 * 1. 在前台服务中运行 FFmpeg 任务，防止被系统杀后台。
 * 2. 监听停止指令，取消任务。
 * 3. 更新通知栏进度。
 */
class FfmpegService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var taskJob: Job? = null
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var notificationManager: NotificationManager

    companion object {
        const val ACTION_START = "action_start"
        const val ACTION_STOP = "action_stop"

        const val EXTRA_INPUT_URI = "extra_input_uri"
        const val EXTRA_ARGS = "extra_args"
        const val EXTRA_EXT = "extra_ext"

        private const val NOTIF_ID = 2001
        private const val CHANNEL_ID = "ffmpeg_channel"
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        // 获取电源锁，防止 CPU 休眠
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BiliDownloader:FfmpegService")
        wakeLock.acquire(60 * 60 * 1000L) // 最多持有1小时
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val inputUri = intent.getStringExtra(EXTRA_INPUT_URI) ?: ""
                val args = intent.getStringExtra(EXTRA_ARGS) ?: ""
                val ext = intent.getStringExtra(EXTRA_EXT) ?: ".mp4"
                startTask(inputUri, args, ext)
            }
            ACTION_STOP -> {
                stopTask()
            }
        }
        return START_NOT_STICKY
    }

    private fun startTask(inputUri: String, args: String, ext: String) {
        if (taskJob?.isActive == true) return // 已有任务在运行

        // 1. 启动前台服务通知
        val notification = buildNotification("FFmpeg 处理中...", 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        // 2. 获取 Repository 实例 (通过 Application 获取 DI 容器)
        val repo = (application as MyApplication).container.ffmpegRepository

        // 3. 启动协程执行任务
        taskJob = serviceScope.launch {
            repo.executeCommand(inputUri, args, ext).collect { state ->
                // 更新全局 Session
                FfmpegSession.updateState(state)

                // 更新通知栏
                when (state) {
                    is FfmpegTaskState.Running -> {
                        val progressInt = (state.progress * 100).toInt()
                        updateNotification("处理中: $progressInt%", progressInt)
                    }
                    is FfmpegTaskState.Success -> {
                        updateNotification("处理完成", 100)
                        stopForegroundService(removeNotification = false)
                    }
                    is FfmpegTaskState.Error -> {
                        updateNotification("处理失败: ${state.message}", 0)
                        stopForegroundService(removeNotification = false)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun stopTask() {
        if (taskJob?.isActive == true) {
            taskJob?.cancel() // 取消协程 -> 触发 Repository 的 awaitClose -> 触发 FFmpegKit.cancel
            taskJob = null
        }
        FfmpegSession.updateState(FfmpegTaskState.Idle) // 重置状态
        stopForegroundService(removeNotification = true)
    }

    private fun stopForegroundService(removeNotification: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(if (removeNotification) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH)
        } else {
            stopForeground(removeNotification)
        }
        // 如果任务结束，释放 WakeLock 并停止服务
        if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()
        stopSelf()
    }

    // --- 通知辅助方法 ---

    private fun updateNotification(content: String, progress: Int) {
        notificationManager.notify(NOTIF_ID, buildNotification(content, progress))
    }

    private fun buildNotification(content: String, progress: Int): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FFmpeg 终端")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // 请确保图标存在
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (progress in 0..100) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true) // 不确定进度
        }

        // 添加停止按钮
        val stopIntent = Intent(this, FfmpegService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = android.app.PendingIntent.getService(
            this, 0, stopIntent, android.app.PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止", stopPendingIntent)

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "FFmpeg 任务", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        taskJob?.cancel()
        serviceScope.cancel()
        if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}