// 视觉模型适配层：输入菜单图片，输出结构化菜品流。
// Claude 走 Anthropic Messages API（SSE 流式）；“示例菜单”在本地流式吐出打包好的菜单，不联网。
// HTTP 引擎由平台注入（Android 用 OkHttp），这里只依赖 ktor-client-core。
plugins {
    alias(libs.plugins.openbite.kmp.library)
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core:model"))
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            // 只在测试里用：MockEngine 回放录好的 SSE 流，测试不联网。
            implementation(libs.ktor.client.mock)
        }
    }
}
