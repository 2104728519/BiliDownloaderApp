package com.example.bilidownloader.core.common

/**
 * 通用数据状态封装类 (Sealed Class).
 * 用于在 Repository 和 ViewModel 层之间传递数据及其加载状态.
 *
 * @param T 承载的数据类型.
 * @property data 成功获取的数据或部分缓存数据.
 * @property message 错误信息或状态描述.
 */
sealed class Resource<T>(
    val data: T? = null,
    val message: String? = null
) {

    /**
     * 表示操作成功.
     * @param data 必须包含非空数据.
     */
    class Success<T>(data: T) : Resource<T>(data)

    /**
     * 表示操作失败.
     * @param message 错误描述.
     * @param data (可选) 即使失败也可返回旧数据用于 UI 降级展示.
     */
    class Error<T>(message: String, data: T? = null) : Resource<T>(data, message)

    /**
     * 表示正在加载或处理中.
     * @param progress 进度值 (0.0f ~ 1.0f). -1f 表示不确定进度.
     * @param data (可选) 加载过程中的临时数据或提示信息.
     */
    class Loading<T>(
        val progress: Float = -1f,
        data: T? = null
    ) : Resource<T>(data)
}