plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.lightledger.app"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.lightledger.app"
        // 兼容 Android 10 及以上
        minSdk = 29
        targetSdk = 35
        versionCode = 25
        versionName = "1.8.2"
        vectorDrawables { useSupportLibrary = true }
        // APK 瘦身：仅保留中英资源
        resourceConfigurations += listOf("zh", "en")
    }

    // APK 输出文件名自动带版本号，如 lalaledger-v1.1.0.apk
    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "lalaledger-v${versionName}.apk"
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("../keystore/lalaledger.jks")
            storePassword = "lalaledger2026"
            keyAlias = "lala"
            keyPassword = "lalaledger2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        // Release 构建自带的 lintVital 检查在部分 Gradle/AGP 组合下有依赖冲突
        // （io.opentelemetry.api.trace.Tracer 缺失），本地分发包无需该检查，关闭之
        checkReleaseBuilds = false
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    // Room schema 导出目录：版本迁移时对照历史 schema 校验
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore（主题模式、当前账本等轻量设置）
    implementation(libs.androidx.datastore.preferences)

    // 图片加载（本地文件缩略图）
    implementation(libs.coil.compose)

    implementation(libs.kotlinx.coroutines.android)
}
