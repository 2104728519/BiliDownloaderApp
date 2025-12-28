import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    // KSP 插件，用来自动生成数据库代码
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
        versionCode = 16
        versionName = "2.4.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // 把密钥设置成 BuildConfig 的字段
        buildConfigField("String", "OSS_KEY_ID", "\"${localProperties.getProperty("OSS_ACCESS_KEY_ID") ?: ""}\"")
        buildConfigField("String", "OSS_KEY_SECRET", "\"${localProperties.getProperty("OSS_ACCESS_KEY_SECRET") ?: ""}\"")
        buildConfigField("String", "GEMINI_API_KEY", "\"${localProperties.getProperty("GEMINI_API_KEY") ?: ""}\"")
        buildConfigField("String", "DEEPSEEK_API_KEY", "\"${localProperties.getProperty("DEEPSEEK_API_KEY") ?: ""}\"")
        buildConfigField("String", "AL_API_KEY", "\"${localProperties.getProperty("AL_API_KEY") ?: ""}\"")
        ndk {
            abiFilters.add("arm64-v8a")
        }
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
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.12"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // 自定义 APK 文件名逻辑

    applicationVariants.all {
        val variant = this
        variant.outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "bilic_${variant.versionName}.apk"
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
    implementation("androidx.compose.material:material-icons-extended-android:1.6.7")

    // 网络请求
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    //ffmpeg
    implementation(files("libs/ffmpeg-kit-full-gpl-6.0-2.LTS.aar"))
    implementation("com.arthenica:smart-exception-java:0.2.1")
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Room 数据库
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("com.aliyun.dpa:oss-android-sdk:2.9.19")
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}