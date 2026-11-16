// 记录页（Compose）。ViewModel 在 :shared:feature:journal。
plugins {
    alias(libs.plugins.openbite.android.feature)
}

dependencies {
    implementation(project(":shared:feature:journal"))
    implementation(project(":shared:core:domain"))
}
