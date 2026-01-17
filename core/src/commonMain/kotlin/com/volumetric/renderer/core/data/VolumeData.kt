package com.volumetric.renderer.core.data

import kotlinx.serialization.Serializable

/**
 * Volume data dimensions and metadata.
 */
@Serializable
data class VolumeDimensions(
    val width: Int,
    val height: Int,
    val depth: Int
) {
    val totalVoxels: Int get() = width * height * depth
}

/**
 * Volume data with raw voxel values.
 */
data class VolumeData(
    val dimensions: VolumeDimensions,
    val data: FloatArray, // Normalized to 0-1 range
    val name: String = "Unknown",
    val metadata: VolumeMetadata = VolumeMetadata()
) {
    init {
        require(data.size == dimensions.totalVoxels) {
            "Data size (${data.size}) must match dimensions (${dimensions.totalVoxels})"
        }
    }
    
    /**
     * Get voxel value at specific coordinates.
     */
    operator fun get(x: Int, y: Int, z: Int): Float {
        require(x in 0 until dimensions.width) { "X out of bounds: $x" }
        require(y in 0 until dimensions.height) { "Y out of bounds: $y" }
        require(z in 0 until dimensions.depth) { "Z out of bounds: $z" }
        
        val index = z * dimensions.width * dimensions.height + y * dimensions.width + x
        return data[index]
    }
    
    /**
     * Calculate histogram for transfer function UI.
     */
    fun calculateHistogram(bins: Int = 256): IntArray {
        val histogram = IntArray(bins)
        for (value in data) {
            val bin = (value * (bins - 1)).toInt().coerceIn(0, bins - 1)
            histogram[bin]++
        }
        return histogram
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VolumeData) return false
        
        if (dimensions != other.dimensions) return false
        if (!data.contentEquals(other.data)) return false
        if (name != other.name) return false
        if (metadata != other.metadata) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = dimensions.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }
}

/**
 * Metadata associated with volume data.
 */
@Serializable
data class VolumeMetadata(
    val patientName: String = "",
    val studyDescription: String = "",
    val seriesDescription: String = "",
    val modality: String = "",
    val pixelSpacing: List<Float> = listOf(1f, 1f, 1f),
    val sliceThickness: Float = 1f,
    val bitsAllocated: Int = 16,
    val rescaleIntercept: Float = 0f,
    val rescaleSlope: Float = 1f
)
