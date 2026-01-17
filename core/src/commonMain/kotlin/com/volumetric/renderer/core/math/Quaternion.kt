package com.volumetric.renderer.core.math

import kotlin.math.*

/**
 * Quaternion for 3D rotations.
 */
data class Quaternion(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val w: Float = 1f
) {
    /**
     * Multiply two quaternions (combines rotations).
     * this * other applies 'other' rotation then 'this' rotation (usually).
     * But standard math definition: q1 * q2 result.
     */
    operator fun times(other: Quaternion): Quaternion {
        return Quaternion(
            w * other.x + x * other.w + y * other.z - z * other.y,
            w * other.y - x * other.z + y * other.w + z * other.x,
            w * other.z + x * other.y - y * other.x + z * other.w,
            w * other.w - x * other.x - y * other.y - z * other.z
        )
    }

    /**
     * Rotate a vector by this quaternion.
     * v' = q * v * q^-1
     */
    fun rotate(v: Vector3): Vector3 {
        // Optimized implementation
        val u = Vector3(x, y, z)
        val s = w
        
        // 2.0 * dot(u, v) * u
        val term1 = u * (2.0f * (u dot v))
        // (s*s - dot(u, u)) * v
        val term2 = v * (s * s - (u dot u))
        // 2.0 * s * cross(u, v)
        val term3 = (u cross v) * (2.0f * s)
        
        return term1 + term2 + term3
    }
    
    fun normalize(): Quaternion {
        val len = sqrt(x*x + y*y + z*z + w*w)
        if (len < 0.000001f) return IDENTITY
        return Quaternion(x/len, y/len, z/len, w/len)
    }

    companion object {
        val IDENTITY = Quaternion(0f, 0f, 0f, 1f)
        
        fun fromAxisAngle(axis: Vector3, angleRadians: Float): Quaternion {
            val halfAngle = angleRadians * 0.5f
            val s = sin(halfAngle)
            val n = axis.normalize()
            return Quaternion(
                n.x * s,
                n.y * s,
                n.z * s,
                cos(halfAngle)
            )
        }
    }
}
