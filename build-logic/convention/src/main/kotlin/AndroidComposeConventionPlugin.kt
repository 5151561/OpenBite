import com.android.build.api.dsl.LibraryExtension
import com.openbite.buildlogic.lib
import com.openbite.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** openbite.android.compose：在 Android 库上打开 Compose（编译器插件 + BOM + Material 3）。designsystem 用 library + compose。 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("openbite.android.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            extensions.configure<LibraryExtension> { buildFeatures.compose = true }
            dependencies {
                add("implementation", platform(libs.lib("androidx-compose-bom")))
                add("implementation", libs.lib("androidx-compose-ui"))
                add("implementation", libs.lib("androidx-compose-material3"))
                add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
                // collectAsStateWithLifecycle；也让 lifecycle 各库对齐到版本目录的版本。
                add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
                add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
            }
        }
    }
}
