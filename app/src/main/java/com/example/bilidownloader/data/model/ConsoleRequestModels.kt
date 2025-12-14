package com.example.bilidownloader.data.model

// 这个文件用来定义发送请求时的 Body 结构

// 最外层的 params 结构
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
    val workspaceId: String, // "llm-icoydhg1hgctj33v"
    val monitorType: String = "Basic",
    val labelFilters: LabelFilters,
    val startTime: Long, // 动态生成
    val endTime: Long,   // 动态生成
    val stepUnit: String = "DAY",
    val enableAsync: Boolean = true
)

data class LabelFilters(
    val resourceId: String, // "paraformer-v2"
    val resourceType: String = "model",
    val modelCallSource: String = "Online",
    val workspaceId: String
)

// 这个参数基本是固定的，用于控制台自身的一些追踪
data class CornerstoneParam(
    val protocol: String = "V2",
    val console: String = "ONE_CONSOLE",
    val productCode: String = "p_efm",
    val domain: String = "bailian.console.aliyun.com",
    val xsp_lang: String = "zh-CN"
)