package com.example.bilidownloader.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import com.example.bilidownloader.data.database.HistoryEntity
import com.example.bilidownloader.utils.DateUtils

@OptIn(ExperimentalFoundationApi::class) // 使用长按功能需要这个注解
@Composable
fun HistoryItem(
    history: HistoryEntity,
    isSelectionMode: Boolean, // 是否处于多选模式
    isSelected: Boolean,      // 当前这一项是否被选中
    onClick: () -> Unit,      // 点击事件
    onLongClick: () -> Unit   // 长按事件
) {
    // 外层卡片
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(90.dp) // 固定高度
            .clip(RoundedCornerShape(8.dp))
            // 【关键】组合点击事件：支持点击和长按
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            // 选中时变个颜色，提示用户
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. 左侧封面图
            AsyncImage(
                model = history.coverUrl.replace("http://", "https://"),
                contentDescription = null,
                modifier = Modifier
                    .width(140.dp) // 16:9 比例大概的宽度
                    .fillMaxHeight(),
                contentScale = ContentScale.Crop
            )

            // 2. 中间文字信息
            Column(
                modifier = Modifier
                    .weight(1f) // 占满剩余空间
                    .padding(8.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 标题 (最多显示2行)
                Text(
                    text = history.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // 底部信息
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

            // 3. 右侧复选框 (只有在多选模式下才显示)
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() }, // 点击复选框等同于点击条目
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}