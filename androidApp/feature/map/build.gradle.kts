// 地图与时间胶囊（06），Compose + MapLibre Native。ViewModel 在 :shared:feature:map。
plugins {
    alias(libs.plugins.openbite.android.feature)
}

dependencies {
    implementation(project(":shared:feature:map"))
    implementation(project(":shared:core:domain"))
    implementation(libs.maplibre.android.sdk)
}
