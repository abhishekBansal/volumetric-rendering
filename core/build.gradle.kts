plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm {
        jvmToolchain(17)
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
            }
        }
        
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        
        val jvmMain by getting {
            dependencies {
                // DICOM support
                implementation("org.dcm4che:dcm4che-core:5.29.2")
                implementation("org.dcm4che:dcm4che-imageio:5.29.2")
                implementation("org.dcm4che:dcm4che-image:5.29.2")
            }
        }
    }
}
