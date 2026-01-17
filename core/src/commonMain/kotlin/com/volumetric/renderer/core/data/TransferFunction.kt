package com.volumetric.renderer.core.data

import kotlinx.serialization.Serializable

/**
 * A single stop in the transfer function gradient.
 */
@Serializable
data class GradientStop(
    val position: Float, // 0.0 to 1.0
    val red: Float,      // 0.0 to 1.0
    val green: Float,    // 0.0 to 1.0
    val blue: Float,     // 0.0 to 1.0
    val alpha: Float     // 0.0 to 1.0 (opacity)
) {
    // Convenience aliases for shorter names
    val r: Float get() = red
    val g: Float get() = green
    val b: Float get() = blue
    
    init {
        require(position in 0f..1f) { "Position must be in [0, 1]" }
        require(red in 0f..1f) { "Red must be in [0, 1]" }
        require(green in 0f..1f) { "Green must be in [0, 1]" }
        require(blue in 0f..1f) { "Blue must be in [0, 1]" }
        require(alpha in 0f..1f) { "Alpha must be in [0, 1]" }
    }
}

/**
 * Bezier curve control point for opacity curve.
 */
@Serializable
data class BezierControlPoint(
    val x: Float, // 0.0 to 1.0
    val y: Float  // 0.0 to 1.0
)

/**
 * Transfer function with gradient stops and Bezier opacity curve.
 * Replaces the old formula-based approach.
 */
@Serializable
data class TransferFunction(
    val gradientStops: List<GradientStop> = defaultGradientStops(),
    val opacityCurve: List<BezierControlPoint> = defaultOpacityCurve(),
    val name: String = "Default"
) {
    init {
        require(gradientStops.isNotEmpty()) { "Must have at least one gradient stop" }
        require(gradientStops.sortedBy { it.position } == gradientStops) {
            "Gradient stops must be sorted by position"
        }
    }
    
    /**
     * Sample the transfer function at a specific density value.
     * Returns RGBA values.
     */
    fun sample(density: Float): FloatArray {
        val pos = density.coerceIn(0f, 1f)
        
        // Find surrounding gradient stops
        val index = gradientStops.indexOfFirst { it.position >= pos }
        
        return when {
            index == 0 -> {
                // Before first stop
                floatArrayOf(
                    gradientStops[0].red,
                    gradientStops[0].green,
                    gradientStops[0].blue,
                    gradientStops[0].alpha
                )
            }
            index == -1 -> {
                // After last stop
                val last = gradientStops.last()
                floatArrayOf(last.red, last.green, last.blue, last.alpha)
            }
            else -> {
                // Interpolate between two stops
                val stop1 = gradientStops[index - 1]
                val stop2 = gradientStops[index]
                val t = (pos - stop1.position) / (stop2.position - stop1.position)
                
                floatArrayOf(
                    lerp(stop1.red, stop2.red, t),
                    lerp(stop1.green, stop2.green, t),
                    lerp(stop1.blue, stop2.blue, t),
                    lerp(stop1.alpha, stop2.alpha, t)
                )
            }
        }
    }
    
    /**
     * Evaluate opacity using Bezier curve.
     */
    private fun evaluateOpacity(t: Float): Float {
        if (opacityCurve.isEmpty()) return t
        
        // Simple linear interpolation for now
        // Can be upgraded to cubic Bezier later
        val scaledT = t * (opacityCurve.size - 1)
        val index = scaledT.toInt().coerceIn(0, opacityCurve.size - 2)
        val localT = scaledT - index
        
        return lerp(opacityCurve[index].y, opacityCurve[index + 1].y, localT)
    }
    
    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
    
    /**
     * Generate a 1D texture from this transfer function.
     * @param samples Number of samples (typically 256 or 512)
     */
    fun toTexture1D(samples: Int = 256): FloatArray {
        val data = FloatArray(samples * 4)
        for (i in 0 until samples) {
            val t = i.toFloat() / (samples - 1)
            val color = sample(t)
            data[i * 4] = color[0]     // R
            data[i * 4 + 1] = color[1] // G
            data[i * 4 + 2] = color[2] // B
            data[i * 4 + 3] = color[3] // A
            
            // Debug print for middle sample
            if (i == samples / 2) {
                println("TF Sample at 0.5: R=${color[0]}, G=${color[1]}, B=${color[2]}, A=${color[3]}")
            }
        }
        return data
    }
    
    companion object {
        /**
         * Default gradient stops (grayscale).
         */
        fun defaultGradientStops() = listOf(
            GradientStop(0f, 0f, 0f, 0f, 0f),
            GradientStop(1f, 1f, 1f, 1f, 1f)
        )
        
        /**
         * Default opacity curve (linear).
         */
        fun defaultOpacityCurve() = listOf(
            BezierControlPoint(0f, 0f),
            BezierControlPoint(1f, 1f)
        )
        
        /**
         * Bone preset (similar to original colormaps).
         */
        fun bonePreset() = TransferFunction(
            gradientStops = listOf(
                GradientStop(0f, 0f, 0f, 0f, 0f),
                GradientStop(0.3f, 0.2f, 0.2f, 0.25f, 0.1f),
                GradientStop(0.6f, 0.8f, 0.8f, 0.6f, 0.5f),
                GradientStop(1f, 1f, 1f, 0.9f, 0.9f)
            ),
            name = "Bone"
        )
        
        /**
         * Hot metal preset.
         */
        fun hotMetalPreset() = TransferFunction(
            gradientStops = listOf(
                GradientStop(0f, 0f, 0f, 0f, 0f),
                GradientStop(0.33f, 0.5f, 0f, 0f, 0.3f),
                GradientStop(0.66f, 1f, 0.5f, 0f, 0.6f),
                GradientStop(1f, 1f, 1f, 0.5f, 1f)
            ),
            name = "Hot Metal"
        )
    }
}
