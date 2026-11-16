// 视觉模型适配层：输入菜单图片，输出结构化菜品流。
// 11/19 起加 Ktor 和 Claude 实现；骨架阶段只定接口。
plugins {
    alias(libs.plugins.openbite.kmp.library)
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
        }
    }
}
