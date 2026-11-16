// 一餐的查看、补记忆（05）、小票存档（19），Compose。ViewModel 在 :shared:feature:meal。
plugins {
    alias(libs.plugins.openbite.android.feature)
}

dependencies {
    implementation(project(":shared:feature:meal"))
    implementation(project(":shared:core:domain"))
    // BackHandler：返回时先确认要不要放弃改动。
    implementation(libs.androidx.activity.compose)
}
