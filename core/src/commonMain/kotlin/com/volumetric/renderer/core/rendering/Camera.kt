package com.volumetric.renderer.core.rendering

import com.volumetric.renderer.core.math.Vector3

/**
 * Camera for 3D navigation.
 * Similar to the original C++ camera control.
 */
data class Camera(
    val position: Vector3 = Vector3(0f, 0f, 3f),
    val target: Vector3 = Vector3.ZERO,
    val up: Vector3 = Vector3.UNIT_Y,
    val fovDegrees: Float = 45f,
    val near: Float = 0.1f,
    val far: Float = 100f
) {
    /**
     * Move camera forward/backward along view direction.
     */
    fun moveForward(distance: Float): Camera {
        val direction = (target - position).normalize()
        return copy(
            position = position + direction * distance,
            target = target + direction * distance
        )
    }
    
    /**
     * Move camera left/right perpendicular to view direction.
     */
    fun moveRight(distance: Float): Camera {
        val direction = (target - position).normalize()
        val right = (direction cross up).normalize()
        return copy(
            position = position + right * distance,
            target = target + right * distance
        )
    }
    
    /**
     * Move camera up/down along the up vector.
     */
    fun moveUp(distance: Float): Camera {
        return copy(
            position = position + up * distance,
            target = target + up * distance
        )
    }
    
    /**
     * Rotate camera around target (orbit).
     */
    fun orbit(deltaYaw: Float, deltaPitch: Float): Camera {
        val toCamera = position - target
        val distance = toCamera.length()
        
        // Convert to spherical coordinates
        val theta = kotlin.math.atan2(toCamera.z, toCamera.x) + deltaYaw
        val phi = kotlin.math.acos(toCamera.y / distance).coerceIn(0.1f, 3.04f) + deltaPitch
        
        // Convert back to Cartesian
        val newPosition = target + Vector3(
            distance * kotlin.math.sin(phi) * kotlin.math.cos(theta),
            distance * kotlin.math.cos(phi),
            distance * kotlin.math.sin(phi) * kotlin.math.sin(theta)
        )
        
        return copy(position = newPosition)
    }
    
    /**
     * Zoom in/out by moving camera closer/farther from target.
     */
    fun zoom(factor: Float): Camera {
        val direction = (position - target).normalize()
        val newDistance = (position - target).length() * factor
        return copy(position = target + direction * newDistance.coerceIn(0.5f, 20f))
    }
}
