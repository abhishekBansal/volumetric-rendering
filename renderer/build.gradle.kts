plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm {
        jvmToolchain(17)
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":core"))
            }
        }
        
        val jvmMain by getting {
            dependencies {
                // JOGL for OpenGL (replaces LWJGL)
                val jogampVersion = "2.6.0"
                implementation("org.jogamp.jogl:jogl-all-main:$jogampVersion")
                implementation("org.jogamp.gluegen:gluegen-rt-main:$jogampVersion")
            }
        }
    }
}
