// 一餐的查看、补记忆（05）和小票存档（19）的共享 ViewModel。
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
