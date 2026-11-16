// 设置页。ViewModel 在 :shared:feature:settings。
plugins {
    alias(libs.plugins.openbite.android.feature)
}

dependencies {
    implementation(project(":shared:feature:settings"))
    implementation(project(":shared:core:domain"))
}
