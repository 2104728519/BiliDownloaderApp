package com.example.bilidownloader.utils

import com.example.bilidownloader.core.network.NetworkModule // <--- 1. 引入 NetworkModule
import com.example.bilidownloader.data.model.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

object ConsoleScraper {

    // 重要的常量，从你的 cURL 中提取
    private const val WORKSPACE_ID = "llm-icoydhg1hgctj33v"
    private const val RESOURCE_ID = "paraformer-v2"


    /**
     * 获取本月至今的总使用时长（单位：秒）
     */
    suspend fun getTotalUsageInSeconds(cookie: String, secToken: String): Double? = withContext(Dispatchers.IO) {
        try {
            // 1. 动态计算时间戳
            val calendar = Calendar.getInstance()
            val endTime = calendar.timeInMillis // 当前时间

            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis // 本月第一天零点

            // 2. 构建请求体
            val paramsJson = buildParamsJson(startTime, endTime)

            // 3. 发起请求
            // 这里调用 NetworkModule.consoleService
            val response = NetworkModule.consoleService.getMonitorData(
                cookie = cookie,
                secToken = secToken,
                paramsJson = paramsJson
            )

            // 4. 解析结果
            if (response.code == "200") {
                val originData = response.data?.dataV2?.data?.data?.originData

                val targetMetric = originData?.find {
                    it.metricName == "model_total_amount" && it.aggMethod == "cumsum"
                }

                return@withContext targetMetric?.points?.firstOrNull()?.value
            } else {
                return@withContext null
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    private fun buildParamsJson(startTime: Long, endTime: Long): String {
        val params = ConsoleParams(
            Data = ConsoleRequestData(
                reqDTO = ReqDTO(
                    workspaceId = WORKSPACE_ID,
                    labelFilters = LabelFilters(
                        resourceId = RESOURCE_ID,
                        workspaceId = WORKSPACE_ID
                    ),
                    startTime = startTime,
                    endTime = endTime
                ),
                cornerstoneParam = CornerstoneParam()
            )
        )
        return Gson().toJson(params)
    }
}