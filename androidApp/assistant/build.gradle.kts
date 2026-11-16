// 系统助手接口（Android 16+ AppFunctions）。只调用 :shared:core:domain 里的 AssistantApi，不碰数据库和文件。
// 用 feature 约定插件拿到统一的 Android 配置、命名空间和 Koin；这里没有界面。
plugins {
    alias(libs.plugins.openbite.android.feature)
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(project(":shared:core:domain"))
    implementation(project(":shared:core:model"))
    implementation(libs.androidx.appfunctions)
    ksp(libs.androidx.appfunctions.compiler)
}
