package com.example.bilidownloader.features.login

import android.annotation.SuppressLint
import android.net.Uri
import android.util.Log
import android.webkit.ConsoleMessage
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
import com.example.bilidownloader.core.common.Constants

/**
 * 极验验证码 WebView 组件.
 *
 * 核心功能：
 * 1. **加载本地 HTML**：注入极验 JS SDK (`gt.js`) 并初始化验证码。
 * 2. **User-Agent 伪装**：强制使用与 Retrofit 请求头一致的 UA，确保风控环境一致。
 * 3. **JSBridge 通信**：通过拦截 `jsbridge://` 协议的 URL 跳转，实现 JS 向 Kotlin 传递验证结果 (validate, seccode)。
 * 4. **Cookie 窃取/同步**：关键步骤 —— 从 WebView 的 CookieManager 中提取 B 站下发的设备指纹 (buvid) 和会话 Cookie，同步到 App 的网络层。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GeetestWebView(
    geetestInfo: GeetestInfo,
    onSuccess: (String, String, String) -> Unit, // Callback: validate, seccode, cookie
    onError: (String) -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)) // 半透明背景遮罩
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

                        // 【关键】必须与 BiliHeaderInterceptor 保持一致，否则验证通过但登录接口会报 -400
                        userAgentString = Constants.COMMON_USER_AGENT
                        Log.d("GeetestWebView", "设置 UA: $userAgentString")
                    }

                    setBackgroundColor(0)

                    // WebChromeClient: 捕获 JS console.log 用于调试
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            if (consoleMessage != null) {
                                Log.d("GeetestWebView", "[JS Console] ${consoleMessage.message()}")
                            }
                            return true
                        }
                    }

                    // WebViewClient: 拦截自定义协议跳转
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString() ?: return false
                            Log.d("GeetestWebView", "WebView 尝试跳转: $url")

                            // 拦截 JS 发出的成功信号
                            if (url.startsWith("jsbridge://success")) {
                                try {
                                    val uri = Uri.parse(url)
                                    val validate = uri.getQueryParameter("validate")
                                    val seccode = uri.getQueryParameter("seccode")

                                    // 【核心逻辑】提取 WebView 内部生成的 Cookie (包含关键的 buvid3)
                                    val cookieManager = android.webkit.CookieManager.getInstance()
                                    val webViewCookie = cookieManager.getCookie("https://passport.bilibili.com") ?: ""

                                    Log.d("GeetestWebView", "成功窃取 WebView Cookie: $webViewCookie")

                                    if (!validate.isNullOrEmpty() && !seccode.isNullOrEmpty()) {
                                        post { onSuccess(validate, seccode, webViewCookie) }
                                    }
                                } catch (e: Exception) {
                                    Log.e("GeetestWebView", "解析参数异常", e)
                                }
                                return true // 阻止跳转，由 Native 处理
                            }

                            // 拦截错误信号
                            if (url.startsWith("jsbridge://error")) {
                                val uri = Uri.parse(url)
                                val msg = uri.getQueryParameter("msg") ?: "未知错误"
                                post { onError(msg) }
                                return true
                            }

                            // 拦截关闭信号
                            if (url.startsWith("jsbridge://close")) {
                                post { onClose() }
                                return true
                            }

                            return false // 其他链接正常加载
                        }
                    }

                    // 加载构造好的 HTML，BaseURL 指向 B 站 Passport 域以解决跨域问题
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

/**
 * 构造包含极验 SDK 的 HTML 页面.
 * 使用 JS 代码初始化极验，并定义回调函数将结果通过 window.location.href 传出。
 */
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
                // 轮询等待 initGeetest 函数加载完成
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
                            
                            // 对参数进行 URL 编码防止特殊字符截断
                            var validate = encodeURIComponent(result.geetest_validate);
                            var seccode = encodeURIComponent(result.geetest_seccode);
                            
                            // 触发 Native 拦截
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