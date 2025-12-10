package com.example.bilidownloader.data.model

// 1. 通用的快递外箱
// B 站所有的回复外面都包着这一层
// T 代表“泛型”，意思就是里面装什么都可以（可能是用户信息，也可能是视频地址）
data class BiliResponse<T>(
    val code: Int,      // 状态码，0 代表成功，其他数字代表出错了
    val message: String?, // 如果出错了，这里会有错误原因
    val data: T?        // 这里才是真正我们要的数据
)

// ------------------------------------------------
// 2. 这里的盒子是为了装【用户信息 API】的数据
// 我们需要从这里拿到加密用的密钥
data class NavData(
    val wbi_img: WbiImg // 里面包含密钥图片的信息
)

data class WbiImg(
    val img_url: String, // 图片 Key
    val sub_url: String  // 子 Key
)

// 3. 【新增】视频详情数据 (代替原来的 PageData)
// 这个盒子装的东西比较多：标题、封面、作者、分集列表都在这里
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
    val name: String, // UP主名字
    val face: String  // UP主头像
)

data class PageData(
    val cid: Long,
    val part: String, // 分集标题
    val page: Int
)

// ------------------------------------------------
// 4. 播放地址 (升级版 - 增加字段)
data class PlayData(
    val timelength: Long?,              // <-- 新增此行，用于接收视频总时长(毫秒)
    val accept_quality: List<Int>?,      // 例如: [80, 64, 32, 16]
    val accept_description: List<String>?, // 例如: ["1080P 高清", "720P 高清", ...]
    val dash: DashInfo?,
    val durl: List<DurlInfo>?
)

data class DurlInfo(
    val url: String,
    val size: Long // 普通 MP4 模式也有 size
)

data class DashInfo(
    val video: List<MediaInfo>,
    val audio: List<MediaInfo>?
)

data class MediaInfo(
    val id: Int,        // 画质/音质 ID
    val baseUrl: String,
    val bandwidth: Long, // 码率 (bps)，用于计算体积
    val codecs: String?, // 编码格式，例如 "avc1.64001F" (H264) 或 "hev1.1.6.L120" (H265)
    val width: Int?,    // 视频宽度
    val height: Int?    // 视频高度
)