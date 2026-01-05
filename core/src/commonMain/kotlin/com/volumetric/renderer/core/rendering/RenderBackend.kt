package com.volumetric.renderer.core.rendering

import com.volumetric.renderer.core.math.Matrix4x4
import com.volumetric.renderer.core.math.Vector3

/**
 * Rendering capabilities available on different platforms.
 * Desktop OpenGL 4.5 has more features than mobile OpenGL ES 3.2.
 */
data class RenderCapabilities(
    val supportsDirectStateAccess: Boolean,
    val supportsComputeShaders: Boolean,
    val supportsPersistentMapping: Boolean,
    val maxTextureSize: Int,
    val maxTexture3DSize: Int,
    val maxComputeWorkGroupSize: IntArray
)

/**
 * Abstract representation of a shader program.
 */
interface ShaderProgram {
    val id: Int
    
    fun use()
    fun setUniform(name: String, value: Float)
    fun setUniform(name: String, value: Int)
    fun setUniform(name: String, value: Vector3)
    fun setUniform(name: String, value: Matrix4x4)
    fun setUniform(name: String, x: Float, y: Float, z: Float)
    fun dispose()
}

/**
 * Abstract representation of a 3D texture (for volume data).
 */
interface Texture3D {
    val id: Int
    val width: Int
    val height: Int
    val depth: Int
    
    fun bind(unit: Int)
    fun unbind()
    fun dispose()
}

/**
 * Abstract representation of a 1D texture (for transfer function).
 */
interface Texture1D {
    val id: Int
    val width: Int
    
    fun bind(unit: Int)
    fun unbind()
    fun update(data: FloatArray)
    fun dispose()
}

/**
 * Uniform Buffer Object for efficient uniform management.
 */
interface UniformBuffer {
    val id: Int
    val size: Int
    
    fun bind(bindingPoint: Int)
    fun update(data: FloatArray, offset: Int = 0)
    fun dispose()
}

/**
 * Render state containing all information needed for rendering.
 */
data class RenderState(
    val volumeTexture: Texture3D? = null,
    val transferFunctionTexture: Texture1D? = null,
    val modelMatrix: Matrix4x4 = Matrix4x4.identity(),
    val viewMatrix: Matrix4x4 = Matrix4x4.identity(),
    val projectionMatrix: Matrix4x4 = Matrix4x4.identity(),
    val cameraPosition: Vector3 = Vector3.ZERO,
    val lightPosition: Vector3 = Vector3(2f, 2f, 2f),
    val lightColor: Vector3 = Vector3.ONE,
    val ambientLight: Vector3 = Vector3(0.2f, 0.2f, 0.2f),
    val ka: Vector3 = Vector3(0.3f, 0.3f, 0.3f), // Ambient material
    val kd: Vector3 = Vector3(0.6f, 0.6f, 0.6f), // Diffuse material
    val ks: Vector3 = Vector3(0.8f, 0.8f, 0.8f), // Specular material
    val shininess: Int = 32,
    val stepSize: Float = 0.005f,
    val maxSteps: Int = 1000,
    val bboxMin: Vector3 = Vector3.ZERO,
    val bboxMax: Vector3 = Vector3.ONE,
    val debugMode: Int = 0  // 0=normal, 1=density, 2=coords
)

/**
 * Main rendering backend abstraction.
 * Implementations: OpenGLRenderBackend (desktop), OpenGLESRenderBackend (mobile).
 */
interface RenderBackend {
    val capabilities: RenderCapabilities
    
    /**
     * Initialize the rendering backend.
     */
    fun initialize()
    
    /**
     * Create a 3D texture from volume data.
     * @param data Raw volume data (normalized 0-1 range)
     * @param width Width of the volume
     * @param height Height of the volume
     * @param depth Depth of the volume
     */
    fun createTexture3D(data: FloatArray, width: Int, height: Int, depth: Int): Texture3D
    
    /**
     * Create a 1D texture for transfer function.
     * @param data RGBA color data (4 floats per pixel)
     * @param width Number of samples
     */
    fun createTexture1D(data: FloatArray, width: Int): Texture1D
    
    /**
     * Create a shader program from vertex and fragment shader source.
     * @param vertexSource GLSL vertex shader code
     * @param fragmentSource GLSL fragment shader code
     */
    fun createShaderProgram(vertexSource: String, fragmentSource: String): ShaderProgram
    
    /**
     * Create a uniform buffer object.
     * @param size Size in bytes
     */
    fun createUniformBuffer(size: Int): UniformBuffer
    
    /**
     * Render a frame with the given state.
     */
    fun render(state: RenderState)
    
    /**
     * Set the viewport dimensions.
     */
    fun setViewport(x: Int, y: Int, width: Int, height: Int)
    
    /**
     * Clear the framebuffer.
     */
    fun clear(r: Float = 0f, g: Float = 0f, b: Float = 0f, a: Float = 1f)
    
    /**
     * Cleanup resources.
     */
    fun cleanup()
}
