// 测试共用的 Fake（Repository、平台能力）和工具。只给各模块的 commonTest 用，不进 App。
plugins {
    alias(libs.plugins.openbite.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core:model"))
            api(project(":shared:core:data"))
            api(project(":shared:core:domain"))
            api(project(":shared:core:network"))
            api(libs.kotlinx.coroutines.test)
            api(libs.ktor.client.mock)
        }
    }
}
