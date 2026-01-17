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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.onExternalDrag
import androidx.compose.ui.DragData
import java.io.File
import java.awt.KeyboardFocusManager
import java.awt.KeyEventDispatcher
import java.awt.event.KeyEvent

import com.volumetric.renderer.desktop.ui.ControlPanel
import com.volumetric.renderer.desktop.ui.FilePickerDialog

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
    
    // Shared state for file picker trigger (accessible from both AWT and Compose)
    val filePickerTrigger = mutableStateOf(0)

    // Register a global AWT KeyEventDispatcher to forward keyboard events to the renderer.
    val kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    
    val dispatcher = KeyEventDispatcher { e ->
        if (e.id == KeyEvent.KEY_PRESSED) {
            when (e.keyCode) {
                KeyEvent.VK_ESCAPE -> {
                    renderer.handleKeyPress('\u001B')
                    false
                }
                KeyEvent.VK_O -> {
                    // Increment to trigger file picker in Compose
                    filePickerTrigger.value++
                    false
                }
                else -> {
                    val ch = e.keyChar
                    if (ch.code != 0) renderer.handleKeyPress(ch)
                    false
                }
            }
        } else {
            false // do not consume; allow other handlers to run
        }
    }
    kfm.addKeyEventDispatcher(dispatcher)
    
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
                App(renderer, filePickerTrigger)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App(renderer: JOGLVolumeRenderer, filePickerTrigger: MutableState<Int>) {
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var triggerLoadFile by remember { mutableStateOf<File?>(null) }
    var isDragging by remember { mutableStateOf(false) } // Track dragging state
    
    println("[App] Composable recomposed, trigger value: ${filePickerTrigger.value}")
    
    // Watch for keyboard 'O' trigger or Load Dataset button click
    LaunchedEffect(filePickerTrigger.value) {
        if (filePickerTrigger.value > 0) {
            println("[App] 🔑 File picker triggered (count: ${filePickerTrigger.value})")
            println("[App] 📂 Opening file picker dialog...")
            
            val file = FilePickerDialog.showOpenDialog()
            
            if (file != null) {
                println("[App] 📁 File selected: ${file.absolutePath}")
                println("[App] 📁 File exists: ${file.exists()}, isFile: ${file.isFile}, isDirectory: ${file.isDirectory}")
                
                // Validate file
                val (isValid, validationError) = FilePickerDialog.validateDatasetFile(file)
                println("[App] Validation result - isValid: $isValid, error: $validationError")
                
                if (isValid) {
                    println("[App] ✅ File validated, triggering load...")
                    triggerLoadFile = file
                } else {
                    println("[App] ❌ File validation failed: $validationError")
                    errorMessage = validationError
                }
            } else {
                println("[App] 📂 File picker cancelled by user")
            }
        }
    }
    
    // Handle file loading
    LaunchedEffect(triggerLoadFile) {
        val file = triggerLoadFile
        if (file != null) {
            println("[App] 🚀 Starting dataset load for: ${file.absolutePath}")
            renderer.loadDataset(
                file = file,
                onSuccess = { volumeData ->
                    println("[App] ✅ Dataset loaded successfully: ${volumeData.name}")
                    errorMessage = null
                    triggerLoadFile = null
                },
                onError = { error ->
                    println("[App] ❌ Dataset loading failed: $error")
                    errorMessage = error
                    triggerLoadFile = null
                }
            )
        }
    }
    
    // Handle Load Dataset button click
    val onLoadDatasetClick: () -> Unit = {
        println("[App] 🖱️ Load Dataset button clicked")
        filePickerTrigger.value++
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .onExternalDrag(
                    onDragStart = { isDragging = true },
                    onDragExit = { isDragging = false },
                    onDrop = { state ->
                        isDragging = false
                        val data = state.dragData
                        if (data is DragData.FilesList) {
                            val path = data.readFiles().firstOrNull() ?: return@onExternalDrag
                            println("[App] 📦 Drag raw path: '$path'")
                            
                            val file = try {
                                if (path.startsWith("file:")) {
                                    File(java.net.URI(path))
                                } else {
                                    File(path)
                                }
                            } catch (e: Exception) {
                                println("[App] ⚠️ Error parsing drag path '$path': ${e.message}")
                                // Try simplified path as fallback
                                val cleanPath = path.removePrefix("file://").removePrefix("file:")
                                File(cleanPath)
                            }
                            
                            println("[App] 📦 Native Drop (Resolved): ${file.absolutePath}")
                            val (isValid, error) = FilePickerDialog.validateDatasetFile(file)
                            if (isValid) {
                                triggerLoadFile = file
                            } else {
                                errorMessage = error
                            }
                        }
                    }
                )
        ) {
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
                // Slicing State
                sliceXRange = renderer.sliceXMin.value..renderer.sliceXMax.value,
                onSliceXRangeChange = { range ->
                    renderer.sliceXMin.value = range.start
                    renderer.sliceXMax.value = range.endInclusive
                },
                sliceYRange = renderer.sliceYMin.value..renderer.sliceYMax.value,
                onSliceYRangeChange = { range ->
                    renderer.sliceYMin.value = range.start
                    renderer.sliceYMax.value = range.endInclusive
                },
                sliceZRange = renderer.sliceZMin.value..renderer.sliceZMax.value,
                onSliceZRangeChange = { range ->
                    renderer.sliceZMin.value = range.start
                    renderer.sliceZMax.value = range.endInclusive
                },
                onLoadDatasetClicked = onLoadDatasetClick,
                errorMessage = errorMessage,
                onDismissError = { errorMessage = null },
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

        // Drag Overlay
        if (isDragging) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    "Drop Volume Data Here",
                    color = Color.White,
                    style = MaterialTheme.typography.h4
                )
            }
        }
    }
}
