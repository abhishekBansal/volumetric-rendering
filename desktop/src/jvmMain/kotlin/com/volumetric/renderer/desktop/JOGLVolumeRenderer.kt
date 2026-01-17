package com.volumetric.renderer.desktop

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.nio.ByteBuffer
import com.jogamp.opengl.*
import com.jogamp.opengl.awt.GLJPanel
import com.volumetric.renderer.core.data.GradientStop
import com.volumetric.renderer.core.data.TransferFunction
import com.volumetric.renderer.core.data.VolumeData
import com.volumetric.renderer.core.data.VolumeDimensions
import com.volumetric.renderer.core.io.DicomLoader
import com.volumetric.renderer.core.io.NiftiLoader
import com.volumetric.renderer.core.math.Matrix4x4
import com.volumetric.renderer.core.math.Vector3
import com.volumetric.renderer.core.rendering.Camera
import com.volumetric.renderer.core.rendering.RenderState
import com.volumetric.renderer.renderer.jogl.JOGLRenderBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.PI

/**
 * JOGL-based volume renderer implementing GLEventListener.
 * Can be embedded in a GLJPanel for Swing/Compose integration.
 */
class JOGLVolumeRenderer(private val initialDicomPath: String? = null) : GLEventListener {
    
    private var backend: JOGLRenderBackend? = null
    private var camera = Camera(
        position = Vector3(2f, 2f, 3f),
        target = Vector3(0.5f, 0.5f, 0.5f)
    )
    
    private var volumeData: VolumeData? = null
    private var renderState: RenderState? = null
    private var lastFrameTime = System.nanoTime()
    
    // Loading state management
    private val _loadingState = MutableStateFlow<LoadingState>(LoadingState.Idle)
    val loadingState: StateFlow<LoadingState> = _loadingState.asStateFlow()
    
    var fps = 0
        private set
    
    // Offscreen Rendering Support
    var offscreenImage = mutableStateOf<ImageBitmap?>(null)
    var isOffscreenMode = false
    private var pixelBuffer: ByteBuffer? = null
    
    // State for UI
    var currentTransferFunctionState = mutableStateOf(TransferFunction())
    var volumeDataState = mutableStateOf<VolumeData?>(null)
    
    // Material State
    var materialAmbient = mutableStateOf(0.6f) // Increased from 0.3 for visibility
    var materialDiffuse = mutableStateOf(0.8f)
    var materialSpecular = mutableStateOf(0.8f)
    var materialShininess = mutableStateOf(32f)
    
    // Lighting State
    var lightColor = mutableStateOf(androidx.compose.ui.graphics.Color.White)
    var ambientLightColor = mutableStateOf(androidx.compose.ui.graphics.Color(0.6f, 0.6f, 0.6f)) // Brighter ambient
    var lightPosition = mutableStateOf(Vector3(2f, 2f, 2f))
    
    // Rendering Quality
    var stepSize = mutableStateOf(0.004f)
    var maxSteps = mutableStateOf(512)
    
    // Viewport size
    private var viewportWidth = 800
    private var viewportHeight = 600
    private var frameCount = 0L
    
    // Transfer function presets
    private var currentPresetIndex = 0
    private var debugMode = 0  // 0=normal, 1=density, 2=coords
    private val transferFunctionPresets = listOf(
        // CT Anatomy (Based on user analysis)
        TransferFunction(
            gradientStops = listOf(
                GradientStop(0.0f, 0.79f, 0.88f, 0.82f, 0.0f),      // -770 HU (Air)
                GradientStop(0.2f, 1.0f, 0.78f, 0.7f, 0.0f),        // -144 HU (Lung)
                GradientStop(0.26f, 0.73f, 0.0f, 0.03f, 0.0f),      // 62 HU (Blood)
                GradientStop(0.27f, 0.81f, 0.04f, 0.07f, 0.0f),     // 90 HU (Opacity Start)
                GradientStop(0.29f, 1.0f, 0.14f, 0.17f, 0.07f),     // 158 HU (Soft Tissue)
                GradientStop(0.30f, 1.0f, 0.35f, 0.17f, 0.12f),     // 210 HU (Muscle)
                GradientStop(0.31f, 1.0f, 0.46f, 0.15f, 0.14f),     // 228 HU (Soft Tissue 2)
                GradientStop(0.32f, 1.0f, 0.64f, 0.11f, 0.21f),     // 259 HU (Dense Tissue)
                GradientStop(0.33f, 0.98f, 0.73f, 0.32f, 0.39f),    // 330 HU (Bone Start)
                GradientStop(0.37f, 0.94f, 0.92f, 0.73f, 0.83f),    // 466 HU (Bone Marrow)
                GradientStop(0.38f, 0.94f, 0.92f, 0.73f, 0.94f),    // 499 HU (Bone)
                GradientStop(0.75f, 1.0f, 1.0f, 1.0f, 0.94f),       // 2001 HU (Cortical Bone)
                GradientStop(1.0f, 1.0f, 0.96f, 0.99f, 1.0f)        // 3071 HU (Dense Bone)
            ),
            name = "CT Anatomy"
        ),
        // Cardiac MRI (Optimized for la_003)
        TransferFunction(
            gradientStops = listOf(
                GradientStop(0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
                GradientStop(0.2f, 0.0f, 0.0f, 0.0f, 0.0f),         // Cut off noise up to 20%
                GradientStop(0.25f, 1.0f, 0.14f, 0.17f, 0.05f),     // Myocardium (Low opacity)
                GradientStop(0.35f, 1.0f, 0.35f, 0.17f, 0.2f),      // Muscle
                GradientStop(0.5f, 1.0f, 0.64f, 0.11f, 0.5f),       // Dense
                GradientStop(0.7f, 0.94f, 0.92f, 0.73f, 0.8f),      // Blood Pool / Contrast
                GradientStop(1.0f, 1.0f, 1.0f, 1.0f, 1.0f)
            ),
            name = "Cardiac MRI"
        ),
        // Cardiac MRI (Blood Pool)
        TransferFunction(
            gradientStops = listOf(
                GradientStop(0.0f, 0.0f, 0.0f, 0.0f, 0.0f),
                GradientStop(0.4f, 0.0f, 0.0f, 0.0f, 0.0f),         // Aggressive cutoff
                GradientStop(0.45f, 0.8f, 0.0f, 0.0f, 0.1f),        // Transition
                GradientStop(0.55f, 1.0f, 0.2f, 0.2f, 0.6f),        // Blood Pool edge
                GradientStop(0.7f, 1.0f, 0.8f, 0.8f, 0.9f),         // Blood Pool center
                GradientStop(1.0f, 1.0f, 1.0f, 1.0f, 1.0f)
            ),
            name = "Cardiac MRI (Blood Pool)"
        ),
        // High contrast for seeing structures
        TransferFunction(
            gradientStops = listOf(
                GradientStop(0f, 0f, 0f, 0f, 0f),
                GradientStop(0.05f, 0.1f, 0.1f, 0.1f, 0.0f),
                GradientStop(0.2f, 0.3f, 0.3f, 0.3f, 0.3f),
                GradientStop(0.4f, 0.6f, 0.6f, 0.6f, 0.6f),
                GradientStop(0.6f, 0.85f, 0.85f, 0.85f, 0.8f),
                GradientStop(1f, 1f, 1f, 1f, 1f)
            ),
            name = "High Contrast"
        ),
        // Maximum visibility
        TransferFunction(
            gradientStops = listOf(
                GradientStop(0f, 0f, 0f, 0f, 0f),
                GradientStop(0.1f, 0.2f, 0.2f, 0.25f, 0.4f),
                GradientStop(0.3f, 0.5f, 0.5f, 0.6f, 0.7f),
                GradientStop(0.5f, 0.8f, 0.8f, 0.9f, 0.9f),
                GradientStop(1f, 1f, 1f, 1f, 1f)
            ),
            name = "Maximum"
        ),
        // Soft tissue
        TransferFunction(
            gradientStops = listOf(
                GradientStop(0f, 0f, 0f, 0f, 0f),
                GradientStop(0.15f, 0.3f, 0.2f, 0.15f, 0.2f),
                GradientStop(0.35f, 0.7f, 0.5f, 0.4f, 0.5f),
                GradientStop(0.6f, 1.0f, 0.8f, 0.7f, 0.8f),
                GradientStop(1f, 1f, 1f, 1f, 1f)
            ),
            name = "Soft Tissue"
        ),
        // Vascular (red tint)
        TransferFunction(
            gradientStops = listOf(
                GradientStop(0f, 0f, 0f, 0f, 0f),
                GradientStop(0.2f, 0.3f, 0.05f, 0.05f, 0.3f),
                GradientStop(0.4f, 0.8f, 0.2f, 0.15f, 0.6f),
                GradientStop(0.7f, 1.0f, 0.5f, 0.3f, 0.9f),
                GradientStop(1f, 1f, 0.8f, 0.6f, 1f)
            ),
            name = "Vascular"
        ),
        TransferFunction.bonePreset(),
        TransferFunction.hotMetalPreset(),
        TransferFunction(
            gradientStops = listOf(
                GradientStop(0f, 0f, 0f, 0f, 0f),
                GradientStop(0.5f, 0.3f, 0.6f, 0.9f, 0.5f),
                GradientStop(1f, 0.9f, 0.3f, 0.1f, 1f)
            ),
            name = "Purple-Gold"
        )
    )
    private var needsTextureUpdate = false
    
    // Callback for notifying UI about state changes
    var onStateChanged: (() -> Unit)? = null
    
    override fun init(drawable: GLAutoDrawable) {
        val gl = drawable.gl.gL4
        println("[JOGLVolumeRenderer] Initializing OpenGL...")
        
        backend = JOGLRenderBackend(gl)
        backend?.initialize()
        
        loadShaders(gl)
        
        // Load DICOM if path provided, otherwise create test volume
        if (initialDicomPath != null) {
            val file = File(initialDicomPath)
            if (file.exists()) {
                println("[JOGLVolumeRenderer] Loading medical data from: $initialDicomPath")
                loadDataset(file)
            } else {
                println("[JOGLVolumeRenderer] ⚠️ Path not found: $initialDicomPath")
                createTestVolume()
            }
        } else {
            createTestVolume()
        }
        
        println("[JOGLVolumeRenderer] ✓ Initialization complete")
    }
    
    override fun display(drawable: GLAutoDrawable) {
        if (frameCount == 0L) {
            println("[display] ⚠️ FIRST display() call - GL rendering starting")
        }
        
        val gl = drawable.gl.gL4
        backend?.updateGL(gl)
        
        // DEBUG: Always clear with a visible color first to test GL is working
        gl.glClearColor(0.2f, 0.0f, 0.3f, 1.0f)  // Purple background
        gl.glClear(GL4.GL_COLOR_BUFFER_BIT or GL4.GL_DEPTH_BUFFER_BIT)
        
        val volume = volumeData
        val back = backend
        
        if (volume == null || back == null) {
            // Still loading - purple background shows GL is working
            println("[display] No volume data or backend - showing purple background")
            return
        }
        
        // Create textures if needed
        if (renderState == null) {
            println("[display] Creating textures for volume: ${volume.name}")
            println("[display] Volume dimensions: ${volume.dimensions.width}x${volume.dimensions.height}x${volume.dimensions.depth}")

            // Derive physical spacing from metadata (defaults to 1.0 if missing)
            val meta = volume.metadata
            val px = meta.pixelSpacing.getOrNull(0) ?: 1f
            val py = meta.pixelSpacing.getOrNull(1) ?: 1f
            val pz = meta.sliceThickness

            val physX = px * volume.dimensions.width
            val physY = py * volume.dimensions.height
            val physZ = pz * volume.dimensions.depth

            // Normalize so the largest dimension maps to size 1.0 in world-space
            val maxPhys = maxOf(physX, physY, physZ)
            val scaleX = physX / maxPhys
            val scaleY = physY / maxPhys
            val scaleZ = physZ / maxPhys

            val model = Matrix4x4.scale(scaleX.toFloat(), scaleY.toFloat(), scaleZ.toFloat())
            val bboxMin = Vector3(0f, 0f, 0f)
            val bboxMax = Vector3(scaleX.toFloat(), scaleY.toFloat(), scaleZ.toFloat())

            val volumeTexture = back.createTexture3D(
                volume.data,
                volume.dimensions.width,
                volume.dimensions.height,
                volume.dimensions.depth
            )

            val transferFunction = currentTransferFunctionState.value
            val tfTexture = back.createTexture1D(transferFunction.toTexture1D(), 256)

            renderState = RenderState(
                volumeTexture = volumeTexture,
                transferFunctionTexture = tfTexture,
                modelMatrix = model,
                bboxMin = bboxMin,
                bboxMax = bboxMax
            )

            // Center camera on the physical bbox
            val center = Vector3((bboxMin.x + bboxMax.x) / 2f, (bboxMin.y + bboxMax.y) / 2f, (bboxMin.z + bboxMax.z) / 2f)
            camera = camera.copy(target = center)

            println("[display] ✅ Textures created successfully")
            println("[display]   Volume texture ID: $volumeTexture")
            println("[display]   Transfer function: ${transferFunction.name}")
            println("[display]   Physical bbox -> min:$bboxMin max:$bboxMax")
            println("[display]   Model scale: ($scaleX, $scaleY, $scaleZ)")
            println("[display]   Camera reset to: pos=${camera.position}, target=${camera.target}")
        }
        
        // Update transfer function texture if preset changed
        if (needsTextureUpdate) {
            println("[display] Updating transfer function texture...")
            val transferFunction = currentTransferFunctionState.value
            val tfTexture = back.createTexture1D(transferFunction.toTexture1D(), 256)
            renderState = renderState?.copy(transferFunctionTexture = tfTexture)
            needsTextureUpdate = false
            println("[display] ✅ Transfer function updated to: ${transferFunction.name}")
        }
        
        // Update matrices
        val aspect = viewportWidth.toFloat() / viewportHeight.toFloat()
        val viewMatrix = Matrix4x4.lookAt(camera.position, camera.target, camera.up)
        val projectionMatrix = Matrix4x4.perspective(
            (camera.fovDegrees * PI / 180f).toFloat(),
            aspect,
            camera.near,
            camera.far
        )
        
        renderState = renderState?.copy(
            viewMatrix = viewMatrix,
            projectionMatrix = projectionMatrix,
            cameraPosition = camera.position,
            debugMode = debugMode,
            ka = Vector3(materialAmbient.value, materialAmbient.value, materialAmbient.value),
            kd = Vector3(materialDiffuse.value, materialDiffuse.value, materialDiffuse.value),
            ks = Vector3(materialSpecular.value, materialSpecular.value, materialSpecular.value),
            shininess = materialShininess.value.toInt(),
            lightColor = Vector3(lightColor.value.red, lightColor.value.green, lightColor.value.blue),
            ambientLight = Vector3(ambientLightColor.value.red, ambientLightColor.value.green, ambientLightColor.value.blue),
            lightPosition = lightPosition.value,
            stepSize = stepSize.value,
            maxSteps = maxSteps.value
        )
        
        // Render
        back.setViewport(0, 0, viewportWidth, viewportHeight)
        back.clear(0.1f, 0.1f, 0.15f, 1.0f) // Dark background
        renderState?.let { state ->
            back.render(state)
        } ?: println("[display] WARNING: renderState is null, skipping render")
        
        // Calculate FPS
        frameCount++
        val currentTime = System.nanoTime()
        val deltaTime = (currentTime - lastFrameTime) / 1_000_000_000.0
        fps = (1.0 / deltaTime).toInt()
        lastFrameTime = currentTime

        if (isOffscreenMode) {
            readPixels(gl, viewportWidth, viewportHeight)
        }
    }

    private fun readPixels(gl: GL4, width: Int, height: Int) {
        val bufferSize = width * height * 4
        if (pixelBuffer == null || pixelBuffer!!.capacity() < bufferSize) {
            pixelBuffer = ByteBuffer.allocateDirect(bufferSize)
            println("[readPixels] Allocated new pixel buffer: ${width}x${height} = $bufferSize bytes")
        }

        pixelBuffer!!.rewind()
        gl.glReadPixels(0, 0, width, height, GL4.GL_RGBA, GL4.GL_UNSIGNED_BYTE, pixelBuffer)

        val bytes = ByteArray(bufferSize)
        pixelBuffer!!.get(bytes)
        
        val bitmap = Bitmap()
        val imageInfo = ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
        bitmap.allocPixels(imageInfo)
        bitmap.installPixels(bytes)
        
        offscreenImage.value = bitmap.asComposeImageBitmap()
        
        if (frameCount == 1L) {
            println("[readPixels] ✅ First frame bitmap created and set to offscreenImage")
        }
    }
    
    override fun reshape(drawable: GLAutoDrawable, x: Int, y: Int, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        val gl = drawable.gl.gL4
        gl.glViewport(x, y, width, height)
    }
    
    override fun dispose(drawable: GLAutoDrawable) {
        println("[JOGLVolumeRenderer] Disposing resources...")
        // Clean up GL resources
        renderState?.volumeTexture?.dispose()
        renderState?.transferFunctionTexture?.dispose()
        backend?.cleanup()
        
        // CRITICAL: Reset state so new context can recreate textures
        renderState = null
        backend = null
    }
    
    // === Input Handling ===
    
    fun handleMouseDrag(dx: Float, dy: Float) {
        val sensitivity = 0.005f
        camera = camera.orbit(-dx * sensitivity, -dy * sensitivity)
    }
    
    fun handleMouseScroll(delta: Float) {
        val zoomFactor = if (delta > 0) 0.9f else 1.1f
        camera = camera.zoom(zoomFactor)
    }
    
    fun handleKeyPress(key: Char) {
        println("[JOGLVolumeRenderer] Key pressed: $key")
        val speed = 0.1f
        camera = when (key.lowercaseChar()) {
            'w' -> camera.moveForward(speed)
            's' -> camera.moveForward(-speed)
            'a' -> camera.moveRight(-speed)
            'd' -> camera.moveRight(speed)
            ' ' -> camera.moveUp(speed)
            'q' -> camera.moveUp(-speed)
            'p' -> {
                cycleTransferFunctionPreset()
                camera
            }
            'g' -> {
                // Toggle debug mode: 0 -> 1 -> 2 -> 0
                debugMode = (debugMode + 1) % 3
                println("Debug mode: $debugMode (0=normal, 1=density, 2=coords)")
                camera
            }
            else -> camera
        }
    }
    
    // === Transfer Function Control ===
    
    fun cycleTransferFunctionPreset() {
        currentPresetIndex = (currentPresetIndex + 1) % transferFunctionPresets.size
        currentTransferFunctionState.value = transferFunctionPresets[currentPresetIndex]
        needsTextureUpdate = true
        println("✓ Switched to transfer function: ${transferFunctionPresets[currentPresetIndex].name}")
        onStateChanged?.invoke()
    }
    
    fun setTransferFunctionPreset(index: Int) {
        if (index in transferFunctionPresets.indices) {
            currentPresetIndex = index
            currentTransferFunctionState.value = transferFunctionPresets[currentPresetIndex]
            needsTextureUpdate = true
            println("✓ Set transfer function: ${transferFunctionPresets[currentPresetIndex].name}")
        }
    }
    
    fun setTransferFunction(newTransferFunction: TransferFunction) {
        currentTransferFunctionState.value = newTransferFunction
        needsTextureUpdate = true
        // Note: We don't update currentPresetIndex here as it might be a custom TF
    }
    
    // === Getters ===
    
    fun getVolumeData(): VolumeData? = volumeData
    
    fun getCurrentTransferFunction(): TransferFunction = currentTransferFunctionState.value
    
    fun getTransferFunctionPresets(): List<TransferFunction> = transferFunctionPresets
    
    fun getCurrentPresetIndex(): Int = currentPresetIndex
    
    fun getCameraInfo(): String = "Pos(%.1f, %.1f, %.1f)".format(
        camera.position.x, camera.position.y, camera.position.z
    )
    
    fun getVolumeInfo(): String = volumeData?.let {
        "${it.dimensions.width}x${it.dimensions.height}x${it.dimensions.depth} - ${it.name}"
    } ?: "Loading..."
    
    // === Private Helpers ===
    
    private fun loadShaders(gl: GL4) {
        val vertexShader = loadShaderResource("/shaders/volume_vertex.glsl")
        val fragmentShader = loadShaderResource("/shaders/volume_fragment.glsl")
        
        val shaderProgram = backend?.createShaderProgram(vertexShader, fragmentShader)
        if (shaderProgram != null) {
            backend?.setVolumeShader(shaderProgram)
            println("✓ Shaders loaded")
        }
    }
    
    private fun createTestVolume() {
        // Create a 64³ test volume with a sphere gradient
        val dims = VolumeDimensions(64, 64, 64)
        val data = FloatArray(dims.totalVoxels) { index ->
            val x = (index % dims.width).toFloat()
            val y = ((index / dims.width) % dims.height).toFloat()
            val z = (index / (dims.width * dims.height)).toFloat()
            
            val cx = dims.width / 2f
            val cy = dims.height / 2f
            val cz = dims.depth / 2f
            
            val dx = (x - cx) / cx
            val dy = (y - cy) / cy
            val dz = (z - cz) / cz
            
            val distance = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
            kotlin.math.max(0f, 1f - distance)
        }
        
        volumeData = VolumeData(dims, data, "Test Sphere")
        volumeDataState.value = volumeData
        println("✓ Test volume created: ${dims.width}x${dims.height}x${dims.depth}")
        onStateChanged?.invoke()
    }
    
    /**
     * Load a medical dataset from a file or directory.
     * Supports DICOM (.dcm), NIfTI (.nii, .nii.gz), and DICOM series (directories).
     * 
     * @param file File or directory to load
     * @param onSuccess Optional callback invoked on successful load
     * @param onError Optional callback invoked on error with error message
     */
    fun loadDataset(
        file: File,
        onSuccess: ((VolumeData) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        println("[JOGLVolumeRenderer] loadDataset called with: ${file.absolutePath}")
        println("[JOGLVolumeRenderer] File exists: ${file.exists()}, isFile: ${file.isFile}, isDirectory: ${file.isDirectory}")
        
        // Prevent multiple concurrent loads
        if (_loadingState.value is LoadingState.Loading) {
            println("[JOGLVolumeRenderer] ⚠️ Load already in progress, skipping...")
            onError?.invoke("Another dataset is currently loading")
            return
        }
        
        // Validate file exists
        if (!file.exists()) {
            val error = "File or directory does not exist: ${file.absolutePath}"
            println("[JOGLVolumeRenderer] ❌ $error")
            _loadingState.value = LoadingState.Error(error)
            onError?.invoke(error)
            return
        }
        
        println("[JOGLVolumeRenderer] Setting loading state...")
        _loadingState.value = LoadingState.Loading(0f, "Preparing to load...")
        
        println("[JOGLVolumeRenderer] Launching coroutine for data loading...")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                println("\n=== Loading medical imaging data from: ${file.absolutePath} ===")
                
                val volume = when {
                    file.name.endsWith(".nii") || file.name.endsWith(".nii.gz") -> {
                        _loadingState.value = LoadingState.Loading(0.1f, "Loading NIfTI file...")
                        val result = NiftiLoader.loadNiftiFile(file.absolutePath)
                        if (result == null) {
                            throw Exception("Failed to load NIfTI file: ${file.name}")
                        }
                        result
                    }
                    file.isDirectory -> {
                        _loadingState.value = LoadingState.Loading(0.1f, "Scanning DICOM directory...")
                        val result = DicomLoader.loadDicomSeries(file.absolutePath) { progress ->
                            val message = "Loading DICOM series (${(progress * 100).toInt()}%)..."
                            _loadingState.value = LoadingState.Loading(progress, message)
                            println(message)
                        }
                        
                        result.getOrNull()?.first ?: throw result.exceptionOrNull() 
                            ?: Exception("Failed to load DICOM series from: ${file.name}")
                    }
                    else -> {
                        // Check if this file is part of a series in the same folder
                        val parentDir = file.parentFile
                        val siblingDicoms = parentDir?.listFiles { f -> 
                            f.extension.equals("dcm", ignoreCase = true) || f.extension.all { it.isDigit() }
                        }?.toList() ?: emptyList()
                        
                        if (siblingDicoms.size > 1) {
                            println("[JOGLVolumeRenderer] Detected multiple DICOM files in parent directory. Loading as series.")
                            _loadingState.value = LoadingState.Loading(0.1f, "Scanning DICOM series...")
                            val result = DicomLoader.loadDicomSeries(parentDir!!.absolutePath) { progress ->
                                val message = "Loading DICOM series (${(progress * 100).toInt()}%)..."
                                _loadingState.value = LoadingState.Loading(progress, message)
                                println(message)
                            }
                            result.getOrNull()?.first ?: throw result.exceptionOrNull() 
                                ?: Exception("Failed to load DICOM series from: ${parentDir.name}")
                        } else {
                            _loadingState.value = LoadingState.Loading(0.1f, "Loading DICOM file...")
                            val result = DicomLoader.loadSingleDicom(file.absolutePath)
                            result.getOrNull()?.first ?: throw result.exceptionOrNull() 
                                ?: Exception("Failed to load DICOM file: ${file.name}")
                        }
                    }
                }
                
                println("\n✓ Volume loaded successfully!")
                println("  Dimensions: ${volume.dimensions.width}x${volume.dimensions.height}x${volume.dimensions.depth}")
                
                // Update volume data and reset render state on main thread
                volumeData = volume
                volumeDataState.value = volume
                renderState = null  // Force texture recreation on next frame
                needsTextureUpdate = false
                
                // Reset camera
                camera = Camera(
                    position = Vector3(2f, 2f, 3f),
                    target = Vector3(0.5f, 0.5f, 0.5f)
                )
                
                _loadingState.value = LoadingState.Success
                println("[JOGLVolumeRenderer] ✅ Volume loaded successfully and ready to render")
                println("[JOGLVolumeRenderer] Render state reset - will recreate textures on next frame")
                onStateChanged?.invoke()
                onSuccess?.invoke(volume)
                
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown error occurred while loading dataset"
                println("✗ Error loading data: $errorMsg")
                e.printStackTrace()
                _loadingState.value = LoadingState.Error(errorMsg, e)
                onError?.invoke(errorMsg)
            }
        }
    }
    
    private fun loadShaderResource(path: String): String {
        return this::class.java.getResourceAsStream(path)?.bufferedReader()?.readText()
            ?: throw RuntimeException("Failed to load shader: $path")
    }
    
    companion object {
        /**
         * Create a GLJPanel configured for this renderer
         */
        fun createGLPanel(renderer: JOGLVolumeRenderer): GLJPanel {
            val profile = GLProfile.get(GLProfile.GL4)
            val capabilities = GLCapabilities(profile).apply {
                doubleBuffered = true
                hardwareAccelerated = true
                sampleBuffers = true
                numSamples = 4
            }
            
            return GLJPanel(capabilities).apply {
                isFocusable = true
                addGLEventListener(renderer)
            }
        }

        /**
         * Create a GLCanvas configured for this renderer (Heavyweight fallback)
         */
        fun createGLCanvas(renderer: JOGLVolumeRenderer): com.jogamp.opengl.awt.GLCanvas {
            val profile = GLProfile.get(GLProfile.GL4)
            val capabilities = GLCapabilities(profile).apply {
                doubleBuffered = true
                hardwareAccelerated = true
                sampleBuffers = true
                numSamples = 4
            }
            
            return com.jogamp.opengl.awt.GLCanvas(capabilities).apply {
                isFocusable = true
                addGLEventListener(renderer)
            }
        }
    }
}
