package com.volumetric.renderer.renderer.jogl

import com.jogamp.opengl.GL4
import com.jogamp.common.nio.Buffers
import com.volumetric.renderer.core.rendering.Texture3D
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * JOGL OpenGL 4.1 implementation of 3D texture.
 * Uses GL_R32F for macOS Metal compatibility.
 */
class JOGLTexture3D(
    private val gl: GL4,
    data: FloatArray,
    override val width: Int,
    override val height: Int,
    override val depth: Int
) : Texture3D {
    
    override val id: Int
    
    init {
        val textures = IntArray(1)
        gl.glGenTextures(1, textures, 0)
        id = textures[0]
        
        gl.glBindTexture(GL4.GL_TEXTURE_3D, id)
        
        // Clear any previous errors
        gl.glGetError()
        
        // Set texture parameters first
        gl.glTexParameteri(GL4.GL_TEXTURE_3D, GL4.GL_TEXTURE_MIN_FILTER, GL4.GL_LINEAR)
        gl.glTexParameteri(GL4.GL_TEXTURE_3D, GL4.GL_TEXTURE_MAG_FILTER, GL4.GL_LINEAR)
        gl.glTexParameteri(GL4.GL_TEXTURE_3D, GL4.GL_TEXTURE_WRAP_S, GL4.GL_CLAMP_TO_EDGE)
        gl.glTexParameteri(GL4.GL_TEXTURE_3D, GL4.GL_TEXTURE_WRAP_T, GL4.GL_CLAMP_TO_EDGE)
        gl.glTexParameteri(GL4.GL_TEXTURE_3D, GL4.GL_TEXTURE_WRAP_R, GL4.GL_CLAMP_TO_EDGE)
        
        // Create a direct FloatBuffer using JOGL's Buffers utility
        val floatBuffer = Buffers.newDirectFloatBuffer(data)
        
        // Use glTexStorage3D + glTexSubImage3D for better compatibility
        gl.glTexStorage3D(
            GL4.GL_TEXTURE_3D,
            1,  // 1 mipmap level
            GL4.GL_R32F,  // Internal format
            width, height, depth
        )
        
        var error = gl.glGetError()
        if (error != GL4.GL_NO_ERROR) {
            println("Warning: glTexStorage3D error: $error")
        }
        
        // Now upload the data
        gl.glTexSubImage3D(
            GL4.GL_TEXTURE_3D, 0,
            0, 0, 0,  // offset
            width, height, depth,
            GL4.GL_RED, GL4.GL_FLOAT,
            floatBuffer
        )
        
        error = gl.glGetError()
        if (error != GL4.GL_NO_ERROR) {
            println("Warning: glTexSubImage3D error: $error")
        }
        
        gl.glBindTexture(GL4.GL_TEXTURE_3D, 0)
        
        println("  3D texture created: ${width}x${height}x${depth} (${data.size * 4 / 1024 / 1024} MB)")
    }
    
    override fun bind(unit: Int) {
        gl.glActiveTexture(GL4.GL_TEXTURE0 + unit)
        gl.glBindTexture(GL4.GL_TEXTURE_3D, id)
        // Verify texture is valid
        val boundTex = IntArray(1)
        gl.glGetIntegerv(GL4.GL_TEXTURE_BINDING_3D, boundTex, 0)
        if (boundTex[0] != id) {
            println("Warning: 3D texture bind failed! Expected $id, got ${boundTex[0]}")
        }
    }
    
    override fun unbind() {
        gl.glBindTexture(GL4.GL_TEXTURE_3D, 0)
    }
    
    override fun dispose() {
        gl.glDeleteTextures(1, intArrayOf(id), 0)
    }
}
