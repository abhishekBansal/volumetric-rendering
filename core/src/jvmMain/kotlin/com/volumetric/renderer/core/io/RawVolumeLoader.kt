package com.volumetric.renderer.core.io

import com.volumetric.renderer.core.data.VolumeData
import com.volumetric.renderer.core.data.VolumeDimensions
import com.volumetric.renderer.core.data.VolumeMetadata
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Loader for raw binary volume data (legacy format).
 * Supports the original .raw files from the C++ version.
 */
object RawVolumeLoader {
    /**
     * Load a raw binary file with known dimensions.
     * @param file The raw file to load
     * @param width Volume width
     * @param height Volume height
     * @param depth Volume depth
     * @param bitsPerVoxel Bits per voxel (8 or 16)
     * @param littleEndian Byte order (default: true for little-endian)
     */
    fun load(
        file: File,
        width: Int,
        height: Int,
        depth: Int,
        bitsPerVoxel: Int = 16,
        littleEndian: Boolean = true
    ): VolumeData {
        require(file.exists()) { "File does not exist: ${file.absolutePath}" }
        require(bitsPerVoxel == 8 || bitsPerVoxel == 16) { "Only 8 or 16 bits per voxel supported" }
        
        val dimensions = VolumeDimensions(width, height, depth)
        val expectedSize = dimensions.totalVoxels * (bitsPerVoxel / 8)
        
        require(file.length() == expectedSize.toLong()) {
            "File size (${file.length()}) does not match expected size ($expectedSize)"
        }
        
        val bytes = file.readBytes()
        val buffer = ByteBuffer.wrap(bytes)
        buffer.order(if (littleEndian) ByteOrder.LITTLE_ENDIAN else ByteOrder.BIG_ENDIAN)
        
        val data = FloatArray(dimensions.totalVoxels)
        
        when (bitsPerVoxel) {
            8 -> {
                for (i in data.indices) {
                    data[i] = (buffer.get(i).toInt() and 0xFF) / 255f
                }
            }
            16 -> {
                val maxValue = 65535f
                for (i in data.indices) {
                    val value = buffer.getShort(i * 2).toInt() and 0xFFFF
                    data[i] = value / maxValue
                }
            }
        }
        
        return VolumeData(
            dimensions = dimensions,
            data = data,
            name = file.nameWithoutExtension,
            metadata = VolumeMetadata(bitsAllocated = bitsPerVoxel)
        )
    }
}
