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
include(":shared:core:testing")
include(":shared:feature:home")
include(":shared:feature:map")
include(":shared:feature:meal")
include(":shared:feature:memories")
include(":shared:feature:milestone")
include(":shared:feature:order")
include(":shared:feature:place")
include(":shared:feature:scan")
include(":shared:feature:search")
include(":shared:feature:settings")
include(":shared:feature:share")

// ---- Android 原生界面 ----
include(":androidApp:app")
include(":androidApp:assistant")
include(":androidApp:core:designsystem")
include(":androidApp:feature:home")
include(":androidApp:feature:map")
include(":androidApp:feature:meal")
include(":androidApp:feature:memories")
include(":androidApp:feature:milestone")
include(":androidApp:feature:order")
include(":androidApp:feature:place")
include(":androidApp:feature:scan")
include(":androidApp:feature:search")
include(":androidApp:feature:settings")
include(":androidApp:feature:share")
