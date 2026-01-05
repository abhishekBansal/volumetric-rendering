import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

kotlin {
    jvm {
        jvmToolchain(17)
        withJava()
    }
    
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":core"))
                implementation(project(":renderer"))
                
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                
                // JOGL for OpenGL (replaces LWJGL)
                val jogampVersion = "2.6.0"
                implementation("org.jogamp.jogl:jogl-all-main:$jogampVersion")
                implementation("org.jogamp.gluegen:gluegen-rt-main:$jogampVersion")
                
                // Coroutines for async operations
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.volumetric.renderer.desktop.MainKt"
        
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "VolumetricRenderer"
            packageVersion = "1.0.0"
            
            macOS {
                iconFile.set(project.file("icon.icns"))
            }
            windows {
                iconFile.set(project.file("icon.ico"))
            }
            linux {
                iconFile.set(project.file("icon.png"))
            }
        }
        
        jvmArgs += listOf(
            "-Xmx4g",
            "-XX:+UseG1GC"
            // Note: -XstartOnFirstThread removed - GLFW runs in background thread
        )
    }
}
