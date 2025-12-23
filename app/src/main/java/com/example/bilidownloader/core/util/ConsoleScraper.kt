package com.example.bilidownloader.core.util

import com.example.bilidownloader.core.model.ConsoleParams
import com.example.bilidownloader.core.model.ConsoleRequestData
import com.example.bilidownloader.core.model.CornerstoneParam
import com.example.bilidownloader.core.model.LabelFilters
import com.example.bilidownloader.core.model.ReqDTO
import com.example.bilidownloader.core.network.NetworkModule
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * 阿里云控制台数据爬虫工具.
 *
 * 通过模拟用户在浏览器控制台的操作，调用非公开 API 获取模型的使用量统计数据。
 * 需要用户提供 Cookie 和 SecToken。
 */
object ConsoleScraper {

    // 阿里云百炼控制台的相关常量
    private const val WORKSPACE_ID = "llm-icoydhg1hgctj33v"
    private const val RESOURCE_ID = "paraformer-v2"

    /**
     * 获取本月至今的模型总使用时长 (秒).
     *
     * @param cookie 用户登录控制台后的 Cookie.
     * @param secToken 控制台页面中的安全令牌.
     * @return 使用时长 (秒)，若获取失败返回 null.
     */
    suspend fun getTotalUsageInSeconds(cookie: String, secToken: String): Double? = withContext(Dispatchers.IO) {
        try {
            // 1. 计算本月时间范围 (本月1号0点 ~ 当前时间)
            val calendar = Calendar.getInstance()
            val endTime = calendar.timeInMillis

            calendar.set(Calendar.DAY_OF_MONTH, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis

            // 2. 构建监控请求体
            val paramsJson = buildParamsJson(startTime, endTime)

            // 3. 调用控制台内部 API
            val response = NetworkModule.consoleService.getMonitorData(
                cookie = cookie,
                secToken = secToken,
                paramsJson = paramsJson
            )

            // 4. 解析 metric 数据
            if (response.code == "200") {
                val originData = response.data?.dataV2?.data?.data?.originData

                // 查找 "model_total_amount" 且聚合方式为 "cumsum" (累加) 的指标
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