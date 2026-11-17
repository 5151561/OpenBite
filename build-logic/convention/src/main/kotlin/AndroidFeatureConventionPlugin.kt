import com.openbite.buildlogic.lib
import com.openbite.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * openbite.android.feature：Android 原生界面模块（`:androidApp:feature:*`）= library + compose + designsystem + Koin。
 *
 * 依赖规则（MAD）：
 * - feature 只放 Compose 页面，ViewModel 和它们的 Koin 定义在对应的 `:shared:feature:*` 里。
 * - feature 之间互不依赖，跳转由 `:androidApp:app` 负责。
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("openbite.android.compose")
            dependencies {
                add("implementation", project(":androidApp:core:designsystem"))
                add("implementation", platform(libs.lib("koin-bom")))
                add("implementation", libs.lib("koin-android"))
                add("implementation", libs.lib("koin-compose-viewmodel"))
            }
        }
    }
}
