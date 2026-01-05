package com.volumetric.renderer.core.math

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * 4x4 matrix for transformations (translation, rotation, scale, projection).
 * Corresponds to the original C++ Matrix4x4 class.
 * 
 * Storage is column-major to match OpenGL/GLSL conventions.
 */
data class Matrix4x4(
    val elements: FloatArray = FloatArray(16) { if (it % 5 == 0) 1f else 0f }
) {
    operator fun get(row: Int, col: Int): Float = elements[col * 4 + row]
    
    operator fun set(row: Int, col: Int, value: Float) {
        elements[col * 4 + row] = value
    }
    
    /**
     * Matrix multiplication
     */
    operator fun times(other: Matrix4x4): Matrix4x4 {
        val result = Matrix4x4()
        for (row in 0..3) {
            for (col in 0..3) {
                var sum = 0f
                for (k in 0..3) {
                    sum += this[row, k] * other[k, col]
                }
                result[row, col] = sum
            }
        }
        return result
    }
    
    /**
     * Transform a 3D vector (treats as point with w=1)
     */
    operator fun times(v: Vector3): Vector3 {
        val x = this[0, 0] * v.x + this[0, 1] * v.y + this[0, 2] * v.z + this[0, 3]
        val y = this[1, 0] * v.x + this[1, 1] * v.y + this[1, 2] * v.z + this[1, 3]
        val z = this[2, 0] * v.x + this[2, 1] * v.y + this[2, 2] * v.z + this[2, 3]
        val w = this[3, 0] * v.x + this[3, 1] * v.y + this[3, 2] * v.z + this[3, 3]
        return if (w != 0f) Vector3(x / w, y / w, z / w) else Vector3(x, y, z)
    }
    
    /**
     * Transpose the matrix
     */
    fun transpose(): Matrix4x4 {
        val result = Matrix4x4()
        for (row in 0..3) {
            for (col in 0..3) {
                result[row, col] = this[col, row]
            }
        }
        return result
    }
    
    /**
     * Get raw elements for OpenGL (already in column-major order)
     */
    fun toFloatArray(): FloatArray = elements.copyOf()
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Matrix4x4) return false
        return elements.contentEquals(other.elements)
    }
    
    override fun hashCode(): Int = elements.contentHashCode()
    
    companion object {
        /**
         * Identity matrix
         */
        fun identity() = Matrix4x4()
        
        /**
         * Translation matrix
         */
        fun translation(x: Float, y: Float, z: Float): Matrix4x4 {
            val m = identity()
            m[0, 3] = x
            m[1, 3] = y
            m[2, 3] = z
            return m
        }
        
        fun translation(v: Vector3) = translation(v.x, v.y, v.z)
        
        /**
         * Uniform scale matrix
         */
        fun scale(s: Float): Matrix4x4 {
            val m = identity()
            m[0, 0] = s
            m[1, 1] = s
            m[2, 2] = s
            return m
        }
        
        /**
         * Non-uniform scale matrix
         */
        fun scale(x: Float, y: Float, z: Float): Matrix4x4 {
            val m = identity()
            m[0, 0] = x
            m[1, 1] = y
            m[2, 2] = z
            return m
        }
        
        fun scale(v: Vector3) = scale(v.x, v.y, v.z)
        
        /**
         * Rotation around X axis (radians)
         */
        fun rotationX(angleRad: Float): Matrix4x4 {
            val m = identity()
            val c = cos(angleRad)
            val s = sin(angleRad)
            m[1, 1] = c
            m[1, 2] = -s
            m[2, 1] = s
            m[2, 2] = c
            return m
        }
        
        /**
         * Rotation around Y axis (radians)
         */
        fun rotationY(angleRad: Float): Matrix4x4 {
            val m = identity()
            val c = cos(angleRad)
            val s = sin(angleRad)
            m[0, 0] = c
            m[0, 2] = s
            m[2, 0] = -s
            m[2, 2] = c
            return m
        }
        
        /**
         * Rotation around Z axis (radians)
         */
        fun rotationZ(angleRad: Float): Matrix4x4 {
            val m = identity()
            val c = cos(angleRad)
            val s = sin(angleRad)
            m[0, 0] = c
            m[0, 1] = -s
            m[1, 0] = s
            m[1, 1] = c
            return m
        }
        
        /**
         * Perspective projection matrix
         */
        fun perspective(fovYRad: Float, aspect: Float, near: Float, far: Float): Matrix4x4 {
            val m = Matrix4x4(FloatArray(16))
            val f = 1f / tan(fovYRad / 2f)
            
            m[0, 0] = f / aspect
            m[1, 1] = f
            m[2, 2] = (far + near) / (near - far)
            m[2, 3] = (2f * far * near) / (near - far)
            m[3, 2] = -1f
            m[3, 3] = 0f
            
            return m
        }
        
        /**
         * Orthographic projection matrix
         */
        fun orthographic(
            left: Float, right: Float,
            bottom: Float, top: Float,
            near: Float, far: Float
        ): Matrix4x4 {
            val m = identity()
            
            m[0, 0] = 2f / (right - left)
            m[1, 1] = 2f / (top - bottom)
            m[2, 2] = -2f / (far - near)
            
            m[0, 3] = -(right + left) / (right - left)
            m[1, 3] = -(top + bottom) / (top - bottom)
            m[2, 3] = -(far + near) / (far - near)
            
            return m
        }
        
        /**
         * Look-at view matrix
         */
        fun lookAt(eye: Vector3, center: Vector3, up: Vector3): Matrix4x4 {
            val f = (center - eye).normalize()
            val s = (f cross up).normalize()
            val u = s cross f
            
            val m = identity()
            
            m[0, 0] = s.x
            m[0, 1] = s.y
            m[0, 2] = s.z
            
            m[1, 0] = u.x
            m[1, 1] = u.y
            m[1, 2] = u.z
            
            m[2, 0] = -f.x
            m[2, 1] = -f.y
            m[2, 2] = -f.z
            
            m[0, 3] = -(s dot eye)
            m[1, 3] = -(u dot eye)
            m[2, 3] = f dot eye
            
            return m
        }
    }
}
