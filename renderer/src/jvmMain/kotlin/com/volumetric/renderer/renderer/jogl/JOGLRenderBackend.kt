package com.volumetric.renderer.renderer.jogl

import com.jogamp.opengl.GL4
import com.jogamp.common.nio.Buffers
import com.volumetric.renderer.core.rendering.*

/**
 * JOGL OpenGL 4.1 (macOS compatible) implementation of RenderBackend.
 * Uses traditional OpenGL calls without DSA.
 */
class JOGLRenderBackend(private var gl: GL4) : RenderBackend {
    
    override val capabilities: RenderCapabilities
        get() {
            val maxTexSize = IntArray(1)
            val maxTex3DSize = IntArray(1)
            gl.glGetIntegerv(GL4.GL_MAX_TEXTURE_SIZE, maxTexSize, 0)
            gl.glGetIntegerv(GL4.GL_MAX_3D_TEXTURE_SIZE, maxTex3DSize, 0)
            return RenderCapabilities(
                supportsDirectStateAccess = false,
                supportsComputeShaders = false,
                supportsPersistentMapping = false,
                maxTextureSize = maxTexSize[0],
                maxTexture3DSize = maxTex3DSize[0],
                maxComputeWorkGroupSize = intArrayOf(0, 0, 0)
            )
        }
    
    private var volumeShader: ShaderProgram? = null
    private var cubeVAO: Int = 0
    private var cubeVBO: Int = 0
    
    // UBOs
    private var matricesUBO: UniformBuffer? = null
    private var materialUBO: UniformBuffer? = null
    private var lightingUBO: UniformBuffer? = null
    
    /**
     * Update the GL context (called each frame from GLEventListener)
     */
    fun updateGL(newGL: GL4) {
        gl = newGL
    }
    
    override fun initialize() {
        // Enable depth testing and blending
        gl.glEnable(GL4.GL_DEPTH_TEST)
        gl.glEnable(GL4.GL_BLEND)
        gl.glBlendFunc(GL4.GL_SRC_ALPHA, GL4.GL_ONE_MINUS_SRC_ALPHA)
        
        // Create cube geometry for ray casting
        createCubeGeometry()
        
        // Create UBOs
        matricesUBO = createUniformBuffer(3 * 16 * 4) // 3 matrices * 16 floats * 4 bytes
        materialUBO = createUniformBuffer(4 * 4 * 4)  // Ka, Kd, Ks, shininess
        lightingUBO = createUniformBuffer(3 * 4 * 4)  // lightColor, ambientLight, lightPosition
        
        val version = gl.glGetString(GL4.GL_VERSION)
        val renderer = gl.glGetString(GL4.GL_RENDERER)
        println("JOGL OpenGL initialized: $version")
        println("Renderer: $renderer")
    }
    
    private fun createCubeGeometry() {
        // Cube vertices for bounding box (0 to 1)
        val vertices = floatArrayOf(
            // Front face
            0f, 0f, 1f,  1f, 0f, 1f,  1f, 1f, 1f,
            1f, 1f, 1f,  0f, 1f, 1f,  0f, 0f, 1f,
            // Back face
            0f, 0f, 0f,  0f, 1f, 0f,  1f, 1f, 0f,
            1f, 1f, 0f,  1f, 0f, 0f,  0f, 0f, 0f,
            // Top face
            0f, 1f, 0f,  0f, 1f, 1f,  1f, 1f, 1f,
            1f, 1f, 1f,  1f, 1f, 0f,  0f, 1f, 0f,
            // Bottom face
            0f, 0f, 0f,  1f, 0f, 0f,  1f, 0f, 1f,
            1f, 0f, 1f,  0f, 0f, 1f,  0f, 0f, 0f,
            // Right face
            1f, 0f, 0f,  1f, 1f, 0f,  1f, 1f, 1f,
            1f, 1f, 1f,  1f, 0f, 1f,  1f, 0f, 0f,
            // Left face
            0f, 0f, 0f,  0f, 0f, 1f,  0f, 1f, 1f,
            0f, 1f, 1f,  0f, 1f, 0f,  0f, 0f, 0f
        )
        
        // Create VAO and VBO
        val vaoArray = IntArray(1)
        val vboArray = IntArray(1)
        gl.glGenVertexArrays(1, vaoArray, 0)
        gl.glGenBuffers(1, vboArray, 0)
        cubeVAO = vaoArray[0]
        cubeVBO = vboArray[0]
        
        gl.glBindVertexArray(cubeVAO)
        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, cubeVBO)
        
        // Use direct buffer for OpenGL compatibility
        val buffer = Buffers.newDirectFloatBuffer(vertices)
        gl.glBufferData(GL4.GL_ARRAY_BUFFER, (vertices.size * 4).toLong(), buffer, GL4.GL_STATIC_DRAW)
        
        // Configure vertex attributes
        gl.glEnableVertexAttribArray(0)
        gl.glVertexAttribPointer(0, 3, GL4.GL_FLOAT, false, 3 * 4, 0)
        
        gl.glBindVertexArray(0)
        gl.glBindBuffer(GL4.GL_ARRAY_BUFFER, 0)
    }
    
    override fun createTexture3D(data: FloatArray, width: Int, height: Int, depth: Int): Texture3D {
        return JOGLTexture3D(gl, data, width, height, depth)
    }
    
    override fun createTexture1D(data: FloatArray, width: Int): Texture1D {
        return JOGLTexture1D(gl, data, width)
    }
    
    override fun createShaderProgram(vertexSource: String, fragmentSource: String): ShaderProgram {
        return JOGLShaderProgram(gl, vertexSource, fragmentSource)
    }
    
    override fun createUniformBuffer(size: Int): UniformBuffer {
        return JOGLUniformBuffer(gl, size)
    }
    
    override fun render(state: RenderState) {
        val shader = volumeShader
        val volTexture = state.volumeTexture
        val tfTexture = state.transferFunctionTexture
        
        if (shader == null || volTexture == null || tfTexture == null) {
            // Debug: print what's missing
            if (shader == null) println("Warning: volumeShader is null")
            if (volTexture == null) println("Warning: volumeTexture is null")
            if (tfTexture == null) println("Warning: transferFunction is null")
            return
        }
        
        // Update UBOs
        updateMatricesUBO(state)
        updateMaterialUBO(state)
        updateLightingUBO(state)
        
        // Bind UBOs
        matricesUBO?.bind(0)
        materialUBO?.bind(1)
        lightingUBO?.bind(2)
        
        // Use shader
        shader.use()
        
        // Bind textures and set sampler uniforms
        volTexture.bind(0)
        tfTexture.bind(1)
        shader.setUniform("volumeData", 0)
        shader.setUniform("transferFunction", 1)
        
        // Set uniforms
        shader.setUniform("cameraPosition", state.cameraPosition)
        shader.setUniform("bboxMin", state.bboxMin)
        shader.setUniform("bboxMax", state.bboxMax)
        shader.setUniform("sliceMin", state.sliceMin)
        shader.setUniform("sliceMax", state.sliceMax)
        shader.setUniform("step", state.stepSize)
        shader.setUniform("steps", state.maxSteps)
        shader.setUniform("debugMode", state.debugMode)
        
        // Render cube (disable depth test for proper alpha blending)
        gl.glDisable(GL4.GL_DEPTH_TEST)
        gl.glEnable(GL4.GL_CULL_FACE)
        gl.glCullFace(GL4.GL_FRONT) // Render back faces for ray casting entry points
        
        gl.glBindVertexArray(cubeVAO)
        gl.glDrawArrays(GL4.GL_TRIANGLES, 0, 36)
        gl.glBindVertexArray(0)
        
        gl.glDisable(GL4.GL_CULL_FACE)
        gl.glEnable(GL4.GL_DEPTH_TEST)
    }
    
    private fun updateMatricesUBO(state: RenderState) {
        val data = FloatArray(48) // 3 matrices * 16 floats
        var offset = 0
        state.modelMatrix.toFloatArray().copyInto(data, offset)
        offset += 16
        state.viewMatrix.toFloatArray().copyInto(data, offset)
        offset += 16
        state.projectionMatrix.toFloatArray().copyInto(data, offset)
        matricesUBO?.update(data)
    }
    
    private fun updateMaterialUBO(state: RenderState) {
        val data = floatArrayOf(
            state.ka.x, state.ka.y, state.ka.z, 0f,
            state.kd.x, state.kd.y, state.kd.z, 0f,
            state.ks.x, state.ks.y, state.ks.z, 0f,
            state.shininess.toFloat(), 0f, 0f, 0f
        )
        materialUBO?.update(data)
    }
    
    private fun updateLightingUBO(state: RenderState) {
        val data = floatArrayOf(
            state.lightColor.x, state.lightColor.y, state.lightColor.z, 0f,
            state.ambientLight.x, state.ambientLight.y, state.ambientLight.z, 0f,
            state.lightPosition.x, state.lightPosition.y, state.lightPosition.z, 0f
        )
        lightingUBO?.update(data)
    }
    
    override fun setViewport(x: Int, y: Int, width: Int, height: Int) {
        gl.glViewport(x, y, width, height)
    }
    
    override fun clear(r: Float, g: Float, b: Float, a: Float) {
        gl.glClearColor(r, g, b, a)
        gl.glClear(GL4.GL_COLOR_BUFFER_BIT or GL4.GL_DEPTH_BUFFER_BIT)
    }
    
    override fun cleanup() {
        volumeShader?.dispose()
        matricesUBO?.dispose()
        materialUBO?.dispose()
        lightingUBO?.dispose()
        
        gl.glDeleteVertexArrays(1, intArrayOf(cubeVAO), 0)
        gl.glDeleteBuffers(1, intArrayOf(cubeVBO), 0)
    }
    
    fun setVolumeShader(shader: ShaderProgram) {
        volumeShader = shader
    }
}
