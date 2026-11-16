// 识别菜单页（Compose）：拍照 / 相册、权限、流式显示。ViewModel 在 :shared:feature:scan。
plugins {
    alias(libs.plugins.openbite.android.feature)
}

dependencies {
    implementation(project(":shared:feature:scan"))
    implementation(project(":shared:core:domain"))
    // TakePicture / PickVisualMedia、FileProvider、权限检查。
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
