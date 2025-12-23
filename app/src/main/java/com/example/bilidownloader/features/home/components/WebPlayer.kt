package com.example.bilidownloader.features.home.components

import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * B 站嵌入式 Web 播放器组件.
 *
 * 通过 WebView 加载 B 站官方播放器 H5 页面 (`player.bilibili.com`)。
 * 核心功能：
 * 1. **生命周期绑定**：监听 Compose/Activity 生命周期，实现前后台切换时的自动暂停/恢复播放。
 * 2. **UA 伪装**：模拟 Android 手机浏览器，强制加载 H5 播放器而非 Flash 或 PC 版。
 * 3. **资源释放**：组件销毁时彻底销毁 WebView，防止内存泄漏和后台音频残留。
 */
@Composable
fun BiliWebPlayer(bvid: String) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // 使用 remember 持有 WebView 引用，以便在 DisposableEffect 中访问
    var webView: WebView? = remember { null }

    // 监听生命周期事件 (Resume/Pause)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webView?.onResume() // 恢复视频和音频
                Lifecycle.Event.ON_PAUSE -> webView?.onPause()   // 暂停视频和音频
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView?.destroy() // 彻底销毁，防止后台播放
        }
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webView = this

                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    // 伪装 UA，确保加载移动端 H5 播放器
                    userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                }

                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()

                // 加载 B 站通用播放器页面
                loadUrl("https://player.bilibili.com/player.html?bvid=$bvid&high_quality=1&danmaku=0")
            }
        },
        // 双重保险：Compose 视图节点移除时也执行销毁
        onRelease = {
            it.stopLoading()
            it.onPause()
            it.destroy()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp) // 固定高度，模拟 16:9
    )
}