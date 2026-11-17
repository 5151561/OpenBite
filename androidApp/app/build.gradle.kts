import java.util.Properties

// Android 入口：Application、导航、Koin 装配、平台能力的 Android 实现。
plugins {
    alias(libs.plugins.openbite.android.application)
    // Navigation 3 的路由键要可序列化，才能跨进程重建保存返回栈。
    alias(libs.plugins.kotlin.plugin.serialization)
}

// ---- Release 签名 ----
// 从环境变量或 local.properties（不进 git）读 RELEASE_STORE_FILE / RELEASE_STORE_PASSWORD / RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD。
// 四个都有才用正式签名；缺任何一个就用 debug keystore 签，只能自己装来测，并打警告。密码只传给 AGP，不打印。
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

fun releaseSetting(name: String): String? =
    (providers.environmentVariable(name).orNull ?: localProperties.getProperty(name))?.takeIf { it.isNotBlank() }

val releaseStoreFile = releaseSetting("RELEASE_STORE_FILE")?.let { rootProject.file(it) }
val releaseStorePassword = releaseSetting("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSetting("RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSetting("RELEASE_KEY_PASSWORD")
val hasReleaseSigning = releaseStoreFile?.isFile == true &&
    releaseStorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null
val debugSignedWarning =
    "OpenBite release 用的是 debug keystore 签名，只能装在自己的手机上测试，不能发布。" +
        "要正式签名，请在环境变量或 local.properties 里设置 RELEASE_STORE_FILE、RELEASE_STORE_PASSWORD、RELEASE_KEY_ALIAS、RELEASE_KEY_PASSWORD。"

android {
    namespace = "com.openbite.android"

    defaultConfig {
        applicationId = "com.openbite.android"
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (hasReleaseSigning) "release" else "debug")
            // 只打 arm64-v8a（MapLibre 等 .so 只留一份）。用 abiFilters 而不是 splits：
            // 只出一个 APK，installRelease 直接能装；splits 会对 debug 也生效、出多个 APK。上架时用 AAB，由商店按设备分发。
            ndk {
                abiFilters += "arm64-v8a"
            }
        }
    }
}

if (!hasReleaseSigning) {
    if (gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }) {
        logger.warn("警告：$debugSignedWarning")
    }
    // 配置缓存命中时不会重新配置，上面那行就不会再打印；在任务执行时再提醒一次。
    tasks.matching { it.name == "assembleRelease" || it.name == "installRelease" }.configureEach {
        val message = debugSignedWarning
        doLast { logger.warn("警告：$message") }
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
    implementation(project(":androidApp:feature:meal"))
    implementation(project(":androidApp:feature:map"))
    implementation(project(":androidApp:feature:share"))
    implementation(project(":androidApp:feature:search"))
    implementation(project(":androidApp:feature:order"))
    implementation(project(":androidApp:feature:place"))
    implementation(project(":androidApp:feature:scan"))
    implementation(project(":androidApp:feature:settings"))
    // 共享 ViewModel 的 Koin 模块（各 :shared:feature:* 的 commonMain），startKoin 时引入。
    implementation(project(":shared:feature:home"))
    implementation(project(":shared:feature:memories"))
    implementation(project(":shared:feature:milestone"))
    implementation(project(":shared:feature:meal"))
    implementation(project(":shared:feature:map"))
    implementation(project(":shared:feature:share"))
    implementation(project(":shared:feature:search"))
    implementation(project(":shared:feature:order"))
    implementation(project(":shared:feature:place"))
    implementation(project(":shared:feature:scan"))
    implementation(project(":shared:feature:settings"))
    // 系统助手接口（AppFunctions）：Service 和元数据随清单合并进来，App 代码不直接调用。
    implementation(project(":androidApp:assistant"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.json)
    implementation(project(":shared:core:network"))
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
