// Compose 主题与通用组件，只给 Android 界面用。颜色、字号、圆角取自设计稿的 Material 3 变量。
plugins {
    alias(libs.plugins.openbite.android.feature)
}

dependencies {
    // 通用组件（记忆行、头像）直接吃共享模型。
    api(project(":shared:core:model"))
    // 拍照 / 相册选图（补记忆、小票存档共用）。
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
