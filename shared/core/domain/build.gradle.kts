// 领域层：用例。界面和系统助手接口（AppFunctions / App Intents）都只调这里。
plugins {
    alias(libs.plugins.openbite.kmp.library)
    // .openbite 店铺文件的 manifest.json。
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core:model"))
            implementation(project(":shared:core:data"))
            // 视觉模型（识别菜单、读小票）。只在用例内部用，不往 feature 传。
            implementation(project(":shared:core:network"))
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
