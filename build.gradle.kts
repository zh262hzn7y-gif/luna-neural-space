plugins {
    id("com.android.application") version "8.7.0" apply false
    id("org.kotlin.android") version "2.0.20" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
