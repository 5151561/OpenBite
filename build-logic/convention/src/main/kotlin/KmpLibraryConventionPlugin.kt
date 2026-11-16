import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.openbite.buildlogic.OpenBiteVersions
import com.openbite.buildlogic.defaultNamespace
import com.openbite.buildlogic.iosEnabled
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * openbite.kmp.library：`:shared:*` 共享模块。
 * 目标平台：Android（AGP 的 Android KMP 库插件）；openbite.ios=true 时再加 iosArm64 + iosSimulatorArm64。
 * 单元测试写在 commonTest，用 `./gradlew :模块:test` 在 JVM 上跑。
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.multiplatform")
            pluginManager.apply("com.android.kotlin.multiplatform.library")

            extensions.configure<KotlinMultiplatformExtension> {
                jvmToolchain(OpenBiteVersions.JDK_TOOLCHAIN)

                (this as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
                    namespace = defaultNamespace()
                    compileSdk = OpenBiteVersions.COMPILE_SDK
                    minSdk = OpenBiteVersions.MIN_SDK
                    compilerOptions {
                        jvmTarget.set(JvmTarget.fromTarget(OpenBiteVersions.JAVA_TARGET.toString()))
                    }
                    withHostTest {}
                }

                if (iosEnabled) {
                    iosArm64()
                    iosSimulatorArm64()
                }

                sourceSets.getByName("commonTest").dependencies {
                    implementation(kotlin("test"))
                }
            }

            // KMP 模块没有 `test` 任务；补一个，让 `./gradlew test` 也跑共享模块的 JVM 测试。
            tasks.register("test") {
                group = "verification"
                description = "Runs the Android host (JVM) unit tests of this KMP module."
                dependsOn("testAndroidHostTest")
            }
        }
    }
}
