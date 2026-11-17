// 一餐的查看、补记忆（05）、小票存档（19），Compose。ViewModel 在 :shared:feature:meal。
plugins {
    alias(libs.plugins.openbite.android.feature)
}

dependencies {
    implementation(project(":shared:feature:meal"))
    implementation(project(":shared:core:domain"))
    // NavigationBackHandler（navigationevent-compose 随 activity-compose 传递）：有没存的改动时返回先确认。
    implementation(libs.androidx.activity.compose)
}
