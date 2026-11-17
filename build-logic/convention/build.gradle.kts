plugins {
    `kotlin-dsl`
}

group = "com.openbite.buildlogic"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// compileOnly：插件本体由根工程的 `apply false` 放进类路径，这里只用来编译。
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.compiler.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "openbite.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "openbite.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "openbite.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "openbite.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "openbite.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
    }
}
