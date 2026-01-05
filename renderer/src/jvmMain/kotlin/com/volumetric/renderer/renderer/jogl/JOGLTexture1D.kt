package com.volumetric.renderer.renderer.jogl

import com.jogamp.opengl.GL4
import com.jogamp.common.nio.Buffers
import com.volumetric.renderer.core.rendering.Texture1D

/**
 * JOGL OpenGL 4.1 implementation of 1D texture (for transfer functions).
 * Uses GL_RGBA32F for macOS Metal compatibility.
 */
class JOGLTexture1D(
    private val gl: GL4,
    data: FloatArray,
    override val width: Int
) : Texture1D {
    
    override val id: Int
    
    init {
        val textures = IntArray(1)
        gl.glGenTextures(1, textures, 0)
        id = textures[0]
        
        gl.glBindTexture(GL4.GL_TEXTURE_1D, id)
        
        // Clear any previous errors
        gl.glGetError()
        
        // Set texture parameters
        gl.glTexParameteri(GL4.GL_TEXTURE_1D, GL4.GL_TEXTURE_MIN_FILTER, GL4.GL_LINEAR)
        gl.glTexParameteri(GL4.GL_TEXTURE_1D, GL4.GL_TEXTURE_MAG_FILTER, GL4.GL_LINEAR)
        gl.glTexParameteri(GL4.GL_TEXTURE_1D, GL4.GL_TEXTURE_WRAP_S, GL4.GL_CLAMP_TO_EDGE)
        
        // Create direct FloatBuffer using JOGL's Buffers utility
        val floatBuffer = Buffers.newDirectFloatBuffer(data)
        
        // Use glTexStorage1D + glTexSubImage1D for better compatibility
        gl.glTexStorage1D(
            GL4.GL_TEXTURE_1D,
            1,  // 1 mipmap level
            GL4.GL_RGBA32F,  // Internal format
            width
        )
        
        gl.glTexSubImage1D(
            GL4.GL_TEXTURE_1D, 0,
            0, width,
            GL4.GL_RGBA, GL4.GL_FLOAT,
            floatBuffer
        )
        
        gl.glBindTexture(GL4.GL_TEXTURE_1D, 0)
    }
    
    override fun bind(unit: Int) {
        gl.glActiveTexture(GL4.GL_TEXTURE0 + unit)
        gl.glBindTexture(GL4.GL_TEXTURE_1D, id)
    }
    
    override fun unbind() {
        gl.glBindTexture(GL4.GL_TEXTURE_1D, 0)
    }
    
    override fun update(data: FloatArray) {
        gl.glBindTexture(GL4.GL_TEXTURE_1D, id)
        
        // Create direct FloatBuffer using JOGL's Buffers utility
        val floatBuffer = Buffers.newDirectFloatBuffer(data)
        
        gl.glTexSubImage1D(
            GL4.GL_TEXTURE_1D, 0,
            0, width,
            GL4.GL_RGBA, GL4.GL_FLOAT,
            floatBuffer
        )
        gl.glBindTexture(GL4.GL_TEXTURE_1D, 0)
    }
    
    override fun dispose() {
        gl.glDeleteTextures(1, intArrayOf(id), 0)
    }
}
