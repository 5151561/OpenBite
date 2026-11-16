// 记忆列表（记忆 Tab）。ViewModel 在 :shared:feature:memories。
plugins {
    alias(libs.plugins.openbite.android.feature)
}

dependencies {
    implementation(project(":shared:feature:memories"))
    implementation(project(":shared:core:domain"))
}
