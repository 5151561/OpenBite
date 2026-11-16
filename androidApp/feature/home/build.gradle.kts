// 首页（今天）。ViewModel 在 :shared:feature:home。
plugins {
    alias(libs.plugins.openbite.android.feature)
}

dependencies {
    implementation(project(":shared:feature:home"))
    implementation(project(":shared:core:domain"))
}
