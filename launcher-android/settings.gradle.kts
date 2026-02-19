pluginManagement {
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

rootProject.name = "luna-launcher-android"

include(":app")
include(":core-model")
include(":data")
include(":apps-android")
include(":ui-home")
include(":ui-search")
include(":ui-appmenu")

