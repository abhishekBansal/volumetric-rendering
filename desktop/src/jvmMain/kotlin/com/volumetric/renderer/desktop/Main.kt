package com.volumetric.renderer.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.io.File

import com.volumetric.renderer.desktop.ui.ControlPanel

fun main(args: Array<String>) {
    println("=== VOLUMETRIC RENDERER WITH JOGL + COMPOSE DESKTOP ===")
    println("Controls:")
    println("  Mouse drag: Rotate camera")
    println("  Scroll: Zoom in/out")
    println("  WASD: Move camera")
    println("  P: Cycle transfer function")
    println()
    
    // Find project root (where settings.gradle.kts is)
    fun findProjectRoot(): File {
        var dir = File(System.getProperty("user.dir"))
        while (dir.parentFile != null) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile
        }
        return File(System.getProperty("user.dir"))
    }
    
    val projectRoot = findProjectRoot()
    println("Project root: ${projectRoot.absolutePath}")
    
    // Check for DICOM file/directory argument
    val dicomPath = args.firstOrNull()?.let { path ->
        val file = File(path)
        when {
            file.isAbsolute && file.exists() -> path
            File(projectRoot, path).exists() -> File(projectRoot, path).absolutePath
            file.exists() -> file.absolutePath
            else -> {
                println("Warning: File not found: $path")
                null
            }
        }
    }
    
    if (dicomPath != null) {
        println("Will load medical data from: $dicomPath")
    } else {
        println("Usage: Run with DICOM/NIfTI file/directory path as argument")
        println("Example: ./gradlew desktop:run --args=\"data/Task02_Heart/imagesTr/la_003.nii.gz\"")
        println()
        
        // List available data files
        val dataDir = File(projectRoot, "data")
        if (dataDir.exists()) {
            println("Available data in ${dataDir.absolutePath}:")
            dataDir.listFiles()?.filter { it.isDirectory }?.take(5)?.forEach { dir ->
                val images = File(dir, "imagesTr")
                if (images.exists()) {
                    val niftiFiles = images.listFiles()?.filter { it.name.endsWith(".nii.gz") }?.take(2)
                    niftiFiles?.forEach { println("  data/${dir.name}/imagesTr/${it.name}") }
                }
            }
        }
    }
    
    // Create renderer (will be initialized when GLJPanel is created)
    val renderer = JOGLVolumeRenderer(dicomPath)
    
    // Start Compose Desktop application
    application {
        Window(
            onCloseRequest = { exitApplication() },
            title = "Volumetric Renderer - JOGL + Compose",
            state = rememberWindowState(width = 1400.dp, height = 900.dp)
        ) {
            MaterialTheme(
                colors = darkColors(
                    primary = Color(0xFF2196F3),
                    secondary = Color(0xFF03DAC6),
                    surface = Color(0xFF1E1E1E),
                    background = Color(0xFF121212)
                )
            ) {
                App(renderer)
            }
        }
    }
}

@Composable
fun App(renderer: JOGLVolumeRenderer) {
    Row(modifier = Modifier.fillMaxSize()) {
        // Control Panel (left side)
        ControlPanel(
            volumeData = renderer.volumeDataState.value,
            transferFunction = renderer.currentTransferFunctionState.value,
            presets = renderer.getTransferFunctionPresets(),
            onTransferFunctionChange = { renderer.setTransferFunction(it) },
            materialAmbient = renderer.materialAmbient.value,
            onMaterialAmbientChange = { renderer.materialAmbient.value = it },
            materialDiffuse = renderer.materialDiffuse.value,
            onMaterialDiffuseChange = { renderer.materialDiffuse.value = it },
            materialSpecular = renderer.materialSpecular.value,
            onMaterialSpecularChange = { renderer.materialSpecular.value = it },
            materialShininess = renderer.materialShininess.value,
            onMaterialShininessChange = { renderer.materialShininess.value = it },
            lightColor = renderer.lightColor.value,
            onLightColorChange = { renderer.lightColor.value = it },
            ambientLightColor = renderer.ambientLightColor.value,
            onAmbientLightColorChange = { renderer.ambientLightColor.value = it },
            lightPosition = renderer.lightPosition.value,
            onLightPositionChange = { renderer.lightPosition.value = it },
            stepSize = renderer.stepSize.value,
            onStepSizeChange = { renderer.stepSize.value = it },
            maxSteps = renderer.maxSteps.value,
            onMaxStepsChange = { renderer.maxSteps.value = it },
            modifier = Modifier
                .width(400.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colors.surface)
        )
        
        // Render viewport (right side) - uses SwingPanel with GLJPanel
        VolumeViewport(
            renderer = renderer,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}
