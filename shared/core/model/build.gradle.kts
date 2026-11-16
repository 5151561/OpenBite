// 领域模型：纯 Kotlin 数据类，不依赖数据库、网络或界面。
plugins {
    alias(libs.plugins.openbite.kmp.library)
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.core)
            api(libs.kotlinx.datetime)
        }
    }
}
