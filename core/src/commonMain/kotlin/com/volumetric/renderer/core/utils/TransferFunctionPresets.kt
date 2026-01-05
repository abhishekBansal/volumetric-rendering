package com.volumetric.renderer.core.utils

import com.volumetric.renderer.core.data.GradientStop
import com.volumetric.renderer.core.data.TransferFunction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Manager for transfer function presets - save/load from JSON
 */
object TransferFunctionPresets {
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    /**
     * Serialize transfer function to JSON string
     */
    fun toJson(transferFunction: TransferFunction): String {
        return json.encodeToString(transferFunction)
    }
    
    /**
     * Deserialize transfer function from JSON string
     */
    fun fromJson(jsonString: String): TransferFunction {
        return json.decodeFromString(jsonString)
    }
    
    /**
     * Built-in presets for quick access
     */
    val builtInPresets = listOf(
        // High contrast for seeing structures
        TransferFunction(
            gradientStops = listOf(
                GradientStop(0f, 0f, 0f, 0f, 0f),
                GradientStop(0.05f, 0.1f, 0.1f, 0.1f, 0.0f),
                GradientStop(0.2f, 0.3f, 0.3f, 0.3f, 0.3f),
                GradientStop(0.4f, 0.6f, 0.6f, 0.6f, 0.6f),
                GradientStop(0.6f, 0.85f, 0.85f, 0.85f, 0.8f),
                GradientStop(1f, 1f, 1f, 1f, 1f)
            ),
            name = "High Contrast"
        ),
        
        // Maximum visibility
        TransferFunction(
            gradientStops = listOf(
                GradientStop(0f, 0f, 0f, 0f, 0f),
                GradientStop(0.1f, 0.2f, 0.2f, 0.25f, 0.4f),
                GradientStop(0.3f, 0.5f, 0.5f, 0.6f, 0.7f),
                GradientStop(0.5f, 0.8f, 0.8f, 0.9f, 0.9f),
                GradientStop(1f, 1f, 1f, 1f, 1f)
            ),
            name = "Maximum"
        ),
        
        // Soft tissue
        TransferFunction(
            gradientStops = listOf(
                GradientStop(0f, 0f, 0f, 0f, 0f),
                GradientStop(0.15f, 0.3f, 0.2f, 0.15f, 0.2f),
                GradientStop(0.35f, 0.7f, 0.5f, 0.4f, 0.5f),
                GradientStop(0.6f, 1.0f, 0.8f, 0.7f, 0.8f),
                GradientStop(1f, 1f, 1f, 1f, 1f)
            ),
            name = "Soft Tissue"
        ),
        
        // Vascular (red tint)
        TransferFunction(
            gradientStops = listOf(
                GradientStop(0f, 0f, 0f, 0f, 0f),
                GradientStop(0.2f, 0.3f, 0.05f, 0.05f, 0.3f),
                GradientStop(0.4f, 0.8f, 0.2f, 0.15f, 0.6f),
                GradientStop(0.7f, 1.0f, 0.5f, 0.3f, 0.9f),
                GradientStop(1f, 1f, 0.8f, 0.6f, 1f)
            ),
            name = "Vascular"
        ),
        
        // CT Bone
        TransferFunction(
            gradientStops = listOf(
                GradientStop(0f, 0f, 0f, 0f, 0f),
                GradientStop(0.3f, 0.2f, 0.1f, 0f, 0f),
                GradientStop(0.6f, 1f, 0.9f, 0.7f, 0.7f),
                GradientStop(1f, 1f, 1f, 1f, 1f)
            ),
            name = "CT Bone"
        ),
        
        // MRI Brain
        TransferFunction(
            gradientStops = listOf(
                GradientStop(0f, 0f, 0f, 0f, 0f),
                GradientStop(0.2f, 0f, 0.1f, 0.3f, 0.2f),
                GradientStop(0.5f, 0.5f, 0.5f, 0.8f, 0.6f),
                GradientStop(0.8f, 1f, 0.8f, 0.6f, 0.8f),
                GradientStop(1f, 1f, 1f, 1f, 1f)
            ),
            name = "MRI Brain"
        ),
        
        // Hot Metal
        TransferFunction(
            gradientStops = listOf(
                GradientStop(0f, 0f, 0f, 0f, 0f),
                GradientStop(0.25f, 0.5f, 0f, 0f, 0.3f),
                GradientStop(0.5f, 1f, 0.5f, 0f, 0.6f),
                GradientStop(0.75f, 1f, 1f, 0.5f, 0.8f),
                GradientStop(1f, 1f, 1f, 1f, 1f)
            ),
            name = "Hot Metal"
        ),
        
        // Cool Blue
        TransferFunction(
            gradientStops = listOf(
                GradientStop(0f, 0f, 0f, 0f, 0f),
                GradientStop(0.25f, 0f, 0f, 0.5f, 0.3f),
                GradientStop(0.5f, 0f, 0.5f, 1f, 0.6f),
                GradientStop(0.75f, 0.5f, 1f, 1f, 0.8f),
                GradientStop(1f, 1f, 1f, 1f, 1f)
            ),
            name = "Cool Blue"
        ),
        
        // Rainbow
        TransferFunction(
            gradientStops = listOf(
                GradientStop(0f, 0f, 0f, 0f, 0f),
                GradientStop(0.2f, 0.5f, 0f, 0.5f, 0.4f),  // Purple
                GradientStop(0.4f, 0f, 0f, 1f, 0.6f),       // Blue
                GradientStop(0.6f, 0f, 1f, 0f, 0.7f),       // Green
                GradientStop(0.8f, 1f, 1f, 0f, 0.8f),       // Yellow
                GradientStop(1f, 1f, 0f, 0f, 1f)            // Red
            ),
            name = "Rainbow"
        ),
        
        // Grayscale
        TransferFunction(
            gradientStops = listOf(
                GradientStop(0f, 0f, 0f, 0f, 0f),
                GradientStop(0.33f, 0.33f, 0.33f, 0.33f, 0.5f),
                GradientStop(0.66f, 0.66f, 0.66f, 0.66f, 0.75f),
                GradientStop(1f, 1f, 1f, 1f, 1f)
            ),
            name = "Grayscale"
        )
    )
    
    /**
     * Get preset by name
     */
    fun getPresetByName(name: String): TransferFunction? {
        return builtInPresets.firstOrNull { it.name == name }
    }
    
    /**
     * Export preset to JSON file (platform-specific implementation needed)
     */
    fun exportPreset(transferFunction: TransferFunction): String {
        return toJson(transferFunction)
    }
    
    /**
     * Import preset from JSON file (platform-specific implementation needed)
     */
    fun importPreset(jsonContent: String): Result<TransferFunction> {
        return try {
            val tf = fromJson(jsonContent)
            Result.success(tf)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
