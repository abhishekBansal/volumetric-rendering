package com.volumetric.renderer.core.utils

import com.volumetric.renderer.core.data.VolumeData

/**
 * Calculate histogram of volume data
 * Can be implemented on CPU or GPU depending on platform
 */
interface HistogramCalculator {
    fun calculateHistogram(volumeData: VolumeData, bins: Int = 256): IntArray
}

/**
 * CPU-based histogram calculation for fallback
 */
class CPUHistogramCalculator : HistogramCalculator {
    override fun calculateHistogram(volumeData: VolumeData, bins: Int): IntArray {
        val histogram = IntArray(bins)
        val data = volumeData.data
        
        data.forEach { value ->
            // Normalize to 0-1 range and convert to bin index
            val normalized = (value.toInt() and 0xFF) / 255f
            val binIndex = (normalized * (bins - 1)).toInt().coerceIn(0, bins - 1)
            histogram[binIndex]++
        }
        
        return histogram
    }
}
