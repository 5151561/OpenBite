pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "OpenBite"

// ---- KMP 共享内核：Android 先用，iOS 以后打开 openbite.ios=true ----
include(":shared:core:model")
include(":shared:core:database")
include(":shared:core:network")
include(":shared:core:data")
include(":shared:core:domain")
include(":shared:feature:home")
include(":shared:feature:memories")
include(":shared:feature:milestone")
include(":shared:feature:settings")

// ---- Android 原生界面 ----
include(":androidApp:app")
include(":androidApp:core:designsystem")
include(":androidApp:feature:home")
include(":androidApp:feature:memories")
include(":androidApp:feature:milestone")
include(":androidApp:feature:settings")
