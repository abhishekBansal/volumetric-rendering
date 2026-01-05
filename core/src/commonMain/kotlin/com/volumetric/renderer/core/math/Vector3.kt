package com.volumetric.renderer.core.math

import kotlin.math.sqrt

/**
 * 3D vector with basic operations.
 * Corresponds to the original C++ Vector3 class.
 */
data class Vector3(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
) {
    operator fun plus(other: Vector3) = Vector3(x + other.x, y + other.y, z + other.z)
    
    operator fun minus(other: Vector3) = Vector3(x - other.x, y - other.y, z - other.z)
    
    operator fun times(scalar: Float) = Vector3(x * scalar, y * scalar, z * scalar)
    
    operator fun div(scalar: Float) = Vector3(x / scalar, y / scalar, z / scalar)
    
    operator fun unaryMinus() = Vector3(-x, -y, -z)
    
    /**
     * Dot product
     */
    infix fun dot(other: Vector3): Float = x * other.x + y * other.y + z * other.z
    
    /**
     * Cross product
     */
    infix fun cross(other: Vector3) = Vector3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )
    
    /**
     * Length (magnitude) of the vector
     */
    fun length(): Float = sqrt(x * x + y * y + z * z)
    
    /**
     * Squared length (avoids sqrt for performance comparisons)
     */
    fun lengthSquared(): Float = x * x + y * y + z * z
    
    /**
     * Normalized vector (unit vector)
     */
    fun normalize(): Vector3 {
        val len = length()
        return if (len > 0f) this / len else this
    }
    
    /**
     * Linear interpolation between this vector and another
     */
    fun lerp(other: Vector3, t: Float): Vector3 {
        return this * (1f - t) + other * t
    }
    
    /**
     * Distance to another vector
     */
    fun distanceTo(other: Vector3): Float = (this - other).length()
    
    /**
     * Component-wise multiplication
     */
    fun multiply(other: Vector3) = Vector3(x * other.x, y * other.y, z * other.z)
    
    /**
     * Reflect this vector around a normal
     */
    fun reflect(normal: Vector3): Vector3 {
        return this - normal * (2f * (this dot normal))
    }
    
    companion object {
        val ZERO = Vector3(0f, 0f, 0f)
        val ONE = Vector3(1f, 1f, 1f)
        val UNIT_X = Vector3(1f, 0f, 0f)
        val UNIT_Y = Vector3(0f, 1f, 0f)
        val UNIT_Z = Vector3(0f, 0f, 1f)
    }
}
