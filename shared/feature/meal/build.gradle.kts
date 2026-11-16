// 一餐的查看、补记忆（05）和小票存档（19）的共享 ViewModel。
plugins {
    alias(libs.plugins.openbite.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:core:model"))
            implementation(project(":shared:core:domain"))
            // 编辑结果（MealEdit、NewPhoto）和读出来的小票（ScannedReceipt）直接用下层的类型。
            api(project(":shared:core:data"))
            api(project(":shared:core:network"))
            api(libs.jetbrains.lifecycle.viewmodel)
            api(libs.kotlinx.coroutines.core)
        }
    }
}
