package com.example.bilidownloader.data.model

// region 1. Generic Response Wrapper (通用响应壳)

/**
 * B 站 API 通用响应泛型类.
 * @param code 0 表示成功，其他值为错误码.
 * @param data 具体业务数据.
 */
data class BiliResponse<T>(
    val code: Int,
    val message: String?,
    val data: T?
)

// endregion

// region 2. WBI Keys (密钥数据)

data class NavData(
    val wbi_img: WbiImg
)

data class WbiImg(
    val img_url: String, // 混淆密钥 A
    val sub_url: String  // 混淆密钥 B
)

// endregion

// region 3. Video Detail (视频详情)

data class VideoDetail(
    val bvid: String,
    val aid: Long,
    val title: String,
    val pic: String,
    val desc: String,
    val owner: Owner,
    val pages: List<PageData> // 分P信息，用于获取 cid
)

data class Owner(
    val mid: Long,
    val name: String,
    val face: String
)

data class PageData(
    val cid: Long,
    val part: String,
    val page: Int
)

// endregion

// region 4. Play URL & DASH Info (播放地址与流媒体)

/**
 * 视频流媒体信息.
 * 包含 DASH 格式的音视频流以及备用的 durl (MP4) 格式.
 */
data class PlayData(
    val timelength: Long?,
    val accept_quality: List<Int>?,      // 支持的画质 ID 列表
    val accept_description: List<String>?, // 画质描述 (如 "1080P 高清")
    val dash: DashInfo?,
    val durl: List<DurlInfo>?           // 传统 MP4 直链 (非 DASH)
)

data class DurlInfo(
    val url: String,
    val size: Long
)

data class DashInfo(
    val video: List<MediaInfo>,
    val audio: List<MediaInfo>?,
    val dolby: DolbyInfo?, // 杜比全景声
    val flac: FlacInfo?    // Hi-Res 无损音频
)

data class DolbyInfo(
    val type: Int,
    val audio: List<MediaInfo>?
)

data class FlacInfo(
    val display: Boolean,
    val audio: MediaInfo?
)

/**
 * 基础媒体流信息 (视频轨或音频轨).
 */
data class MediaInfo(
    val id: Int,         // 质量 ID (30280=192k音频, 80=1080P视频等)
    val baseUrl: String, // 下载链接
    val bandwidth: Long, // 比特率
    val codecs: String?, // 编码格式 (avc1, hev1, flac...)
    val width: Int?,
    val height: Int?
)

// endregion