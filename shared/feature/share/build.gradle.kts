// 分享这一餐（16）、分享店铺（17）、导入店铺（18）的共享 ViewModel。
plugins {
    alias(libs.plugins.openbite.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core:model"))
            implementation(project(":shared:core:domain"))
            api(libs.jetbrains.lifecycle.viewmodel)
            api(libs.kotlinx.coroutines.core)
        }
    }
}
