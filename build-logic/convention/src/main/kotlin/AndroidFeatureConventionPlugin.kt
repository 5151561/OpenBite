import com.android.build.api.dsl.LibraryExtension
import com.openbite.buildlogic.configureAndroidCommon
import com.openbite.buildlogic.defaultNamespace
import com.openbite.buildlogic.lib
import com.openbite.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * openbite.android.feature：Android 原生界面模块（`:androidApp:feature:*`、`:androidApp:core:designsystem`）。
 *
 * 依赖规则（MAD）：
 * - feature 只放 Compose 页面，ViewModel 在对应的 `:shared:feature:*` 里。
 * - feature 之间互不依赖，跳转由 `:androidApp:app` 负责。
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.configure<LibraryExtension> {
                configureAndroidCommon(this)
                namespace = defaultNamespace()
                buildFeatures.compose = true
            }

            dependencies {
                if (path != ":androidApp:core:designsystem") {
                    add("implementation", project(":androidApp:core:designsystem"))
                }
                add("implementation", platform(libs.lib("androidx-compose-bom")))
                add("implementation", libs.lib("androidx-compose-ui"))
                add("implementation", libs.lib("androidx-compose-material3"))
                add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
                add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
                add("implementation", platform(libs.lib("koin-bom")))
                add("implementation", libs.lib("koin-android"))
                add("implementation", libs.lib("koin-compose-viewmodel"))
                add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
            }
        }
    }
}
