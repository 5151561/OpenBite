// 分享这一餐（16）、分享店铺（17）、导入店铺（18），Compose。ViewModel 在 :shared:feature:share。
plugins {
    alias(libs.plugins.openbite.android.feature)
}

dependencies {
    implementation(project(":shared:feature:share"))
    implementation(project(":shared:core:domain"))
    implementation(libs.androidx.core.ktx)
}
