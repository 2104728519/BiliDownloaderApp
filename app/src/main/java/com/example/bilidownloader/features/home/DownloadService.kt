package com.example.bilidownloader.features.home

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

        const val EXTRA_BVID = "extra_bvid"
        const val EXTRA_CID = "extra_cid"
        const val EXTRA_AUDIO_ONLY = "extra_audio_only"

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
                val audioOnly = intent.getBooleanExtra(EXTRA_AUDIO_ONLY, false)

                // 接收简化参数
                val vid = intent.getIntExtra("vid", 0)
                val vcodec = intent.getStringExtra("vcodec")
                val aid = intent.getIntExtra("aid", 0)
                val acodec = intent.getStringExtra("acodec")

                if (bvid.isNotEmpty()) {
                    val params = DownloadRepository.DownloadParams(
                        bvid, cid,
                        if(vid == 0) null else vid,
                        vcodec,
                        aid, acodec, audioOnly
                    )
                    startDownload(params)
                }
            }
            ACTION_PAUSE -> pauseDownload()
            ACTION_CANCEL -> cancelDownload()
        }
        return START_NOT_STICKY
    }

    private fun startDownload(params: DownloadRepository.DownloadParams) {
        val notification = buildNotification("正在准备下载...", 0, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val appContainer = (application as MyApplication).container
        val downloadRepo = appContainer.downloadRepository

        downloadJob = serviceScope.launch {
            try {
                // 【修改】直接调用 Repository
                downloadRepo.downloadVideo(params).collect { resource ->
                    val currentTime = System.currentTimeMillis()
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

                    DownloadSession.updateState(resource)

                    when (resource) {
                        is Resource.Success -> {
                            launch {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_DETACH)
                                else stopForeground(false)
                                delay(5000)
                                notificationManager.cancel(NOTIFICATION_ID)
                                stopSelf()
                            }
                        }
                        is Resource.Error -> stopForeground(true)
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                val errorResource = Resource.Error<String>(e.message ?: "未知错误")
                DownloadSession.updateState(errorResource)
                updateNotification(errorResource.message ?: "未知错误", 0, false)
                stopForeground(true)
            }
        }
    }

    // ... (pauseDownload, cancelDownload, updateNotification, buildNotification, createNotificationChannel, onDestroy 保持不变) ...
    private fun pauseDownload() {
        if (downloadJob?.isActive == true) {
            downloadJob?.cancel()
            downloadJob = null
            updateNotification("下载已暂停", 0, false)
            serviceScope.launch { DownloadSession.updateState(Resource.Error("PAUSED")) }
        }
    }

    private fun cancelDownload() {
        if (downloadJob?.isActive == true) {
            downloadJob?.cancel()
            downloadJob = null
        }
        serviceScope.launch { DownloadSession.updateState(Resource.Error("CANCELED")) }
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
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "视频下载", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        if (downloadJob?.isActive == true) {
            GlobalScope.launch { DownloadSession.updateState(Resource.Error("CANCELED")) }
        }
        super.onDestroy()
        serviceScope.cancel()
        if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}