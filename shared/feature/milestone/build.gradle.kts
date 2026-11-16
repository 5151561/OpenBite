// 纪念日页的共享 ViewModel 与 UI 状态。
plugins {
    alias(libs.plugins.openbite.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core:model"))
            implementation(project(":shared:core:domain"))
            api(project(":shared:core:data"))
            api(libs.jetbrains.lifecycle.viewmodel)
            api(libs.kotlinx.coroutines.core)
        }
    }
}
