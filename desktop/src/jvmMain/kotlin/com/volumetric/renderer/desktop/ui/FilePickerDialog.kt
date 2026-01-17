package com.volumetric.renderer.desktop.ui

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter

/**
 * Native file picker dialog for selecting datasets.
 * Uses AWT FileDialog for native macOS/Windows appearance.
 */
object FilePickerDialog {
    
    /**
     * Opens a native file/directory picker for dataset selection.
     * Supports .dcm, .dicom, .nii, .nii.gz files and directories containing DICOM series.
     * 
     * @param title Dialog title
     * @return Selected File or null if cancelled
     */
    fun showOpenDialog(title: String = "Open Dataset"): File? {
        println("[FilePickerDialog] Opening file dialog...")
        
        // Use AWT FileDialog for native appearance
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        
        // Set file filter to show only supported formats
        dialog.filenameFilter = FilenameFilter { dir, name ->
            val nameLower = name.lowercase()
            nameLower.endsWith(".dcm") || 
                nameLower.endsWith(".dicom") || 
                nameLower.endsWith(".nii") || 
                nameLower.endsWith(".nii.gz") ||
                // Also allow directories (for DICOM series)
                File(dir, name).isDirectory
        }
        
        // Show the dialog (blocks until user selects or cancels)
        println("[FilePickerDialog] Dialog shown, waiting for user selection...")
        dialog.isVisible = true
        
        // Get selected file
        val selectedFile = dialog.file
        val selectedDir = dialog.directory
        
        println("[FilePickerDialog] User selection - file: $selectedFile, dir: $selectedDir")
        
        if (selectedFile != null && selectedDir != null) {
            val file = File(selectedDir, selectedFile)
            println("[FilePickerDialog] Selected file path: ${file.absolutePath}")
            println("[FilePickerDialog] File exists: ${file.exists()}, isFile: ${file.isFile}, isDirectory: ${file.isDirectory}")
            
            return file
        }
        
        println("[FilePickerDialog] No file selected (cancelled)")
        return null
    }
    
    /**
     * Checks if a file is a DICOM file by reading the preamble
     */
    private fun isDicomFile(file: File): Boolean {
        return try {
            file.inputStream().use { input ->
                val preamble = ByteArray(132)
                val bytesRead = input.read(preamble)
                bytesRead == 132 && 
                preamble[128] == 'D'.code.toByte() && 
                preamble[129] == 'I'.code.toByte() &&
                preamble[130] == 'C'.code.toByte() && 
                preamble[131] == 'M'.code.toByte()
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Validates if a file or directory is a supported dataset format.
     * 
     * @param file File or directory to validate
     * @return Pair of (isValid, errorMessage)
     */
    fun validateDatasetFile(file: File): Pair<Boolean, String?> {
        println("[FilePickerDialog] Validating file: ${file.absolutePath}")
        
        if (!file.exists()) {
            println("[FilePickerDialog] ❌ File does not exist")
            return false to "File or directory does not exist"
        }
        
        // Check if it's a directory (potential DICOM series)
        if (file.isDirectory) {
            println("[FilePickerDialog] Checking directory for DICOM files...")
            val dicomFiles = file.listFiles { f ->
                f.isFile && (f.extension.lowercase() in listOf("dcm", "dicom", "") || isDicomFile(f))
            }
            
            if (dicomFiles.isNullOrEmpty()) {
                println("[FilePickerDialog] ❌ No DICOM files found in directory")
                return false to "Directory does not contain any DICOM files (.dcm, .dicom)"
            }
            
            println("[FilePickerDialog] ✅ Validated DICOM directory with ${dicomFiles.size} files")
            return true to null
        }
        
        // Check file extension
        val extension = file.extension.lowercase()
        val name = file.name.lowercase()
        
        println("[FilePickerDialog] File extension: '$extension', full name: '$name'")
        
        val isSupported = when {
            extension in listOf("dcm", "dicom") -> {
                println("[FilePickerDialog] Matched DICOM extension")
                true
            }
            extension == "nii" -> {
                println("[FilePickerDialog] Matched NIfTI extension (.nii)")
                true
            }
            name.endsWith(".nii.gz") -> {
                println("[FilePickerDialog] Matched compressed NIfTI (.nii.gz)")
                true
            }
            extension == "gz" && name.endsWith(".nii.gz") -> {
                println("[FilePickerDialog] Matched .gz with .nii.gz name")
                true
            }
            else -> {
                println("[FilePickerDialog] No matching extension found")
                false
            }
        }
        
        if (!isSupported) {
            println("[FilePickerDialog] ❌ Unsupported file format")
            return false to "Unsupported file format. Supported formats: .dcm, .dicom, .nii, .nii.gz, or directories containing DICOM files"
        }
        
        println("[FilePickerDialog] ✅ Validated file: ${file.name}")
        return true to null
    }
}
