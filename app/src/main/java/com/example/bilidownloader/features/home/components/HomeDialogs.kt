package com.example.bilidownloader.features.home.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.bilidownloader.core.database.UserEntity

@Composable
fun AccountDialog(
    userList: List<UserEntity>,
    currentUser: UserEntity?,
    onDismiss: () -> Unit,
    onSwitchAccount: (UserEntity) -> Unit,
    onLogout: (UserEntity) -> Unit,
    onManualAdd: () -> Unit,
    onQuitToGuest: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("账号管理", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                if (userList.isEmpty()) {
                    Text("暂无账号，请添加", color = MaterialTheme.colorScheme.secondary)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(userList) { user ->
                            AccountItem(
                                user = user,
                                isCurrent = user.mid == currentUser?.mid,
                                onClick = { if (user.mid != currentUser?.mid) onSwitchAccount(user) },
                                onLongClick = {
                                    var cookieStr = user.sessData.trim()
                                    if (!cookieStr.endsWith(";")) cookieStr += ";"
                                    if (!cookieStr.contains("bili_jct") && user.biliJct.isNotEmpty()) {
                                        cookieStr += " bili_jct=${user.biliJct};"
                                    }
                                    clipboardManager.setText(AnnotatedString(cookieStr))
                                    Toast.makeText(
                                        context,
                                        "完整 Cookie 已复制",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onDelete = { onLogout(user) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onManualAdd) {
                        Icon(Icons.Default.Add, null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("添加账号")
                    }
                    if (currentUser != null) {
                        TextButton(
                            onClick = onQuitToGuest,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("切换游客") }
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) { Text("关闭") }
            }
        }
    }
}

@Composable
fun ManualCookieInputDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onSmsLogin: () -> Unit
) {
    var cookieText by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("添加新账号", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = cookieText,
                    onValueChange = { cookieText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 220.dp),
                    placeholder = { Text("粘贴 SESSDATA=xxx; bili_jct=yyy;") },
                    textStyle = MaterialTheme.typography.bodySmall,
                    minLines = 3
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onSmsLogin) { Text("短信登录") }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Button(
                        onClick = { onConfirm(cookieText) },
                        enabled = cookieText.isNotBlank()
                    ) { Text("添加") }
                }
            }
        }
    }
}
