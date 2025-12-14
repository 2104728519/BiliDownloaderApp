package com.example.bilidownloader.data.model

import com.google.gson.annotations.SerializedName

// 根响应
data class ConsoleResponse(
    val code: String?,
    val data: ConsoleRootData?
)

data class ConsoleRootData(
    @SerializedName("DataV2")
    val dataV2: ConsoleDataV2?
)

data class ConsoleDataV2(
    val data: ConsoleAsyncTaskData?
)

data class ConsoleAsyncTaskData(
    val data: ConsoleInnerData?
)

data class ConsoleInnerData(
    val originData: List<ConsoleMetricItem>?
)

data class ConsoleMetricItem(
    val metricName: String?, // 例如 "model_call_duration"
    val aggMethod: String?,  // 例如 "sum", "avg"
    val points: List<ConsolePoint>?
)

data class ConsolePoint(
    val timestamp: Long,
    val value: Double
)