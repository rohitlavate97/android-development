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

rootProject.name = "enterprise-finance-tracker"

include(":app")
include(":core:common")
include(":core:model")
include(":core:database")
include(":core:network")
include(":core:designsystem")
include(":feature:dashboard")
include(":feature:transactions")
include(":feature:analytics")
