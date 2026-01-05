package com.volumetric.renderer.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
        // Display the rendered frame
        renderer.offscreenImage.value?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = "Volume Render",
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1f, -1f) // Flip vertically as GL is bottom-up
            )
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
        // Initial creation handled by resize()
    }
    
    fun resize(width: Int, height: Int) {
        if (drawable != null && drawable!!.surfaceWidth == width && drawable!!.surfaceHeight == height) {
            return
        }
        
        destroy()
        
        try {
            val profile = GLProfile.get(GLProfile.GL4)
            val capabilities = GLCapabilities(profile)
            val factory = GLDrawableFactory.getFactory(profile)
            
            drawable = factory.createOffscreenAutoDrawable(null, capabilities, null, width, height)
            drawable?.addGLEventListener(renderer)
            
            renderer.isOffscreenMode = true
            println("[OffscreenController] Resized to $width x $height")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun render() {
        drawable?.display()
    }
    
    fun destroy() {
        drawable?.destroy()
        drawable = null
    }
}
