package com.example.bilidownloader.core.common

/**
 * 通用资源封装类 (Sealed Class)
 * 用于在 Repository 和 ViewModel 之间传递数据状态
 * @param T 数据载荷的类型
 */
sealed class Resource<T>(
    val data: T? = null,
    val message: String? = null
) {

    /**
     * 成功状态
     * @param data 获取到的数据
     */
    class Success<T>(data: T) : Resource<T>(data)

    /**
     * 错误状态
     * @param message 错误描述信息
     * @param data (可选) 即使出错也可以返回旧数据用于缓存显示
     */
    class Error<T>(message: String, data: T? = null) : Resource<T>(data, message)

    /**
     * 加载中状态
     * @param progress 进度值 (0.0f ~ 1.0f)，-1f 代表不确定进度
     * @param data (可选) 附加信息，比如 "正在下载视频..."
     */
    class Loading<T>(
        val progress: Float = -1f,
        data: T? = null
    ) : Resource<T>(data)
}
