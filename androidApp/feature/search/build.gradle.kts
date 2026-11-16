// 搜索页（记忆 / 店铺），Compose。ViewModel 在 :shared:feature:search。
plugins {
    alias(libs.plugins.openbite.android.feature)
}

dependencies {
    implementation(project(":shared:feature:search"))
    implementation(project(":shared:core:domain"))
}
