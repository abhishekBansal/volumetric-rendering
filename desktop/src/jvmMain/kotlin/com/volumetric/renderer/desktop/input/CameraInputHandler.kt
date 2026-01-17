package com.volumetric.renderer.desktop.input

import com.volumetric.renderer.core.rendering.Camera

/**
 * pure logic handler for Camera navigation inputs.
 */
object CameraInputHandler {
    private const val MOUSE_SENSITIVITY = 0.005f
    private const val MOVE_SPEED = 0.1f
    private const val ZOOM_IN_FACTOR = 0.9f
    private const val ZOOM_OUT_FACTOR = 1.1f

    fun handleDrag(camera: Camera, dx: Float, dy: Float): Camera {
        return camera.orbit(-dx * MOUSE_SENSITIVITY, -dy * MOUSE_SENSITIVITY)
    }

    fun handleScroll(camera: Camera, delta: Float): Camera {
        val zoomFactor = if (delta > 0) ZOOM_IN_FACTOR else ZOOM_OUT_FACTOR
        return camera.zoom(zoomFactor)
    }

    fun handleKeyMovement(camera: Camera, key: Char): Camera {
        return when (key.lowercaseChar()) {
            'w' -> camera.moveForward(MOVE_SPEED)
            's' -> camera.moveForward(-MOVE_SPEED)
            'a' -> camera.moveRight(-MOVE_SPEED)
            'd' -> camera.moveRight(MOVE_SPEED)
            ' ' -> camera.moveUp(MOVE_SPEED)
            'q' -> camera.moveUp(-MOVE_SPEED)
            else -> camera
        }
    }
}
