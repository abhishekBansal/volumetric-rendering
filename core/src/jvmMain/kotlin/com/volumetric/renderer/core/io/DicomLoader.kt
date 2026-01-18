package com.volumetric.renderer.core.io

import com.volumetric.renderer.core.data.VolumeData
import com.volumetric.renderer.core.data.VolumeDimensions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dcm4che3.data.Attributes
import org.dcm4che3.data.Tag
import org.dcm4che3.data.VR
import org.dcm4che3.io.DicomInputStream
import org.dcm4che3.imageio.plugins.dcm.DicomImageReadParam
import org.dcm4che3.imageio.plugins.dcm.DicomImageReaderSpi
import java.awt.image.BufferedImage
import java.awt.image.DataBufferUShort
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import kotlin.math.max
import kotlin.math.min

/**
 * Metadata extracted from DICOM files.
 */
data class DicomMetadata(
    val patientName: String? = null,
    val studyDate: String? = null,
    val modality: String? = null,
    val seriesDescription: String? = null,
    val sliceThickness: Float = 1f,
    val pixelSpacing: Pair<Float, Float> = Pair(1f, 1f),
    val windowCenter: Int? = null,
    val windowWidth: Int? = null,
    val rescaleIntercept: Float = 0f,
    val rescaleSlope: Float = 1f,
    val bitsAllocated: Int = 16,
    val samplesPerPixel: Int = 1
)

/**
 * DICOM volume loader for medical imaging data.
 */
object DicomLoader {
    
    /**
     * Load a single DICOM file.
     */
    suspend fun loadSingleDicom(path: String): Result<Pair<VolumeData, DicomMetadata>> = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (!file.exists()) {
                return@withContext Result.failure(Exception("File not found: $path"))
            }
            
            DicomInputStream(file).use { dis ->
                val dataset = dis.readDataset()
                val metadata = extractMetadata(dataset)
                
                val width = dataset.getInt(Tag.Columns, 256)
                val height = dataset.getInt(Tag.Rows, 256)
                val depth = 1 // Single slice
                
                val pixelData = extractPixelData(dataset, width, height, 1, metadata)
                
                val dimensions = VolumeDimensions(width, height, depth)
                val volumeData = VolumeData(
                    dimensions = dimensions,
                    data = pixelData,
                    name = file.nameWithoutExtension
                )
                
                Result.success(Pair(volumeData, metadata))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("Failed to load DICOM file: ${e.message}", e))
        }
    }
    
    /**
     * Load a DICOM series from a directory.
     * Automatically sorts slices by instance number or slice location.
     */
    suspend fun loadDicomSeries(
        directoryPath: String,
        progressCallback: (Float) -> Unit = {}
    ): Result<Pair<VolumeData, DicomMetadata>> = withContext(Dispatchers.IO) {
        try {
            val directory = File(directoryPath)
            if (!directory.exists() || !directory.isDirectory) {
                return@withContext Result.failure(Exception("Directory not found: $directoryPath"))
            }
            
            // Find all DICOM files
            val dicomFiles = directory.listFiles { file ->
                file.isFile && (file.extension.lowercase() in listOf("dcm", "dicom", "") || 
                                isDicomFile(file))
            }?.toList() ?: emptyList()
            
            if (dicomFiles.isEmpty()) {
                return@withContext Result.failure(Exception("No DICOM files found in directory"))
            }
            
            println("Found ${dicomFiles.size} potential DICOM files")
            
            // Read first file to get dimensions and metadata
            val firstDataset = DicomInputStream(dicomFiles[0]).use { it.readDataset() }
            val metadata = extractMetadata(firstDataset)
            
            val width = firstDataset.getInt(Tag.Columns, 256)
            val height = firstDataset.getInt(Tag.Rows, 256)
            val depth = dicomFiles.size
            
            println("Volume dimensions: ${width}x${height}x${depth}")
            println("Modality: ${metadata.modality}")
            println("Bits allocated: ${metadata.bitsAllocated}")
            
            // Sort files by slice location or instance number
            val sortedFiles = dicomFiles.sortedBy { file ->
                DicomInputStream(file).use { dis ->
                    val ds = dis.readDataset()
                    ds.getDouble(Tag.SliceLocation, ds.getInt(Tag.InstanceNumber, 0).toDouble())
                }
            }
            
            // Allocate volume buffer
            val totalVoxels = width * height * depth
            val volumeBuffer = FloatArray(totalVoxels)
            
            var minValue = Float.MAX_VALUE
            var maxValue = Float.MIN_VALUE
            
            // Load all slices
            sortedFiles.forEachIndexed { sliceIndex, file ->
                DicomInputStream(file).use { dis ->
                    val dataset = dis.readDataset()
                    
                    // Print some debug info for first file
                    if (sliceIndex == 0) {
                        println("First file debug:")
                        println("  Has PixelData tag: ${dataset.contains(Tag.PixelData)}")
                        println("  Transfer Syntax: ${dataset.getString(Tag.TransferSyntaxUID)}")
                        println("  Photometric Interpretation: ${dataset.getString(Tag.PhotometricInterpretation)}")
                        println("  Samples Per Pixel: ${dataset.getInt(Tag.SamplesPerPixel, 0)}")
                    }
                    
                    // Use dcm4che's proper image reading with decompression
                    val sliceData = try {
                        // Initialize ImageIO plugins
                        javax.imageio.spi.IIORegistry.getDefaultInstance()
                            .registerServiceProvider(org.dcm4che3.imageio.plugins.dcm.DicomImageReaderSpi())
                        
                        val iis = javax.imageio.stream.FileImageInputStream(file)
                        val readers = javax.imageio.ImageIO.getImageReadersByFormatName("DICOM")
                        
                        if (readers.hasNext()) {
                            val reader = readers.next()
                            reader.input = iis
                            val param = reader.defaultReadParam as? DicomImageReadParam
                            
                            val image: BufferedImage = reader.read(0, param)
                            reader.dispose()
                            iis.close()
                            
                            println("✓ Decompressed image: ${image.width}x${image.height}, type: ${image.type}")
                            
                            // Extract pixel data from BufferedImage
                            val pixels = FloatArray(width * height)
                            
                            when (image.type) {
                                BufferedImage.TYPE_USHORT_GRAY, BufferedImage.TYPE_BYTE_GRAY -> {
                                    val raster = image.raster
                                    for (y in 0 until height) {
                                        for (x in 0 until width) {
                                            val sample = raster.getSample(x, y, 0).toFloat()
                                            val value = sample * metadata.rescaleSlope + metadata.rescaleIntercept
                                            pixels[y * width + x] = value
                                        }
                                    }
                                }
                                else -> {
                                    // Fallback: extract as RGB and convert to grayscale
                                    for (y in 0 until height) {
                                        for (x in 0 until width) {
                                            val rgb = image.getRGB(x, y)
                                            val gray = ((rgb shr 16) and 0xFF).toFloat()
                                            pixels[y * width + x] = gray * metadata.rescaleSlope + metadata.rescaleIntercept
                                        }
                                    }
                                }
                            }
                            pixels
                        } else {
                            println("⚠️  No DICOM ImageReader found, using raw extraction")
                            extractPixelData(dataset, width, height, 1, metadata, dataset.bigEndian())
                        }
                    } catch (e: Exception) {
                        println("⚠️  Image decompression failed: ${e.message}")
                        e.printStackTrace()
                        extractPixelData(dataset, width, height, 1, metadata, dataset.bigEndian())
                    }
                    
                    // Copy slice data into volume
                    val sliceOffset = sliceIndex * width * height
                    sliceData.copyInto(volumeBuffer, sliceOffset)
                    
                    // Track min/max for normalization
                    sliceData.forEach { value ->
                        minValue = min(minValue, value)
                        maxValue = max(maxValue, value)
                    }
                }
                
                progressCallback((sliceIndex + 1).toFloat() / depth)
            }
            
            println("Data range: [$minValue, $maxValue]")
            
            // Normalize to [0, 1]
            if (maxValue > minValue) {
                val range = maxValue - minValue
                println("Normalizing with range: $range")
                for (i in volumeBuffer.indices) {
                    volumeBuffer[i] = (volumeBuffer[i] - minValue) / range
                }
                println("Normalized range: [0.0, 1.0]")
            } else {
                println("⚠️  No variation in data (all same value), volume will be uniform")
            }
            
            val dimensions = VolumeDimensions(width, height, depth)
            val volumeData = VolumeData(
                dimensions = dimensions,
                data = volumeBuffer,
                name = directory.name
            )
            
            Result.success(Pair(volumeData, metadata))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("Failed to load DICOM series: ${e.message}", e))
        }
    }
    
    /**
     * Extract pixel data from DICOM dataset and normalize to [0, 1].
     */
    private fun extractPixelData(
        dataset: Attributes,
        width: Int,
        height: Int,
        depth: Int,
        metadata: DicomMetadata,
        bigEndian: Boolean = false
    ): FloatArray {
        val totalPixels = width * height * depth
        val floatData = FloatArray(totalPixels)
        
        // Get the pixel data value
        val pixelDataValue = dataset.getValue(Tag.PixelData)
        
        val bytes = when (pixelDataValue) {
            null -> {
                println("⚠️  PixelData tag not found")
                null
            }
            is ByteArray -> {
                println("✓ Found ${pixelDataValue.size} bytes of pixel data (ByteArray)")
                pixelDataValue
            }
            is org.dcm4che3.data.BulkData -> {
                println("✓ Found BulkData, trying to load")
                try {
                    // BulkData points to data outside the dataset, try to load it
                    dataset.getBytes(Tag.PixelData)
                } catch (e: Exception) {
                    println("⚠️  Could not load BulkData: ${e.message}")
                    null
                }
            }
            is org.dcm4che3.data.Fragments -> {
                println("✓ Found Fragments, count: ${pixelDataValue.size}")
                // Concatenate all fragments (skip index 0 which is offset table)
                val allBytes = mutableListOf<Byte>()
                try {
                    for (i in 1 until pixelDataValue.size) {
                        val fragment = pixelDataValue[i]
                        when (fragment) {
                            is ByteArray -> {
                                println("  Fragment $i: ${fragment.size} bytes")
                                allBytes.addAll(fragment.toList())
                            }
                            else -> println("⚠️  Skipping fragment $i of type: ${fragment?.javaClass?.name}")
                        }
                    }
                    println("  Total extracted: ${allBytes.size} bytes")
                    allBytes.toByteArray()
                } catch (e: Exception) {
                    println("⚠️  Error extracting fragments: ${e.message}")
                    null
                }
            }
            else -> {
                println("⚠️  Unknown pixel data type: ${pixelDataValue.javaClass.name}")
                null
            }
        }
        
        if (bytes == null || bytes.isEmpty()) {
            println("⚠️  No pixel data bytes available, filling with zeros")
            return floatData // Return zeros
        }
        
        when (metadata.bitsAllocated) {
            8 -> {
                // 8-bit data
                bytes.forEachIndexed { i, byte ->
                    if (i < totalPixels) {
                        floatData[i] = (byte.toInt() and 0xFF).toFloat()
                    }
                }
            }
            16 -> {
                // 16-bit data
                val buffer = ByteBuffer.wrap(bytes)
                buffer.order(if (bigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)
                
                for (i in 0 until minOf(totalPixels, bytes.size / 2)) {
                    if (buffer.remaining() >= 2) {
                        val short = buffer.short
                        // Apply rescale slope and intercept
                        val hounsfield = short * metadata.rescaleSlope + metadata.rescaleIntercept
                        floatData[i] = hounsfield
                    }
                }
            }
            else -> {
                println("⚠️  Unsupported bits allocated: ${metadata.bitsAllocated}, filling with zeros")
            }
        }
        
        return floatData
    }
    
    /**
     * Extract metadata from DICOM attributes.
     */
    private fun extractMetadata(dataset: Attributes): DicomMetadata {
        return DicomMetadata(
            patientName = dataset.getString(Tag.PatientName),
            studyDate = dataset.getString(Tag.StudyDate),
            modality = dataset.getString(Tag.Modality),
            seriesDescription = dataset.getString(Tag.SeriesDescription),
            sliceThickness = dataset.getFloat(Tag.SliceThickness, 1f),
            pixelSpacing = Pair(
                dataset.getFloat(Tag.PixelSpacing, 0, 1f),
                dataset.getFloat(Tag.PixelSpacing, 1, 1f)
            ),
            windowCenter = dataset.getInt(Tag.WindowCenter, 0).takeIf { it != 0 },
            windowWidth = dataset.getInt(Tag.WindowWidth, 0).takeIf { it != 0 },
            rescaleIntercept = dataset.getFloat(Tag.RescaleIntercept, 0f),
            rescaleSlope = dataset.getFloat(Tag.RescaleSlope, 1f),
            bitsAllocated = dataset.getInt(Tag.BitsAllocated, 16),
            samplesPerPixel = dataset.getInt(Tag.SamplesPerPixel, 1)
        )
    }
    
    /**
     * Quick check if a file is a DICOM file by reading the preamble.
     */
    private fun isDicomFile(file: File): Boolean {
        return try {
            file.inputStream().use { input ->
                val preamble = ByteArray(132)
                input.read(preamble)
                // Check for "DICM" magic bytes at offset 128
                preamble[128] == 'D'.code.toByte() &&
                preamble[129] == 'I'.code.toByte() &&
                preamble[130] == 'C'.code.toByte() &&
                preamble[131] == 'M'.code.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }
}
