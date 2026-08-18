pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "expense-tracker-app"

include(":app")
include(":core:model")
include(":core:common")
include(":core:database")
include(":core:network")
include(":core:datastore")
include(":core:designsystem")
include(":feature:dashboard")
include(":feature:transactions")
include(":feature:analytics")
