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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.bilidownloader.MyApplication
import com.example.bilidownloader.R
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.domain.DownloadVideoUseCase
import com.example.bilidownloader.ui.state.FormatOption
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notificationManager: NotificationManager
    private lateinit var wakeLock: PowerManager.WakeLock

    companion object {
        const val ACTION_START_DOWNLOAD = "action_start_download"
        const val ACTION_PROGRESS_UPDATE = "action_progress_update"

        // Intent Params
        const val EXTRA_BVID = "extra_bvid"
        const val EXTRA_CID = "extra_cid"
        const val EXTRA_VIDEO_OPT = "extra_video_opt" // JSON string
        const val EXTRA_AUDIO_OPT = "extra_audio_opt" // JSON string
        const val EXTRA_AUDIO_ONLY = "extra_audio_only"

        // Broadcast Extras
        const val BROADCAST_STATUS = "status" // "loading", "success", "error"
        const val BROADCAST_MESSAGE = "message"
        const val BROADCAST_PROGRESS = "progress"

        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "download_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 获取电源锁，防止熄屏后 CPU 休眠导致 FFmpeg 合并失败
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BiliDownloader:DownloadService")
        wakeLock.acquire(10 * 60 * 1000L /* 10 minutes timeout */)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_START_DOWNLOAD) {
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
        return START_NOT_STICKY
    }

    private fun startDownload(params: DownloadVideoUseCase.Params) {
        // 1. 启动前台通知
        val notification = buildNotification("正在准备下载...", 0, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // 2. 获取 UseCase (通过 Application 容器)
        val appContainer = (application as MyApplication).container
        val downloadUseCase = appContainer.downloadVideoUseCase

        // 3. 执行任务
        serviceScope.launch {
            downloadUseCase(params).collect { resource ->
                when (resource) {
                    is Resource.Loading -> {
                        val progress = resource.progress
                        val msg = resource.data ?: "下载中..."
                        updateNotification(msg, (progress * 100).toInt())
                        sendBroadcast(Resource.Loading(progress, msg))
                    }
                    is Resource.Success -> {
                        updateNotification("下载完成: ${resource.data}", 100, false)
                        sendBroadcast(Resource.Success(resource.data!!))
                        stopSelf() // 任务完成，停止服务
                    }
                    is Resource.Error -> {
                        updateNotification("下载失败: ${resource.message}", 0, false)
                        sendBroadcast(Resource.Error(resource.message ?: "未知错误"))
                        stopSelf()
                    }
                }
            }
        }
    }

    private fun updateNotification(content: String, progress: Int, indeterminate: Boolean = false) {
        val notification = buildNotification(content, progress, indeterminate)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(content: String, progress: Int, indeterminate: Boolean): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BiliDownloader 任务运行中")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // 请确保你有这个资源，或者换成 R.mipmap.ic_launcher
            .setProgress(100, progress, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun sendBroadcast(resource: Resource<String>) {
        val intent = Intent(ACTION_PROGRESS_UPDATE)
        when (resource) {
            is Resource.Loading -> {
                intent.putExtra(BROADCAST_STATUS, "loading")
                intent.putExtra(BROADCAST_PROGRESS, resource.progress)
                intent.putExtra(BROADCAST_MESSAGE, resource.data)
            }
            is Resource.Success -> {
                intent.putExtra(BROADCAST_STATUS, "success")
                intent.putExtra(BROADCAST_MESSAGE, resource.data)
            }
            is Resource.Error -> {
                intent.putExtra(BROADCAST_STATUS, "error")
                intent.putExtra(BROADCAST_MESSAGE, resource.message)
            }
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "文件下载服务",
                NotificationManager.IMPORTANCE_LOW // Low 可以在不发出声音的情况下显示进度条
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (wakeLock.isHeld) wakeLock.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}