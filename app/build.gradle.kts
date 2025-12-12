import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    // 【新增】KSP 插件，用来自动生成数据库代码
    id("com.google.devtools.ksp") version "1.9.21-1.0.15"
}

// 读取 local.properties 文件
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}


android {
    namespace = "com.example.bilidownloader"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.bilidownloader"
        minSdk = 26
        targetSdk = 34
        versionCode = 9
        versionName = "1.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true

        }

        // 把密钥设置成 BuildConfig 的字段
        buildConfigField("String", "OSS_KEY_ID", "\"${localProperties.getProperty("OSS_ACCESS_KEY_ID") ?: ""}\"")
        buildConfigField("String", "OSS_KEY_SECRET", "\"${localProperties.getProperty("OSS_ACCESS_KEY_SECRET") ?: ""}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true // 【关键改动】在这里启用 BuildConfig
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.12"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    // 【新增】添加 Material Icons 扩展库
    implementation("androidx.compose.material:material-icons-extended-android:1.6.7")
    // 1. 网络请求三剑客 (Retrofit + OkHttp + Gson)
    // 【解释】Retrofit 是管事的包工头，OkHttp 是底层干苦力的搬砖工，Gson 是负责把数据翻译成 Kotlin 对象的翻译官。
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("com.github.microshow:RxFFmpeg:4.9.0-lite")
    // 图片加载库 Coil
    implementation("io.coil-kt:coil-compose:2.6.0")
    // 数据库 Room
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version") // 支持协程
    ksp("androidx.room:room-compiler:$room_version") // 代码生成器
    // Jetpack Compose 导航库 (负责页面跳转)
    implementation("androidx.navigation:navigation-compose:2.7.7")
    // 阿里云 OSS SDK
    implementation("com.aliyun.dpa:oss-android-sdk:2.9.19")

}