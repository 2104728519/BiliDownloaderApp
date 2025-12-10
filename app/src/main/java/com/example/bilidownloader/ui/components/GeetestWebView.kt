package com.example.bilidownloader.ui.components

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.bilidownloader.data.model.GeetestInfo
import com.example.bilidownloader.data.api.RetrofitClient // 【新增导入】
import android.os.Handler
import android.os.Looper

// 1. 修改 GeetestWebView 函数签名，增加一个 cookie 参数
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GeetestWebView(
    geetestInfo: GeetestInfo,
    onSuccess: (String, String, String) -> Unit, // <--- 传出 validate, seccode, cookie
    onError: (String) -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(false)
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                        // 【核心修改】第二步：强制 WebView 使用和 Retrofit 一模一样的 User-Agent
                        userAgentString = RetrofitClient.COMMON_USER_AGENT
                        Log.d("GeetestWebView", "设置 UA: $userAgentString") // 打印日志确认
                    }

                    setBackgroundColor(0)

                    // 1. 日志捕获 (依然保留，方便调试)
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            if (consoleMessage != null) {
                                Log.d("GeetestWebView", "[JS Console] ${consoleMessage.message()}")
                            }
                            return true
                        }
                    }

                    // 2. URL 拦截逻辑
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString() ?: return false
                            Log.d("GeetestWebView", "WebView 尝试跳转: $url")

                            // 拦截成功信号
                            if (url.startsWith("jsbridge://success")) {
                                try {
                                    val uri = Uri.parse(url)
                                    val validate = uri.getQueryParameter("validate")
                                    val seccode = uri.getQueryParameter("seccode")

                                    // 【核心新增】从 WebView 获取真实的 Cookie
                                    val cookieManager = android.webkit.CookieManager.getInstance()
                                    // 注意：这里使用 B 站 passport 域名提取 Cookie
                                    val webViewCookie = cookieManager.getCookie("https://passport.bilibili.com") ?: ""

                                    Log.d("GeetestWebView", "成功窃取 WebView Cookie: $webViewCookie")

                                    if (!validate.isNullOrEmpty() && !seccode.isNullOrEmpty()) {
                                        // 切换到主线程调用回调 (保险起见)
                                        post { onSuccess(validate, seccode, webViewCookie) } // <--- 传出 Cookie
                                    }
                                } catch (e: Exception) {
                                    Log.e("GeetestWebView", "解析参数异常", e)
                                }
                                return true // 阻止网页真正跳转
                            }

                            // 拦截错误信号
                            if (url.startsWith("jsbridge://error")) {
                                val uri = Uri.parse(url)
                                val msg = uri.getQueryParameter("msg") ?: "未知错误"
                                Log.e("GeetestWebView", "拦截到错误信号: $msg")
                                post { onError(msg) }
                                return true
                            }

                            // 拦截关闭信号
                            if (url.startsWith("jsbridge://close")) {
                                Log.d("GeetestWebView", "拦截到关闭信号")
                                post { onClose() }
                                return true
                            }

                            return false // 其他链接正常加载
                        }
                    }

                    // 3. 加载 HTML (JS 改为跳转 URL)
                    loadDataWithBaseURL(
                        "https://passport.bilibili.com/",
                        getFullHtml(geetestInfo),
                        "text/html",
                        "utf-8",
                        null
                    )
                }
            }
        )
    }
}

// 保持 getFullHtml 方法不变
private fun getFullHtml(info: GeetestInfo): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <script src="https://static.geetest.com/static/tools/gt.js"></script>
        </head>
        <body>
            <div id="captcha-box"></div>
            <script>
                var checkTimer = setInterval(function() {
                    if (window.initGeetest) {
                        clearInterval(checkTimer);
                        startConfig();
                    }
                }, 100);

                function startConfig() {
                    console.log("JS: 开始初始化...");
                    initGeetest({
                        gt: '${info.gt}',
                        challenge: '${info.challenge}',
                        offline: false,
                        new_captcha: true,
                        product: 'bind', 
                        width: '100%'
                    }, function (captchaObj) {
                        
                        captchaObj.onReady(function () {
                            console.log("JS: Ready, verify");
                            captchaObj.verify();
                        });

                        captchaObj.onSuccess(function () {
                            var result = captchaObj.getValidate();
                            console.log("JS: 验证成功, 准备跳转协议...");
                            
                            // 【核心】不调对象，直接跳链接，参数用 encodeURIComponent 编码
                            var validate = encodeURIComponent(result.geetest_validate);
                            var seccode = encodeURIComponent(result.geetest_seccode);
                            
                            window.location.href = "jsbridge://success?validate=" + validate + "&seccode=" + seccode;
                        });

                        captchaObj.onError(function (e) {
                            console.error("JS Error: " + JSON.stringify(e));
                            window.location.href = "jsbridge://error?msg=" + encodeURIComponent(JSON.stringify(e));
                        });

                        captchaObj.onClose(function () {
                            console.log("JS Close");
                            window.location.href = "jsbridge://close";
                        });
                    });
                }
            </script>
        </body>
        </html>
    """.trimIndent()
}