package com.volumetric.renderer.renderer.jogl

import com.jogamp.opengl.GL4
import com.jogamp.common.nio.Buffers
import com.volumetric.renderer.core.rendering.UniformBuffer

/**
 * JOGL OpenGL 4.1 Uniform Buffer Object.
 */
class JOGLUniformBuffer(
    private val gl: GL4,
    override val size: Int
) : UniformBuffer {
    
    override val id: Int
    
    init {
        val buffers = IntArray(1)
        gl.glGenBuffers(1, buffers, 0)
        id = buffers[0]
        
        gl.glBindBuffer(GL4.GL_UNIFORM_BUFFER, id)
        gl.glBufferData(GL4.GL_UNIFORM_BUFFER, size.toLong(), null, GL4.GL_DYNAMIC_DRAW)
        gl.glBindBuffer(GL4.GL_UNIFORM_BUFFER, 0)
    }
    
    override fun bind(bindingPoint: Int) {
        gl.glBindBufferBase(GL4.GL_UNIFORM_BUFFER, bindingPoint, id)
    }
    
    override fun update(data: FloatArray, offset: Int) {
        gl.glBindBuffer(GL4.GL_UNIFORM_BUFFER, id)
        // Use direct buffer for OpenGL compatibility
        val buffer = Buffers.newDirectFloatBuffer(data)
        gl.glBufferSubData(GL4.GL_UNIFORM_BUFFER, offset.toLong(), (data.size * 4).toLong(), buffer)
        gl.glBindBuffer(GL4.GL_UNIFORM_BUFFER, 0)
    }
    
    override fun dispose() {
        gl.glDeleteBuffers(1, intArrayOf(id), 0)
    }
}
