// 本地数据库：Room KMP，单文件 openbite.db。
// Android 用系统自带 SQLite（不打包 .so）；iOS 打开后用 BundledSQLiteDriver。
// Schema 导出到 schemas/ 并提交进仓库，改表时升版本并写 Migration。
plugins {
    alias(libs.plugins.openbite.kmp.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

kotlin {
    compilerOptions {
        // Room KMP 用 expect object 生成数据库构造器
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.androidx.room.runtime)
            api(libs.kotlinx.coroutines.core)
        }
        if (providers.gradleProperty("openbite.ios").orNull?.toBoolean() == true) {
            iosMain.dependencies {
                implementation(libs.androidx.sqlite.bundled)
            }
        }
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    if (providers.gradleProperty("openbite.ios").orNull?.toBoolean() == true) {
        add("kspIosArm64", libs.androidx.room.compiler)
        add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    }
}
