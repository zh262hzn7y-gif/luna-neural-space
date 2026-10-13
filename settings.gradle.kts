pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.kotlin.android" || requested.id.id == "org. jetbrains.kotlin.android" || requested.id.id == "org.gradle.kotlin.android") {
                useModule("org. jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
            }
            if (requested.id.id == "com.android.application") {
                useModule("com.android.tools.build:gradle:8.7.0")
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "luna"
include(":android:app")
