import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.openbite.buildlogic.OpenBiteVersions
import com.openbite.buildlogic.defaultNamespace
import com.openbite.buildlogic.iosEnabled
import com.openbite.buildlogic.lib
import com.openbite.buildlogic.libs
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
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

            // 分层规则：共享 feature 只能依赖 domain 和 model，不能越过 domain 直连 data / database / network。
            if (path.startsWith(":shared:feature:")) {
                // 共享 ViewModel 的 Koin 定义写在各 feature 的 commonMain 里，Android / iOS 共用。
                // 用 koin-core 的 factory：koinViewModel() 按类型取，ViewModel 的生命周期由 ViewModelStore 管。
                extensions.configure<KotlinMultiplatformExtension> {
                    sourceSets.getByName("commonMain").dependencies {
                        implementation(project.dependencies.platform(libs.lib("koin-bom")))
                        implementation(libs.lib("koin-core"))
                    }
                }
                val modulePath = path
                val forbidden = setOf(":shared:core:data", ":shared:core:database", ":shared:core:network")
                configurations.configureEach {
                    val configuration = name
                    dependencies.configureEach {
                        val dep = this
                        if (dep is ProjectDependency && dep.path in forbidden) {
                            throw GradleException("$modulePath 不能依赖 ${dep.path}（$configuration）：feature 只走 :shared:core:domain 的用例。")
                        }
                    }
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
