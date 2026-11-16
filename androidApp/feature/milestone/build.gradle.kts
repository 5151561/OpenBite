// 纪念日（纪念日 Tab）。ViewModel 在 :shared:feature:milestone。
plugins {
    alias(libs.plugins.openbite.android.feature)
}

dependencies {
    implementation(project(":shared:feature:milestone"))
    implementation(project(":shared:core:domain"))
}
