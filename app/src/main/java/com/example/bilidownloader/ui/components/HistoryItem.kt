package com.example.bilidownloader.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.bilidownloader.core.database.HistoryEntity
import com.example.bilidownloader.core.util.DateUtils

/**
 * 历史记录列表项组件.
 *
 * 支持多选模式：
 * - **普通模式**：点击跳转详情。
 * - **选择模式**：点击选中/取消选中，显示复选框。
 * 使用 `combinedClickable` 同时处理点击和长按事件。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryItem(
    history: HistoryEntity,
    isSelectionMode: Boolean, // 当前是否处于多选状态
    isSelected: Boolean,      // 当前条目是否被选中
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(90.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            // 选中时高亮显示背景色
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. 封面图
            AsyncImage(
                model = history.coverUrl.replace("http://", "https://"), // 强制 HTTPS
                contentDescription = null,
                modifier = Modifier
                    .width(140.dp)
                    .fillMaxHeight(),
                contentScale = ContentScale.Crop
            )

            // 2. 信息区域
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 标题 (限制 2 行，超长省略)
                Text(
                    text = history.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // 底部元数据
                Column {
                    Text(
                        text = "UP: ${history.uploader}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                    Text(
                        text = DateUtils.format(history.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.LightGray
                    )
                }
            }

            // 3. 多选复选框 (仅在多选模式下显示)
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() }, // 代理点击事件
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}