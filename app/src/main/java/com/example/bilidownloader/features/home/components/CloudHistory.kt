package com.example.bilidownloader.features.home.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import coil.compose.AsyncImage
import com.example.bilidownloader.core.database.UserEntity
import com.example.bilidownloader.core.model.CloudHistoryItem
import com.example.bilidownloader.features.home.HomeViewModel

@Composable
fun CloudHistoryContent(
    viewModel: HomeViewModel,
    currentUser: UserEntity?,
    cloudHistoryList: List<CloudHistoryItem>,
    isCloudHistoryLoading: Boolean,
    cloudHistoryError: String?,
    onLoginClick: () -> Unit
) {
    val cloudListState = rememberLazyListState()

    LaunchedEffect(cloudListState) {
        snapshotFlow { cloudListState.layoutInfo.visibleItemsInfo }
            .collect { visibleItems ->
                if (visibleItems.isNotEmpty() && visibleItems.last().index >= cloudHistoryList.size - 3) {
                    viewModel.loadMoreCloudHistory()
                }
            }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        when {
            currentUser == null -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.PersonOff,
                        null,
                        Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Text("登录后可查看云端播放历史", color = MaterialTheme.colorScheme.outline)
                    Button(onClick = onLoginClick) { Text("添加账号/登录") }
                }
            }

            isCloudHistoryLoading && cloudHistoryList.isEmpty() -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 40.dp)
                )
            }

            cloudHistoryError != null && cloudHistoryList.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("加载失败", color = MaterialTheme.colorScheme.error)
                    Text(cloudHistoryError, color = MaterialTheme.colorScheme.outline)
                    Button(onClick = { viewModel.refreshCloudHistory() }) { Text("重试") }
                }
            }

            else -> {
                LazyColumn(
                    state = cloudListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cloudHistoryList, key = { it.kid }) { item ->
                        CloudHistoryItem(
                            item = item,
                            onClick = { viewModel.analyzeInput(item.bvid) },
                            onLongClick = { }
                        )
                    }
                    if (isCloudHistoryLoading && cloudHistoryList.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CloudHistoryItem(item: CloudHistoryItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    clipboardManager.setText(AnnotatedString(item.bvid))
                    Toast.makeText(context, "BV号已复制: ${item.bvid}", Toast.LENGTH_SHORT).show()
                    onLongClick()
                }
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = item.cover.replace("http://", "https://"),
                contentDescription = "视频封面",
                modifier = Modifier
                    .width(140.dp)
                    .fillMaxHeight(),
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Column {
                    Text(
                        text = "UP: ${item.author_name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = item.viewDateText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}
