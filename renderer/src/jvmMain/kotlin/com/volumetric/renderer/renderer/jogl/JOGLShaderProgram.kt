package com.volumetric.renderer.renderer.jogl

import com.jogamp.opengl.GL4
import com.volumetric.renderer.core.math.Matrix4x4
import com.volumetric.renderer.core.math.Vector3
import com.volumetric.renderer.core.rendering.ShaderProgram

/**
 * JOGL OpenGL 4.1 shader program implementation.
 */
class JOGLShaderProgram(
    private val gl: GL4,
    vertexSource: String,
    fragmentSource: String
) : ShaderProgram {
    
    override val id: Int
    
    init {
        val vertexShader = compileShader(vertexSource, GL4.GL_VERTEX_SHADER)
        val fragmentShader = compileShader(fragmentSource, GL4.GL_FRAGMENT_SHADER)
        
        id = gl.glCreateProgram()
        gl.glAttachShader(id, vertexShader)
        gl.glAttachShader(id, fragmentShader)
        gl.glLinkProgram(id)
        
        // Check linking errors
        val linkStatus = IntArray(1)
        gl.glGetProgramiv(id, GL4.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == GL4.GL_FALSE) {
            val logLength = IntArray(1)
            gl.glGetProgramiv(id, GL4.GL_INFO_LOG_LENGTH, logLength, 0)
            val log = ByteArray(logLength[0])
            gl.glGetProgramInfoLog(id, logLength[0], null, 0, log, 0)
            throw RuntimeException("Shader program linking failed:\n${String(log)}")
        }
        
        // Bind uniform blocks to binding points (required for OpenGL 4.1)
        val matricesIndex = gl.glGetUniformBlockIndex(id, "Matrices")
        if (matricesIndex != GL4.GL_INVALID_INDEX) {
            gl.glUniformBlockBinding(id, matricesIndex, 0)
        }
        
        val materialIndex = gl.glGetUniformBlockIndex(id, "Material")
        if (materialIndex != GL4.GL_INVALID_INDEX) {
            gl.glUniformBlockBinding(id, materialIndex, 1)
        }
        
        val lightingIndex = gl.glGetUniformBlockIndex(id, "Lighting")
        if (lightingIndex != GL4.GL_INVALID_INDEX) {
            gl.glUniformBlockBinding(id, lightingIndex, 2)
        }
        
        gl.glDeleteShader(vertexShader)
        gl.glDeleteShader(fragmentShader)
    }
    
    private fun compileShader(source: String, type: Int): Int {
        val shader = gl.glCreateShader(type)
        gl.glShaderSource(shader, 1, arrayOf(source), null)
        gl.glCompileShader(shader)
        
        val compileStatus = IntArray(1)
        gl.glGetShaderiv(shader, GL4.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == GL4.GL_FALSE) {
            val logLength = IntArray(1)
            gl.glGetShaderiv(shader, GL4.GL_INFO_LOG_LENGTH, logLength, 0)
            val log = ByteArray(logLength[0])
            gl.glGetShaderInfoLog(shader, logLength[0], null, 0, log, 0)
            val typeName = if (type == GL4.GL_VERTEX_SHADER) "vertex" else "fragment"
            throw RuntimeException("$typeName shader compilation failed:\n${String(log)}")
        }
        
        return shader
    }
    
    override fun use() {
        gl.glUseProgram(id)
    }
    
    override fun setUniform(name: String, value: Float) {
        val location = gl.glGetUniformLocation(id, name)
        if (location != -1) {
            gl.glUniform1f(location, value)
        }
    }
    
    override fun setUniform(name: String, value: Int) {
        val location = gl.glGetUniformLocation(id, name)
        if (location != -1) {
            gl.glUniform1i(location, value)
        }
    }
    
    override fun setUniform(name: String, value: Vector3) {
        val location = gl.glGetUniformLocation(id, name)
        if (location != -1) {
            gl.glUniform3f(location, value.x, value.y, value.z)
        }
    }
    
    override fun setUniform(name: String, value: Matrix4x4) {
        val location = gl.glGetUniformLocation(id, name)
        if (location != -1) {
            gl.glUniformMatrix4fv(location, 1, false, value.toFloatArray(), 0)
        }
    }
    
    override fun setUniform(name: String, x: Float, y: Float, z: Float) {
        val location = gl.glGetUniformLocation(id, name)
        if (location != -1) {
            gl.glUniform3f(location, x, y, z)
        }
    }
    
    override fun dispose() {
        gl.glDeleteProgram(id)
    }
}
