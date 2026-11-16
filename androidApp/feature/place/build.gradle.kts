// 店铺 Tab：店铺列表、店铺详情、电子菜单与原照片（Compose）。ViewModel 在 :shared:feature:place。
plugins {
    alias(libs.plugins.openbite.android.feature)
}

dependencies {
    implementation(project(":shared:feature:place"))
    implementation(project(":shared:core:domain"))
    // 导入店铺：系统文件选择器。
    implementation(libs.androidx.activity.compose)
}
