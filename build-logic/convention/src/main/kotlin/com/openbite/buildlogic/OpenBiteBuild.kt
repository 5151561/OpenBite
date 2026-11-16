package com.openbite.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType

/** 全工程统一的 SDK / JDK 版本。targetSdk 36 + compileSdk 37 是 AppFunctions 的要求。 */
internal object OpenBiteVersions {
    const val COMPILE_SDK = 37
    const val MIN_SDK = 26
    const val TARGET_SDK = 36
    const val JDK_TOOLCHAIN = 21
    val JAVA_TARGET = JavaVersion.VERSION_17
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String) = findLibrary(alias).get()

/** 是否编译 iOS 目标（gradle.properties 里的 openbite.ios）。 */
internal val Project.iosEnabled: Boolean
    get() = providers.gradleProperty("openbite.ios").orNull?.toBoolean() == true

/**
 * 按模块路径生成 namespace：
 * `:shared:core:model` -> `com.openbite.core.model`，
 * `:androidApp:feature:journal` -> `com.openbite.android.feature.journal`。
 */
internal fun Project.defaultNamespace(): String = when {
    path.startsWith(":shared:") -> "com.openbite" + path.removePrefix(":shared").replace(':', '.')
    path.startsWith(":androidApp:") -> "com.openbite.android" + path.removePrefix(":androidApp").replace(':', '.')
    else -> "com.openbite" + path.replace(':', '.')
}

internal fun Project.configureAndroidCommon(android: CommonExtension) {
    android.compileSdk = OpenBiteVersions.COMPILE_SDK
    android.defaultConfig.minSdk = OpenBiteVersions.MIN_SDK
    android.compileOptions.sourceCompatibility = OpenBiteVersions.JAVA_TARGET
    android.compileOptions.targetCompatibility = OpenBiteVersions.JAVA_TARGET
    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(OpenBiteVersions.JDK_TOOLCHAIN))
    }
}
