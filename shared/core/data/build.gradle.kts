// 数据层：Repository、种子数据导入、平台能力接口（相机、定位、密钥、提醒）。
plugins {
    alias(libs.plugins.openbite.kmp.library)
    alias(libs.plugins.kotlin.plugin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core:model"))
            implementation(project(":shared:core:database"))
            api(project(":shared:core:network"))
            implementation(libs.kotlinx.serialization.json)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
    }
}
