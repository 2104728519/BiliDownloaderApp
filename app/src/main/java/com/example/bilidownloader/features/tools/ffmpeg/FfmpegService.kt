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
import com.example.bilidownloader.R
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

        // 1. 启动前台服务通知 (Ongoing = true, 用户无法划掉)
        val notification = buildNotification("FFmpeg 准备中...", 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        // 2. 获取 Repository 实例
        val repo = (application as MyApplication).container.ffmpegRepository

        // 3. 启动协程执行任务
        taskJob = serviceScope.launch {
            repo.executeCommand(inputUri, args, ext).collect { state ->
                // 更新全局 Session 状态供 UI 观察
                FfmpegSession.updateState(state)

                // 更新通知栏处理逻辑
                when (state) {
                    is FfmpegTaskState.Running -> {
                        val progressInt = (state.progress * 100).toInt()
                        updateNotification("处理中: $progressInt%", progressInt)
                    }
                    is FfmpegTaskState.Success -> {
                        // [修改点] 成功后：彻底移除通知并停止服务
                        stopForegroundService(removeNotification = true)
                    }
                    is FfmpegTaskState.Error -> {
                        // [修改点] 失败后：彻底移除通知并停止服务
                        // 错误信息由 UI 层负责向用户展示
                        stopForegroundService(removeNotification = true)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun stopTask() {
        if (taskJob?.isActive == true) {
            taskJob?.cancel() // 取消协程 -> 触发 FFmpegKit 的取消
            taskJob = null
        }
        FfmpegSession.updateState(FfmpegTaskState.Idle)
        stopForegroundService(removeNotification = true)
    }

    private fun stopForegroundService(removeNotification: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(if (removeNotification) STOP_FOREGROUND_REMOVE else STOP_FOREGROUND_DETACH)
        } else {
            stopForeground(removeNotification)
        }

        // 释放电源锁
        if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()

        // 销毁 Service
        stopSelf()
    }

    // --- 通知辅助方法 ---

    private fun updateNotification(content: String, progress: Int) {
        notificationManager.notify(NOTIF_ID, buildNotification(content, progress))
    }

    private fun buildNotification(content: String, progress: Int): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("视频合成中")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOnlyAlertOnce(true)
            .setOngoing(true) // 运行中不可被滑动删除
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (progress in 0..100) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        // 添加停止按钮
        val stopIntent = Intent(this, FfmpegService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = android.app.PendingIntent.getService(
            this, 0, stopIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                android.app.PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "取消任务", stopPendingIntent)

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