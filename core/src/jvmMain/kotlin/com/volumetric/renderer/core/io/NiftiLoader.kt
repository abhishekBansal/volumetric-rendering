package com.volumetric.renderer.core.io

import com.volumetric.renderer.core.data.VolumeData
import com.volumetric.renderer.core.data.VolumeDimensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.zip.GZIPInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/**
 * Loader for NIfTI (Neuroimaging Informatics Technology Initiative) format files.
 * Supports both .nii and .nii.gz (compressed) formats.
 */
object NiftiLoader {
    
    data class NiftiHeader(
        val sizeofHdr: Int,
        val datatype: Short,
        val bitpix: Short,
        val dim: IntArray,
        val pixdim: FloatArray,
        val voxOffset: Float,
        val sclSlope: Float,
        val sclInter: Float,
        val calMax: Float,
        val calMin: Float,
        val sliceEnd: Short,
        val xyzUnits: Byte,
        val timeUnits: Byte,
        val description: String,
        val qformCode: Short,
        val sformCode: Short
    )
    
    suspend fun loadNiftiFile(filePath: String): VolumeData? = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) {
            println("❌ NIfTI file not found: $filePath")
            return@withContext null
        }
        
        println("Loading NIfTI file: ${file.name}")
        
        try {
            // Open file with automatic gzip decompression if needed
            val inputStream = if (filePath.endsWith(".gz")) {
                GZIPInputStream(FileInputStream(file))
            } else {
                FileInputStream(file)
            }
            
            // Read header (348 bytes for NIfTI-1)
            val headerBytes = ByteArray(348)
            var totalRead = 0
            while (totalRead < 348) {
                val read = inputStream.read(headerBytes, totalRead, 348 - totalRead)
                if (read == -1) break
                totalRead += read
            }
            
            if (totalRead < 348) {
                println("❌ Invalid NIfTI file: header too short (read $totalRead bytes)")
                inputStream.close()
                return@withContext null
            }
            
            println("  First 4 bytes (should be 348): ${ByteBuffer.wrap(headerBytes, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int}")
            println("  Datatype offset 70-72: ${ByteBuffer.wrap(headerBytes, 70, 2).order(ByteOrder.LITTLE_ENDIAN).short}")
            
            val header = parseNiftiHeader(headerBytes)
            
            // Validate header and check byte order
            val (validHeader, byteOrder) = if (header.sizeofHdr != 348) {
                // Try big-endian
                val headerBE = parseNiftiHeader(headerBytes, ByteOrder.BIG_ENDIAN)
                if (headerBE.sizeofHdr == 348) {
                    println("  Using BIG_ENDIAN byte order")
                    Pair(headerBE, ByteOrder.BIG_ENDIAN)
                } else {
                    println("❌ Invalid NIfTI header size: ${header.sizeofHdr}")
                    inputStream.close()
                    return@withContext null
                }
            } else {
                println("  Using LITTLE_ENDIAN byte order")
                Pair(header, ByteOrder.LITTLE_ENDIAN)
            }
            
            // Extract dimensions (dim[0] is number of dimensions, dim[1-7] are sizes)
            val width = validHeader.dim[1]
            val height = validHeader.dim[2]
            val depth = validHeader.dim[3]
            
            println("  Dimensions: ${width}x${height}x${depth}")
            println("  Datatype: ${validHeader.datatype}, Bits per pixel: ${validHeader.bitpix}")
            println("  Scale: slope=${validHeader.sclSlope}, intercept=${validHeader.sclInter}")
            println("  Range: [${validHeader.calMin}, ${validHeader.calMax}]")
            
            // Calculate voxel offset (where image data starts)
            val voxelOffset = validHeader.voxOffset.toInt()
            
            // Skip to voxel data
            if (voxelOffset > 348) {
                val skipBytes = voxelOffset - 348
                var skipped = 0L
                while (skipped < skipBytes) {
                    val remaining = skipBytes - skipped
                    val skip = inputStream.skip(remaining)
                    if (skip <= 0) break
                    skipped += skip
                }
            }
            
            // Read voxel data based on datatype
            val volumeSize = width * height * depth
            val voxelData = readVoxelData(inputStream, validHeader, volumeSize, byteOrder)
            
            inputStream.close()
            
            if (voxelData == null) {
                println("❌ Failed to read voxel data")
                return@withContext null
            }
            
            // Apply scaling if specified
            val slope = if (validHeader.sclSlope == 0f) 1f else validHeader.sclSlope
            val intercept = validHeader.sclInter
            
            if (slope != 1f || intercept != 0f) {
                for (i in voxelData.indices) {
                    voxelData[i] = voxelData[i] * slope + intercept
                }
            }
            
            // Normalize to [0, 1] range
            var minVal = Float.MAX_VALUE
            var maxVal = Float.MIN_VALUE
            for (value in voxelData) {
                if (value < minVal) minVal = value
                if (value > maxVal) maxVal = value
            }
            
            println("Data range: [$minVal, $maxVal]")
            
            val range = maxVal - minVal
            if (range > 0) {
                println("Normalizing with range: $range")
                for (i in voxelData.indices) {
                    voxelData[i] = (voxelData[i] - minVal) / range
                }
                
                // Verify normalization
                minVal = Float.MAX_VALUE
                maxVal = Float.MIN_VALUE
                for (value in voxelData) {
                    if (value < minVal) minVal = value
                    if (value > maxVal) maxVal = value
                }
                println("Normalized range: [$minVal, $maxVal]")
            }
            
            println("✓ NIfTI loaded successfully!")
            println("  Volume size: ${width}x${height}x${depth}")
            println("  Voxel spacing: ${validHeader.pixdim[1]} x ${validHeader.pixdim[2]} x ${validHeader.pixdim[3]} mm")
            
            VolumeData(
                dimensions = VolumeDimensions(width, height, depth),
                data = voxelData,
                name = file.name
            )
            
        } catch (e: Exception) {
            println("❌ Error loading NIfTI file: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    private fun parseNiftiHeader(bytes: ByteArray, byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN): NiftiHeader {
        val buffer = ByteBuffer.wrap(bytes).order(byteOrder)
        
        // Offset 0: sizeof_hdr (int32)
        val sizeofHdr = buffer.getInt(0)
        
        // Offset 39: dim_info (byte)
        val dimInfo = buffer.get(39)
        
        // Offset 40: dim array (8 x int16)
        val dim = IntArray(8) { buffer.getShort(40 + it * 2).toInt() }
        
        // Offset 70: datatype (int16)
        val datatype = buffer.getShort(70)
        
        // Offset 72: bitpix (int16)
        val bitpix = buffer.getShort(72)
        
        // Offset 74: slice_start (int16)
        val sliceStart = buffer.getShort(74)
        
        // Offset 76: pixdim array (8 x float32)
        val pixdim = FloatArray(8) { buffer.getFloat(76 + it * 4) }
        
        // Offset 108: vox_offset (float32)
        val voxOffset = buffer.getFloat(108)
        
        // Offset 112: scl_slope (float32)
        val sclSlope = buffer.getFloat(112)
        
        // Offset 116: scl_inter (float32)
        val sclInter = buffer.getFloat(116)
        
        // Offset 120: slice_end (int16)
        val sliceEnd = buffer.getShort(120)
        
        // Offset 122: slice_code (byte)
        val sliceCode = buffer.get(122)
        
        // Offset 123: xyzt_units (byte)
        val xyzUnits = buffer.get(123)
        val timeUnits = buffer.get(123) // Same byte, different bits
        
        // Offset 124: cal_max (float32)
        val calMax = buffer.getFloat(124)
        
        // Offset 128: cal_min (float32)
        val calMin = buffer.getFloat(128)
        
        // Offset 148: descrip (80 bytes)
        val descBytes = ByteArray(80)
        for (i in 0 until 80) {
            descBytes[i] = buffer.get(148 + i)
        }
        val description = String(descBytes).trim { it <= ' ' || it == '\u0000' }
        
        // Offset 252: qform_code (int16)
        val qformCode = buffer.getShort(252)
        
        // Offset 254: sform_code (int16)
        val sformCode = buffer.getShort(254)
        
        return NiftiHeader(
            sizeofHdr = sizeofHdr,
            datatype = datatype,
            bitpix = bitpix,
            dim = dim,
            pixdim = pixdim,
            voxOffset = voxOffset,
            sclSlope = sclSlope,
            sclInter = sclInter,
            calMax = calMax,
            calMin = calMin,
            sliceEnd = sliceEnd,
            xyzUnits = xyzUnits,
            timeUnits = timeUnits,
            description = description,
            qformCode = qformCode,
            sformCode = sformCode
        )
    }
    
    private fun readVoxelData(inputStream: java.io.InputStream, header: NiftiHeader, volumeSize: Int, byteOrder: ByteOrder): FloatArray? {
        return when (header.datatype.toInt()) {
            2 -> { // DT_UNSIGNED_CHAR (uint8)
                val bytes = ByteArray(volumeSize)
                var totalRead = 0
                while (totalRead < volumeSize) {
                    val read = inputStream.read(bytes, totalRead, volumeSize - totalRead)
                    if (read == -1) break
                    totalRead += read
                }
                FloatArray(volumeSize) { (bytes[it].toInt() and 0xFF).toFloat() }
            }
            4 -> { // DT_SIGNED_SHORT (int16)
                val bytes = ByteArray(volumeSize * 2)
                var totalRead = 0
                while (totalRead < bytes.size) {
                    val read = inputStream.read(bytes, totalRead, bytes.size - totalRead)
                    if (read == -1) break
                    totalRead += read
                }
                val buffer = ByteBuffer.wrap(bytes).order(byteOrder)
                FloatArray(volumeSize) { buffer.getShort(it * 2).toFloat() }
            }
            8 -> { // DT_INT (int32)
                val bytes = ByteArray(volumeSize * 4)
                var totalRead = 0
                while (totalRead < bytes.size) {
                    val read = inputStream.read(bytes, totalRead, bytes.size - totalRead)
                    if (read == -1) break
                    totalRead += read
                }
                val buffer = ByteBuffer.wrap(bytes).order(byteOrder)
                FloatArray(volumeSize) { buffer.getInt(it * 4).toFloat() }
            }
            16 -> { // DT_FLOAT (float32)
                val bytes = ByteArray(volumeSize * 4)
                var totalRead = 0
                while (totalRead < bytes.size) {
                    val read = inputStream.read(bytes, totalRead, bytes.size - totalRead)
                    if (read == -1) break
                    totalRead += read
                }
                val buffer = ByteBuffer.wrap(bytes).order(byteOrder)
                FloatArray(volumeSize) { buffer.getFloat(it * 4) }
            }
            64 -> { // DT_DOUBLE (float64)
                val bytes = ByteArray(volumeSize * 8)
                var totalRead = 0
                while (totalRead < bytes.size) {
                    val read = inputStream.read(bytes, totalRead, bytes.size - totalRead)
                    if (read == -1) break
                    totalRead += read
                }
                val buffer = ByteBuffer.wrap(bytes).order(byteOrder)
                FloatArray(volumeSize) { buffer.getDouble(it * 8).toFloat() }
            }
            512 -> { // DT_UINT16 (uint16)
                val bytes = ByteArray(volumeSize * 2)
                var totalRead = 0
                while (totalRead < bytes.size) {
                    val read = inputStream.read(bytes, totalRead, bytes.size - totalRead)
                    if (read == -1) break
                    totalRead += read
                }
                val buffer = ByteBuffer.wrap(bytes).order(byteOrder)
                FloatArray(volumeSize) { (buffer.getShort(it * 2).toInt() and 0xFFFF).toFloat() }
            }
            else -> {
                println("❌ Unsupported NIfTI datatype: ${header.datatype}")
                null
            }
        }
    }
}
