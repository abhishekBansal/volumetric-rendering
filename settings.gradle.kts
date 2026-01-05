rootProject.name = "kotlin-volumetric-renderer"

include(":core")
include(":renderer")
include(":desktop")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://www.dcm4che.org/maven2/")
    }
}
