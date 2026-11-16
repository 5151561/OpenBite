// Android 入口：Application、导航、Koin 装配、平台能力的 Android 实现。
plugins {
    alias(libs.plugins.openbite.android.application)
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
    implementation(project(":androidApp:feature:journal"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
