package com.volumetric.renderer.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.jogamp.opengl.GLCapabilities
import com.jogamp.opengl.GLDrawableFactory
import com.jogamp.opengl.GLOffscreenAutoDrawable
import com.jogamp.opengl.GLProfile
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Compose wrapper for the JOGL volume rendering viewport.
 * Uses Offscreen Rendering (FBO -> Bitmap) to avoid AWT/Compose flickering issues.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VolumeViewport(
    renderer: JOGLVolumeRenderer,
    modifier: Modifier = Modifier
) {
    var fps by remember { mutableStateOf(0) }
    
    // Observe loading state
    val loadingState by renderer.loadingState.collectAsState()
    
    // Manage the Offscreen Drawable
    val offscreenController = remember { OffscreenController(renderer) }
    
    DisposableEffect(Unit) {
        offscreenController.init()
        onDispose {
            offscreenController.destroy()
        }
    }
    
    // Render Loop
    LaunchedEffect(Unit) {
        while (isActive) {
            offscreenController.render()
            fps = renderer.fps
            // Target ~60 FPS
            delay(16) 
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { size ->
                if (size.width > 0 && size.height > 0) {
                    offscreenController.resize(size.width, size.height)
                }
            }
            // focus on click handled by AWT dispatcher; keep pointerInput for drag gestures
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    renderer.handleMouseDrag(dragAmount.x, dragAmount.y)
                    change.consume()
                }
            }
            .onPointerEvent(PointerEventType.Scroll) {
                val change = it.changes.first()
                val scrollAmount = change.scrollDelta.y
                renderer.handleMouseScroll(scrollAmount)
            }
    ) {
        // Display the rendered frame - read .value directly to observe state changes
        if (renderer.offscreenImage.value != null) {
            Image(
                bitmap = renderer.offscreenImage.value!!,
                contentDescription = "Volume Render",
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1f, -1f) // Flip vertically as GL is bottom-up
            )
        } else {
            // Show a placeholder when no image is available
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0.2f, 0f, 0.3f)), // Purple to match GL clear color
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Waiting for OpenGL context...",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        
        // Loading overlay
        if (loadingState is LoadingState.Loading) {
            val loading = loadingState as LoadingState.Loading
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x88000000)) // Semi-transparent black
            ) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    
                    Text(
                        text = loading.message,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    
                    if (loading.progress > 0f) {
                        LinearProgressIndicator(
                            progress = loading.progress,
                            modifier = Modifier.width(200.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "${(loading.progress * 100).toInt()}%",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Overlays
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Text(
                text = "FPS: $fps",
                color = Color.Green,
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = "Renderer: Offscreen (FBO)",
                color = Color.Yellow,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

class OffscreenController(private val renderer: JOGLVolumeRenderer) {
    private var drawable: GLOffscreenAutoDrawable? = null
    
    fun init() {
        println("[OffscreenController] Initializing offscreen controller")
        // Initial creation handled by resize()
    }
    
    fun resize(width: Int, height: Int) {
        println("[OffscreenController] Resize requested: ${width}x${height}")
        
        if (drawable != null && drawable!!.surfaceWidth == width && drawable!!.surfaceHeight == height) {
            println("[OffscreenController] Already at correct size, skipping resize")
            return
        }
        
        destroy()
        
        try {
            println("[OffscreenController] Creating offscreen drawable...")
            val profile = GLProfile.get(GLProfile.GL4)
            val capabilities = GLCapabilities(profile)
            val factory = GLDrawableFactory.getFactory(profile)
            
            drawable = factory.createOffscreenAutoDrawable(null, capabilities, null, width, height)
            drawable?.addGLEventListener(renderer)
            
            renderer.isOffscreenMode = true
            println("[OffscreenController] ✅ Offscreen drawable created: ${width}x${height}")
            println("[OffscreenController] Drawable: ${drawable}")
        } catch (e: Exception) {
            println("[OffscreenController] ❌ Failed to create offscreen drawable: ${e.message}")
            e.printStackTrace()
        }
    }
    
    fun render() {
        drawable?.display()
    }
    
    fun destroy() {
        if (drawable != null) {
            println("[OffscreenController] Destroying drawable")
        }
        drawable?.destroy()
        drawable = null
    }
}
