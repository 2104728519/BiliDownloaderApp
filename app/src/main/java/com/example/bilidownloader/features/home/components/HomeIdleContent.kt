package com.example.bilidownloader.features.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bilidownloader.core.database.HistoryEntity
import com.example.bilidownloader.core.database.UserEntity
import com.example.bilidownloader.core.model.CloudHistoryItem
import com.example.bilidownloader.features.home.HistoryTab
import com.example.bilidownloader.features.home.HistoryViewModel

@Composable
fun HomeIdleContent(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    historyTab: HistoryTab,
    historyList: List<HistoryEntity>,
    isSelectionMode: Boolean,
    selectedItems: List<HistoryEntity>,
    historyViewModel: HistoryViewModel,
    currentUser: UserEntity?,
    cloudHistoryList: List<CloudHistoryItem>,
    isCloudHistoryLoading: Boolean,
    cloudHistoryError: String?,
    onAnalyze: () -> Unit,
    onHistoryClick: (HistoryEntity) -> Unit,
    onHistoryLongClick: (HistoryEntity) -> Unit,
    onManualCookieClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = inputText,
            onValueChange = onInputTextChange,
            label = { Text("粘贴 B 站链接或文字") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3, maxLines = 6,
            trailingIcon = {
                if (inputText.isNotEmpty()) IconButton(onClick = {
                    onInputTextChange("")
                }) { Icon(Icons.Default.Close, "清空") }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onAnalyze,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            enabled = inputText.isNotBlank()
        ) { Text("开始解析") }
        Spacer(modifier = Modifier.height(24.dp))
        TabRow(
            selectedTabIndex = historyTab.ordinal,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = historyTab == HistoryTab.Local,
                onClick = {
                    historyViewModel.selectHistoryTab(
                        HistoryTab.Local,
                        currentUser != null
                    )
                },
                text = { Text("本地记录") })
            Tab(
                selected = historyTab == HistoryTab.Cloud,
                onClick = {
                    historyViewModel.selectHistoryTab(
                        HistoryTab.Cloud,
                        currentUser != null
                    )
                },
                text = { Text("账号记录") })
        }
        when (historyTab) {
            HistoryTab.Local -> {
                if (historyList.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(historyList) { history ->
                            HistoryItem(
                                history = history,
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedItems.contains(history),
                                onClick = { onHistoryClick(history) },
                                onLongClick = { onHistoryLongClick(history) }
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "暂无本地解析记录",
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            HistoryTab.Cloud -> {
                CloudHistoryContent(
                    historyViewModel = historyViewModel,
                    currentUser = currentUser,
                    cloudHistoryList = cloudHistoryList,
                    isCloudHistoryLoading = isCloudHistoryLoading,
                    cloudHistoryError = cloudHistoryError,
                    onLoginClick = onManualCookieClick,
                    onItemClick = { bvid -> onInputTextChange(bvid) }
                )
            }
        }
    }
}
