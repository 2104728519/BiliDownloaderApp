package com.example.bilidownloader.ui.components

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

@Composable
fun BiliWebPlayer(bvid: String) {
    // 1. 获取生命周期管理者 (它知道 APP 是在前台还是后台)
    val lifecycleOwner = LocalLifecycleOwner.current

    // 2. 记住 WebView 的引用，方便我们在外面控制它
    // mutableStateOf 也可以，这里直接用 remember 一个可变变量简单点
    var webView: WebView? = remember { null }

    // 3. 监听生命周期变化 (解决后台播放问题)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webView?.onResume() // 回到前台：恢复
                Lifecycle.Event.ON_PAUSE -> webView?.onPause()   // 去了后台：暂停
                else -> {}
            }
        }
        // 注册监听
        lifecycleOwner.lifecycle.addObserver(observer)

        // 当这个组件被销毁时 (比如用户退出了页面)，取消监听并销毁 WebView
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            webView?.destroy()
        }
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                // 赋值给外面的变量，这样上面的监听器就能控制它了
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
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    // 伪装成 Android 手机浏览器
                    userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                }

                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()

                loadUrl("https://player.bilibili.com/player.html?bvid=$bvid&high_quality=1&danmaku=0")
            }
        },
        // 加上 onRelease 在 Compose 销毁视图时做双重保险
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