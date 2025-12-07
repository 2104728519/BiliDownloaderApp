pluginManagement {


    repositories {
        // 1. 优先去阿里云找工具（速度快）
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }

        // 2. 如果阿里云没有，再去国外的原厂找（作为备胎，防止找不到特定的版本）
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    // 这里是告诉 Android Studio 去哪里下载“RxFFmpeg、Retrofit”这些库
    repositories {
        // 1. 优先去阿里云
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }

        // 2. 专门为了 RxFFmpeg 加的仓库
        maven { url = uri("https://www.jitpack.io") }

        google()
        mavenCentral()


    }
}

// 你的项目名字
rootProject.name = "BiliDownloader"
include(":app")