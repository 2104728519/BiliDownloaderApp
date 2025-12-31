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
 */
@Composable
fun BiliWebPlayer(bvid: String) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var webView: WebView? = remember { null }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webView?.onResume()
                Lifecycle.Event.ON_PAUSE -> webView?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView?.destroy()
        }
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webView = this

                // 【核心修复】将 WebView 背景设为透明 (0) 或 黑色 (0xFF000000.toInt())
                // 这样在页面加载前，会透出 App 的深色背景，消除白屏闪烁
                setBackgroundColor(0)

                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    // 伪装 UA (保持不变)
                    userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"


                }

                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()

                loadUrl("https://player.bilibili.com/player.html?bvid=$bvid&high_quality=1&danmaku=0")
            }
        },
        onRelease = {
            it.stopLoading()
            it.onPause()
            it.destroy()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
    )
}