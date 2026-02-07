pluginManagement {
    includeBuild("gradle/build-logic")

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

rootProject.name = "compose-multiplatform-pdf-export"
include(":multiplatform-pdf-export")
include(":sample:shared")
include(":sample:androidApp")
include(":sample:jvmApp")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")