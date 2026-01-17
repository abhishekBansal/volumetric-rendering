package com.volumetric.renderer.core.rendering

import com.volumetric.renderer.core.math.Quaternion
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
     * Rotate camera around target (orbit) with Quaternions.
     * Uses quaternions to avoid gimbal lock and allow seamless rotation.
     */
    fun orbit(deltaYaw: Float, deltaPitch: Float): Camera {
        val vectorToCamera = position - target
        
        // Calculate current basis vectors
        val forward = (target - position).normalize()
        val right = (forward cross up).normalize()
        
        // 1. Pitch Rotation (Around Local Right)
        val pitchQuat = Quaternion.fromAxisAngle(right, deltaPitch)
        var newVector = pitchQuat.rotate(vectorToCamera)
        var newUp = pitchQuat.rotate(up)
        
        // 2. Yaw Rotation (Around World Y for stable horizon)
        val yawQuat = Quaternion.fromAxisAngle(Vector3.UNIT_Y, deltaYaw)
        newVector = yawQuat.rotate(newVector)
        newUp = yawQuat.rotate(newUp)
        
        return copy(
            position = target + newVector,
            up = newUp.normalize()
        )
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
