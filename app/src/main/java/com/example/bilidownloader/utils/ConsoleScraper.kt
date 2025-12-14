package com.example.bilidownloader.utils

import com.example.bilidownloader.data.api.ConsoleApiService
import com.example.bilidownloader.data.model.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Calendar

object ConsoleScraper {

    // 重要的常量，从你的 cURL 中提取
    private const val WORKSPACE_ID = "llm-icoydhg1hgctj33v"
    private const val RESOURCE_ID = "paraformer-v2"

    private val service: ConsoleApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://bailian-cs.console.aliyun.com/")
            .client(OkHttpClient.Builder().build()) // 正式版可以去掉日志拦截器
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ConsoleApiService::class.java)
    }

    /**
     * 获取本月至今的总使用时长（单位：秒）
     * @param cookie 从阿里云控制台抓取的 Cookie
     * @param secToken 从阿里云控制台抓取的 sec_token
     * @return 返回总秒数，如果失败或未找到则返回 null
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
            val response = service.getMonitorData(
                cookie = cookie,
                secToken = secToken,
                paramsJson = paramsJson
            )

            // 4. 解析结果
            if (response.code == "200") {
                val originData = response.data?.dataV2?.data?.data?.originData

                // 找到 "model_total_amount" 且聚合方式为 "cumsum" 的指标
                val targetMetric = originData?.find {
                    it.metricName == "model_total_amount" && it.aggMethod == "cumsum"
                }

                // 返回该指标的第一个点的值
                return@withContext targetMetric?.points?.firstOrNull()?.value
            } else {
                return@withContext null // API 返回错误码
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null // 网络或解析异常
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