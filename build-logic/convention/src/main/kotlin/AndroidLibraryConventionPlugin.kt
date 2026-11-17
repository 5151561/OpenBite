import com.android.build.api.dsl.LibraryExtension
import com.openbite.buildlogic.configureAndroidCommon
import com.openbite.buildlogic.defaultNamespace
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** openbite.android.library：只做 Android 库的公共配置（SDK、JDK、按路径生成 namespace）。没有界面的模块（assistant）只用它。 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            extensions.configure<LibraryExtension> {
                configureAndroidCommon(this)
                namespace = defaultNamespace()
            }
        }
    }
}
