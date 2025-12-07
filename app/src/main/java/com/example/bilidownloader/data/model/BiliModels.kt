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
// 4. 这里的盒子是为了装【播放地址 API】的数据
// 这是我们最终想要的东西！
data class PlayData(
    val dash: DashInfo? // DASH 格式的信息（高画质通常都在这里）
)

data class DashInfo(
    val video: List<MediaInfo>, // 视频流列表（可能有多条，不同画质）
    val audio: List<MediaInfo>? // 音频流列表（可能有多条，不同音质）
)

data class MediaInfo(
    val id: Int,        // 格式ID（比如 80 代表 1080P）
    val baseUrl: String, // 【关键】这就是真正的下载链接！
    val bandwidth: Long  // 带宽，数字越大画质/音质越好
)