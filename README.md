# BiliDownloader - B站视频下载与音频剪辑工具 📺🎵
（以下内容也是由AI生成）
一个基于 **Jetpack Compose** 和 **Material 3** 设计的现代 Android 应用。
实现了 Bilibili 视频解析、WBI 签名加密、音视频合并下载、历史记录管理以及本地音频裁剪功能。

> ⚠️ **声明**：本项目仅供技术研究和个人学习使用。请勿用于下载版权受保护的内容，严禁用于商业用途。

## ✨ 核心功能

*   **视频解析与下载**
    *   支持解析 `BV` 号及 `b23.tv` 短链接。
    *   集成 **WBI 签名算法**，解决 B 站 API 请求鉴权问题。
    *   支持下载 **1080P 高清视频** (AVC/HEVC) 和 **高音质音频**。
    *   **自动合并**：使用 FFmpeg 自动将分离的视频流和音频流合并为 MP4。
    *   **格式转换**：支持直接提取音频并转码为 MP3 格式。

*   **历史记录管理**
    *   使用 **Room 数据库** 本地持久化存储解析记录。
    *   仿 B 站原生 UI 的历史列表（封面、标题、UP主、时间）。
    *   支持长按进入 **多选模式** 批量删除记录。

*   **音频工作室**
    *   **全盘扫描**：基于 MediaStore 扫描本地所有音频文件（支持 Android 13+ 权限适配）。
    *   **音频裁剪**：可视化的双向滑块操作，支持毫秒级精准裁剪。
    *   **实时试听**：支持裁剪区间的循环预览播放。
    *   **自定义导出**：调用 FFmpeg 进行裁剪并导出到系统音乐库。

*   **现代 UI/UX**
    *   完全基于 **Jetpack Compose** 构建声明式 UI。
    *   遵循 **Material Design 3** 设计规范。
    *   支持 **暗色模式 (Dark Mode)**（跟随系统）。
    *   流畅的动画和状态反馈。

## 🛠️ 技术栈

本项目采用 Android 现代开发推荐架构：

*   **语言**: [Kotlin](https://kotlinlang.org/)
*   **UI 框架**: [Jetpack Compose](https://developer.android.com/jetbrains/compose) (Material 3)
*   **架构模式**: MVVM (Model-View-ViewModel) + Unidirectional Data Flow (UDF)
*   **网络请求**:
    *   [Retrofit2](https://github.com/square/retrofit) + [OkHttp3](https://github.com/square/okhttp)
    *   [Gson](https://github.com/google/gson) (JSON 解析)
*   **多媒体处理**:
    *   [RxFFmpeg](https://github.com/microshow/RxFFmpeg) (基于 FFmpeg 的音视频合并与裁剪)
    *   [Coil](https://github.com/coil-kt/coil) (异步图片加载)
*   **本地存储**:
    *   [Room Database](https://developer.android.com/training/data-storage/room) (SQLite 对象映射)
    *   MediaStore API (Android 分区存储适配)
*   **异步处理**: Kotlin Coroutines + Flow
*   **导航**: Jetpack Navigation Compose


## 📂 项目结构

```text
com.example.bilidownloader
├── data                // 数据层
│   ├── api             // Retrofit API 接口 (Bilibili API)
│   ├── database        // Room 数据库 (Entity, Dao)
│   ├── model           // 数据实体类 (Data Classes)
│   └── repository      // 仓库层 (负责数据分发与业务逻辑)
├── ui                  // 界面层
│   ├── components      // 通用 Compose 组件 (WebPlayer, HistoryItem)
│   ├── screen          // 页面 (HomeScreen, AudioPicker, CropScreen)
│   ├── state           // UI 状态定义 (Sealed Classes)
│   └── viewmodel       // ViewModel (状态管理与逻辑控制)
└── utils               // 工具类
    ├── BiliSigner.kt   // WBI 签名算法核心实现
    ├── FFmpegHelper.kt // FFmpeg 命令封装
    └── StorageHelper.kt// 适配 Android Q+ 的文件存储工具
