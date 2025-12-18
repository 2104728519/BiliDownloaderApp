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
import kotlinx.coroutines.*

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var notificationManager: NotificationManager
    private lateinit var wakeLock: PowerManager.WakeLock

    private var downloadJob: Job? = null
    private var lastNotifyTime = 0L

    companion object {
        const val ACTION_START = "action_start"
        const val ACTION_PAUSE = "action_pause"
        const val ACTION_RESUME = "action_resume"
        const val ACTION_CANCEL = "action_cancel"
        const val ACTION_PROGRESS_UPDATE = "action_progress_update"

        const val EXTRA_BVID = "extra_bvid"
        const val EXTRA_CID = "extra_cid"
        const val EXTRA_VIDEO_OPT = "extra_video_opt"
        const val EXTRA_AUDIO_OPT = "extra_audio_opt"
        const val EXTRA_AUDIO_ONLY = "extra_audio_only"

        const val BROADCAST_STATUS = "status"
        const val BROADCAST_MESSAGE = "message"
        const val BROADCAST_PROGRESS = "progress"

        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "download_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

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
        return START_NOT_STICKY
    }

    private fun startDownload(params: DownloadVideoUseCase.Params) {
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
                    when (resource) {
                        is Resource.Loading -> {
                            val p = (resource.progress * 100).toInt()
                            val msg = resource.data ?: "下载中..."

                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastNotifyTime > 1000 || resource.progress <= 0.01f) {
                                updateNotification(msg, p, false)
                                lastNotifyTime = currentTime
                            }

                            sendBroadcast(Resource.Loading(resource.progress, msg))
                        }
                        is Resource.Success -> {
                            // 1. 先更新状态为完成
                            updateNotification("下载完成", 100, false)
                            sendBroadcast(Resource.Success(resource.data!!))

                            // 【核心修改】不立即自杀，而是启动一个延时任务
                            launch {
                                // (A) 退出前台服务状态，但保留通知 (STOP_FOREGROUND_DETACH)
                                // 这样服务不再是“前台”服务，但通知还在
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    stopForeground(STOP_FOREGROUND_DETACH)
                                } else {
                                    @Suppress("DEPRECATION")
                                    stopForeground(false)
                                }

                                // (B) 延迟 5 秒
                                delay(5000)

                                // (C) 5秒后，移除通知并停止服务
                                notificationManager.cancel(NOTIFICATION_ID)
                                stopSelf()
                            }
                        }
                        is Resource.Error -> {
                            updateNotification("出错: ${resource.message}", 0, false)
                            sendBroadcast(Resource.Error(resource.message ?: "Error"))
                            // 出错时不自动消失，让用户看到，或者你可以也加个 delay
                            stopForeground(true) // 出错可以直接移除前台状态
                            // stopSelf() // 可以选择不立即停止，等待用户操作
                        }
                    }
                }
            } catch (e: CancellationException) {
                // 协程取消触发
            }
        }
    }

    private fun pauseDownload() {
        if (downloadJob?.isActive == true) {
            downloadJob?.cancel()
            downloadJob = null
            updateNotification("下载已暂停", 0, false)
            sendBroadcast(Resource.Error("PAUSED"))
        }
    }

    private fun cancelDownload() {
        if (downloadJob?.isActive == true) {
            downloadJob?.cancel()
            downloadJob = null
        }
        sendBroadcast(Resource.Error("CANCELED"))
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
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
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
                "视频下载",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}