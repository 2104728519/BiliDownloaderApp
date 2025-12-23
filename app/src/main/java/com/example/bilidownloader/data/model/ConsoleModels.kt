package com.example.bilidownloader.data.model

import com.google.gson.annotations.SerializedName

/**
 * 阿里云控制台监控数据响应结构.
 * 结构层级较深，对应控制台私有接口的 JSON 格式.
 */
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
    val metricName: String?, // 监控指标名称 (如 "model_total_amount")
    val aggMethod: String?,  // 聚合方式 (如 "cumsum")
    val points: List<ConsolePoint>?
)

data class ConsolePoint(
    val timestamp: Long,
    val value: Double // 具体数值
)