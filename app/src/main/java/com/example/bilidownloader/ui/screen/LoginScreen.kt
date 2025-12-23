package com.example.bilidownloader.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bilidownloader.ui.components.GeetestWebView
import com.example.bilidownloader.ui.viewmodel.LoginState
import com.example.bilidownloader.ui.viewmodel.LoginViewModel

/**
 * 短信验证码登录页面.
 *
 * 登录流程：
 * 1. 用户输入手机号。
 * 2. 点击获取验证码 -> 触发 `fetchCaptcha` -> 状态变为 `CaptchaRequired`。
 * 3. 弹出全屏 `GeetestWebView`，用户完成滑块/文字点选验证。
 * 4. 验证成功 -> 回调 ViewModel -> 状态变为 `Loading` -> 调用 B 站发送短信 API。
 * 5. 用户输入短信验证码 -> 点击登录 -> 状态变为 `Success` -> 返回主页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val state by viewModel.loginState.collectAsState()
    val timerText by viewModel.timerText.collectAsState()
    val isTimerRunning by viewModel.isTimerRunning.collectAsState()

    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state) {
        if (state is LoginState.Success) {
            snackbarHostState.showSnackbar("登录成功！")
            onBack()
        } else if (state is LoginState.Error) {
            snackbarHostState.showSnackbar((state as LoginState.Error).message)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("短信验证码登录") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(24.dp)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))

                Text("Bilibili 登录", style = MaterialTheme.typography.headlineMedium)
                Text("登录后可获取 1080P+ 高清画质", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)

                Spacer(modifier = Modifier.height(48.dp))

                // 手机号
                OutlinedTextField(
                    value = phone,
                    onValueChange = { if (it.length <= 11) phone = it },
                    label = { Text("手机号") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 验证码
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("验证码") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.fetchCaptcha(phone) },
                        enabled = phone.length == 11 && !isTimerRunning
                    ) {
                        Text(timerText)
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // 登录按钮
                Button(
                    onClick = { viewModel.login(code) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state is LoginState.SmsSent && code.isNotEmpty()
                ) {
                    if (state is LoginState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("登录")
                    }
                }
            }
        }

        // 覆盖在最上层的极验 WebView
        if (state is LoginState.CaptchaRequired) {
            val geetestInfo = (state as LoginState.CaptchaRequired).geetestInfo
            GeetestWebView(
                geetestInfo = geetestInfo,
                onSuccess = { validate, seccode, cookie ->
                    // 将验证结果和 WebView 捕获的 Cookie 一并传回
                    viewModel.onGeetestSuccess(validate, seccode, cookie)
                },
                onError = { msg ->
                    viewModel.onGeetestError(msg)
                },
                onClose = {
                    viewModel.onGeetestClose()
                }
            )
        }
    }
}