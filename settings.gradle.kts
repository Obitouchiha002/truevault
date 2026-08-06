pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "TrueVault"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

include(":core:model")
include(":core:capabilities")
include(":core:common")
include(":core:data")
include(":core:designsystem")
include(":core:database")
include(":core:datastore")
include(":core:legal")
include(":core:notes")
include(":core:crypto")
include(":core:storage")
include(":core:testing")

include(":feature:onboarding")
include(":feature:authentication")
include(":feature:home")
include(":feature:launcher")
include(":feature:vault")
include(":feature:importfiles")
include(":feature:scanner")
include(":feature:privateapps")
include(":feature:settings")
include(":feature:backup")
include(":feature:legal")
include(":feature:notes")
