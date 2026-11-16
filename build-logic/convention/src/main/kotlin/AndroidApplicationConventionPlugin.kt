import com.android.build.api.dsl.ApplicationExtension
import com.openbite.buildlogic.OpenBiteVersions
import com.openbite.buildlogic.configureAndroidCommon
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * openbite.android.application：`:androidApp:app`。
 * 统一 SDK / JDK，并开启 Compose。applicationId、版本号、签名留在模块里。
 * AGP 9 自带 Kotlin 编译，不需要再应用 kotlin-android。
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.configure<ApplicationExtension> {
                configureAndroidCommon(this)
                defaultConfig.targetSdk = OpenBiteVersions.TARGET_SDK
                buildFeatures.compose = true
            }
        }
    }
}
