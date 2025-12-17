package com.example.bilidownloader.core.common

/**
 * 通用资源封装类 (Sealed Class)
 * 用于在 Repository 和 ViewModel 之间传递数据状态
 * @param T 数据载荷的类型
 */
sealed class Resource<T>(val data: T? = null, val message: String? = null) {

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
     * @param data (可选) 预加载数据
     */
    class Loading<T>(data: T? = null) : Resource<T>(data)
}