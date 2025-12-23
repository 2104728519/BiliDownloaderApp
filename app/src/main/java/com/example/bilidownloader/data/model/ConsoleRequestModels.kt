package com.example.bilidownloader.data.model

/**
 * 阿里云控制台监控请求参数结构.
 * 用于构造复杂的 JSON 参数字符串以模拟前端请求.
 */
data class ConsoleParams(
    val Api: String = "zeldaEasy.bailian-telemetry.monitor.getMonitorDataWithOss",
    val V: String = "1.0",
    val Data: ConsoleRequestData
)

data class ConsoleRequestData(
    val reqDTO: ReqDTO,
    val cornerstoneParam: CornerstoneParam
)

data class ReqDTO(
    val workspaceId: String,
    val monitorType: String = "Basic",
    val labelFilters: LabelFilters,
    val startTime: Long,
    val endTime: Long,
    val stepUnit: String = "DAY",
    val enableAsync: Boolean = true
)

data class LabelFilters(
    val resourceId: String,
    val resourceType: String = "model",
    val modelCallSource: String = "Online",
    val workspaceId: String
)

data class CornerstoneParam(
    val protocol: String = "V2",
    val console: String = "ONE_CONSOLE",
    val productCode: String = "p_efm",
    val domain: String = "bailian.console.aliyun.com",
    val xsp_lang: String = "zh-CN"
)