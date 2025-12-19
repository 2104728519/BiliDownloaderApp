package com.example.bilidownloader.data.model

// =========================================================
// 1. 基础通用模型
// =========================================================

// B 站所有的回复外面都包着这一层
// T 代表“泛型”，意思就是里面装什么都可以（可能是用户信息，也可能是视频地址）
data class BiliResponse<T>(
    val code: Int,      // 状态码，0 代表成功，其他数字代表出错了
    val message: String?, // 如果出错了，这里会有错误原因
    val data: T?        // 这里才是真正我们要的数据
)

// =========================================================
// 2. Wbi 加密相关模型
// =========================================================

// 这里的盒子是为了装【用户信息 API】的数据
// 我们需要从这里拿到加密用的密钥
data class NavData(
    val wbi_img: WbiImg // 里面包含密钥图片的信息
)

data class WbiImg(
    val img_url: String, // 图片 Key
    val sub_url: String  // 子 Key
)

// =========================================================
// 3. 视频详情相关模型
// =========================================================

// 视频详情数据：标题、封面、作者、分集列表都在这里
data class VideoDetail(
    val bvid: String,
    val aid: Long,
    val title: String, // 标题
    val pic: String,   // 封面图片链接
    val desc: String,  // 简介
    val owner: Owner,  // 作者信息
    val pages: List<PageData> // 分集列表 (CID 藏在这里)
)

data class Owner(
    val mid: Long,
    val name: String, // UP主名字
    val face: String  // UP主头像
)

data class PageData(
    val cid: Long,
    val part: String, // 分集标题
    val page: Int
)

// =========================================================
// 4. 播放地址相关模型 (修改部分)
// =========================================================

data class PlayData(
    val timelength: Long?,              // 视频总时长 (毫秒)
    val accept_quality: List<Int>?,      // 例如: [80, 64, 32, 16]
    val accept_description: List<String>?, // 例如: ["1080P 高清", "720P 高清", ...]
    val dash: DashInfo?,
    val durl: List<DurlInfo>?           // 非 Dash 模式下的视频地址列表
)

data class DurlInfo(
    val url: String,
    val size: Long // 普通 MP4 模式也有 size
)

// 【修改】DashInfo 增加 dolby 和 flac 字段
data class DashInfo(
    val video: List<MediaInfo>,
    val audio: List<MediaInfo>?,
    val dolby: DolbyInfo?, // 👈 新增：杜比全景声
    val flac: FlacInfo?    // 👈 新增：无损 Hi-Res
)

// 【新增】杜比信息
data class DolbyInfo(
    val type: Int,
    val audio: List<MediaInfo>? // 杜比音轨列表
)

// 【新增】FLAC 信息 (注意：根据 API 文档，flac.audio 是对象)
data class FlacInfo(
    val display: Boolean, // 是否显示 FLAC 选项
    val audio: MediaInfo? // FLAC 音轨信息
)

// 视频流或音频流的基础信息
data class MediaInfo(
    val id: Int,         // 画质/音质 ID (例如: 30280, 30250, ...)
    val baseUrl: String, // 实际的下载地址
    val bandwidth: Long, // 码率 (bps)，用于计算体积
    val codecs: String?, // 编码格式，例如 "avc1.64001F" 或 "flac"
    val width: Int?,     // 视频宽度 (仅视频流有)
    val height: Int?     // 视频高度 (仅视频流有)
)