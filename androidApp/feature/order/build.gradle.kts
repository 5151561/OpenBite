// 点单页（03）和“给店员看”小票页（04），Compose。ViewModel 在 :shared:feature:order。
plugins {
    alias(libs.plugins.openbite.android.feature)
}

dependencies {
    implementation(project(":shared:feature:order"))
    implementation(project(":shared:core:domain"))
    // 小票页隐藏系统栏（WindowInsetsControllerCompat）。
    implementation(libs.androidx.core.ktx)
    // 第一次“已下单”时要通知权限。
    implementation(libs.androidx.activity.compose)
}
