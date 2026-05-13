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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
fun BiliWebPlayer(bvid: String, page: Int = 1) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webViewInstance?.onResume()
                Lifecycle.Event.ON_PAUSE -> webViewInstance?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webViewInstance?.destroy()
        }
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewInstance = this

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
                    userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                }

                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
            }
        },
        update = { view ->
            val targetUrl =
                "https://player.bilibili.com/player.html?bvid=$bvid&p=$page&high_quality=1&danmaku=0"
            // 只有当 URL 真正改变时才 loadUrl，避免由于重组导致的重复加载
            if (view.url != targetUrl && !view.url.isNullOrEmpty()) {
                view.loadUrl(targetUrl)
            } else if (view.url.isNullOrEmpty()) {
                view.loadUrl(targetUrl)
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