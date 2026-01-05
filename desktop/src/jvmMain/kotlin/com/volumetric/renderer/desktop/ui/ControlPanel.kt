package com.volumetric.renderer.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.volumetric.renderer.core.data.TransferFunction
import com.volumetric.renderer.core.utils.CPUHistogramCalculator
import com.volumetric.renderer.core.utils.TransferFunctionPresets
import com.volumetric.renderer.core.data.VolumeData
import com.volumetric.renderer.core.math.Vector3

/**
 * Main control panel for the volumetric renderer
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlPanel(
    volumeData: VolumeData?,
    transferFunction: TransferFunction,
    presets: List<TransferFunction>,
    onTransferFunctionChange: (TransferFunction) -> Unit,
    materialAmbient: Float,
    onMaterialAmbientChange: (Float) -> Unit,
    materialDiffuse: Float,
    onMaterialDiffuseChange: (Float) -> Unit,
    materialSpecular: Float,
    onMaterialSpecularChange: (Float) -> Unit,
    materialShininess: Float,
    onMaterialShininessChange: (Float) -> Unit,
    lightColor: Color,
    onLightColorChange: (Color) -> Unit,
    ambientLightColor: Color,
    onAmbientLightColorChange: (Color) -> Unit,
    lightPosition: Vector3,
    onLightPositionChange: (Vector3) -> Unit,
    stepSize: Float,
    onStepSizeChange: (Float) -> Unit,
    maxSteps: Int,
    onMaxStepsChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val histogram = remember(volumeData) {
        volumeData?.let {
            val calculator = CPUHistogramCalculator()
            calculator.calculateHistogram(it, 256)
        }
    }
    
    var selectedPreset by remember { mutableStateOf(0) }
    
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = "Volumetric Renderer Controls",
                style = MaterialTheme.typography.titleLarge
            )
            
            Divider()
            
            // Preset Selector
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Transfer Function Presets",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    var expanded by remember { mutableStateOf(false) }
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = transferFunction.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Preset") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            presets.forEachIndexed { index, preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.name) },
                                    onClick = {
                                        selectedPreset = index
                                        onTransferFunctionChange(preset)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
            
            // Transfer Function Editor
            TransferFunctionEditor(
                transferFunction = transferFunction,
                onTransferFunctionChange = onTransferFunctionChange,
                histogram = histogram,
                modifier = Modifier.fillMaxWidth()
            )
            
            // Material Properties
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Material Properties",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Text("Ambient: ${"%.2f".format(materialAmbient)}")
                    Slider(
                        value = materialAmbient,
                        onValueChange = onMaterialAmbientChange,
                        valueRange = 0f..1f
                    )
                    
                    Text("Diffuse: ${"%.2f".format(materialDiffuse)}")
                    Slider(
                        value = materialDiffuse,
                        onValueChange = onMaterialDiffuseChange,
                        valueRange = 0f..1f
                    )
                    
                    Text("Specular: ${"%.2f".format(materialSpecular)}")
                    Slider(
                        value = materialSpecular,
                        onValueChange = onMaterialSpecularChange,
                        valueRange = 0f..1f
                    )
                    
                    Text("Shininess: ${materialShininess.toInt()}")
                    Slider(
                        value = materialShininess,
                        onValueChange = onMaterialShininessChange,
                        valueRange = 1f..128f
                    )
                }
            }
            
            // Lighting Properties
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Lighting Properties",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    
                    // Light Color
                    ColorPickerRow("Light Color", lightColor, onLightColorChange)
                    
                    // Ambient Light Color
                    ColorPickerRow("Ambient Color", ambientLightColor, onAmbientLightColorChange)
                    
                    Spacer(Modifier.height(8.dp))
                    Text("Light Position")
                    Column {
                        CompactSlider("X", lightPosition.x, -5f..5f) { onLightPositionChange(lightPosition.copy(x = it)) }
                        CompactSlider("Y", lightPosition.y, -5f..5f) { onLightPositionChange(lightPosition.copy(y = it)) }
                        CompactSlider("Z", lightPosition.z, -5f..5f) { onLightPositionChange(lightPosition.copy(z = it)) }
                    }
                }
            }

            // Rendering Settings
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Rendering Settings",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    
                    Text("Step Size: ${String.format("%.4f", stepSize)}")
                    Slider(
                        value = stepSize,
                        onValueChange = onStepSizeChange,
                        valueRange = 0.001f..0.02f
                    )
                    
                    Text("Max Steps: $maxSteps")
                    Slider(
                        value = maxSteps.toFloat(),
                        onValueChange = { onMaxStepsChange(it.toInt()) },
                        valueRange = 100f..2000f
                    )
                }
            }
            
            // Volume Info
            volumeData?.let { volume ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Volume Information",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Dimensions: ${volume.dimensions.width} × ${volume.dimensions.height} × ${volume.dimensions.depth}")
                        Text("Total voxels: ${volume.dimensions.width * volume.dimensions.height * volume.dimensions.depth}")
                        Text("Data size: ${volume.data.size / 1024 / 1024} MB")
                    }
                }
            }
            
            // Keyboard Shortcuts
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Keyboard Shortcuts",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    KeyboardShortcut("W/A/S/D", "Move camera")
                    KeyboardShortcut("Mouse Drag", "Rotate view")
                    KeyboardShortcut("Scroll", "Zoom in/out")
                    KeyboardShortcut("P", "Cycle presets")
                    KeyboardShortcut("D", "Debug mode")
                    KeyboardShortcut("ESC", "Exit")
                }
            }
        }
    }
}

@Composable
private fun KeyboardShortcut(key: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(key, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ColorPickerRow(
    label: String,
    color: Color,
    onColorChange: (Color) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(label)
        Button(
            onClick = { showDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = color),
            modifier = Modifier.size(40.dp),
            contentPadding = PaddingValues(0.dp)
        ) {}
    }
    
    if (showDialog) {
        ColorPickerDialog(
            initialColor = color,
            onDismiss = { showDialog = false },
            onColorSelected = { 
                onColorChange(it)
                showDialog = false 
            }
        )
    }
}

@Composable
private fun CompactSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = Modifier.height(32.dp)
    ) {
        Text(
            text = "$label: ${String.format("%.1f", value)}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(60.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f)
        )
    }
}
