// Android 入口：Application、导航、Koin 装配、平台能力的 Android 实现。
plugins {
    alias(libs.plugins.openbite.android.application)
    // Navigation 3 的路由键要可序列化，才能跨进程重建保存返回栈。
    alias(libs.plugins.kotlin.plugin.serialization)
}

android {
    namespace = "com.openbite.android"

    defaultConfig {
        applicationId = "com.openbite.android"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":shared:core:model"))
    implementation(project(":shared:core:database"))
    implementation(project(":shared:core:data"))
    implementation(project(":shared:core:domain"))
    implementation(project(":androidApp:core:designsystem"))
    implementation(project(":androidApp:feature:home"))
    implementation(project(":androidApp:feature:memories"))
    implementation(project(":androidApp:feature:milestone"))
    implementation(project(":androidApp:feature:place"))
    implementation(project(":androidApp:feature:scan"))
    implementation(project(":androidApp:feature:settings"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.core)
    implementation(project(":shared:core:network"))
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.exifinterface)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
