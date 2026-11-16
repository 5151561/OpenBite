// 记忆搜索、店铺搜索的共享 ViewModel（输入防抖）。
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
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
