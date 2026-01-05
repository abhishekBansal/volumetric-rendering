package com.volumetric.renderer.desktop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.volumetric.renderer.core.data.GradientStop
import com.volumetric.renderer.core.data.TransferFunction
import kotlin.math.absoluteValue

/**
 * Professional Transfer Function Editor with gradient stops and opacity curve editing
 */
@Composable
fun TransferFunctionEditor(
    transferFunction: TransferFunction,
    onTransferFunctionChange: (TransferFunction) -> Unit,
    histogram: IntArray? = null,
    modifier: Modifier = Modifier
) {
    var selectedStopIndex by remember { mutableStateOf<Int?>(null) }
    var showColorPicker by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transfer Function Editor",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { /* TODO: Reset */ }) {
                        Icon(Icons.Default.Refresh, "Reset")
                    }
                    IconButton(onClick = { /* TODO: Save preset */ }) {
                        Icon(Icons.Default.Save, "Save")
                    }
                    IconButton(onClick = { /* TODO: Load preset */ }) {
                        Icon(Icons.Default.FolderOpen, "Load")
                    }
                }
            }
            
            Divider()
            
            // Gradient Preview
            GradientPreview(
                gradientStops = transferFunction.gradientStops,
                modifier = Modifier.fillMaxWidth().height(60.dp)
            )
            
            // Opacity Curve Editor with Histogram
            OpacityCurveEditor(
                gradientStops = transferFunction.gradientStops,
                histogram = histogram,
                selectedStopIndex = selectedStopIndex,
                onStopSelected = { selectedStopIndex = it },
                onStopMoved = { index, newPosition ->
                    val stops = transferFunction.gradientStops.toMutableList()
                    val stop = stops[index]
                    stops[index] = stop.copy(position = newPosition.coerceIn(0f, 1f))
                    stops.sortBy { it.position }
                    onTransferFunctionChange(transferFunction.copy(gradientStops = stops))
                },
                onStopOpacityChanged = { index, newOpacity ->
                    val stops = transferFunction.gradientStops.toMutableList()
                    val stop = stops[index]
                    stops[index] = stop.copy(alpha = newOpacity.coerceIn(0f, 1f))
                    onTransferFunctionChange(transferFunction.copy(gradientStops = stops))
                },
                onAddStop = { position ->
                    val stops = transferFunction.gradientStops.toMutableList()
                    // Interpolate color and opacity at position
                    val interpolatedStop = interpolateStop(transferFunction.gradientStops, position)
                    stops.add(interpolatedStop)
                    stops.sortBy { it.position }
                    onTransferFunctionChange(transferFunction.copy(gradientStops = stops))
                    selectedStopIndex = stops.indexOf(interpolatedStop)
                },
                onDeleteStop = { index ->
                    if (transferFunction.gradientStops.size > 2) {
                        val stops = transferFunction.gradientStops.toMutableList()
                        stops.removeAt(index)
                        onTransferFunctionChange(transferFunction.copy(gradientStops = stops))
                        selectedStopIndex = null
                    }
                },
                modifier = Modifier.fillMaxWidth().height(250.dp)
            )
            
            Divider()
            
            // Selected Stop Controls
            selectedStopIndex?.let { index ->
                if (index < transferFunction.gradientStops.size) {
                    val stop = transferFunction.gradientStops[index]
                    
                    SelectedStopControls(
                        stop = stop,
                        onColorClick = { showColorPicker = true },
                        onPositionChange = { newPos ->
                            val stops = transferFunction.gradientStops.toMutableList()
                            stops[index] = stop.copy(position = newPos.coerceIn(0f, 1f))
                            stops.sortBy { it.position }
                            onTransferFunctionChange(transferFunction.copy(gradientStops = stops))
                        },
                        onOpacityChange = { newAlpha ->
                            val stops = transferFunction.gradientStops.toMutableList()
                            stops[index] = stop.copy(alpha = newAlpha.coerceIn(0f, 1f))
                            onTransferFunctionChange(transferFunction.copy(gradientStops = stops))
                        },
                        onDelete = {
                            if (transferFunction.gradientStops.size > 2) {
                                val stops = transferFunction.gradientStops.toMutableList()
                                stops.removeAt(index)
                                onTransferFunctionChange(transferFunction.copy(gradientStops = stops))
                                selectedStopIndex = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
    
    // Color Picker Dialog
    if (showColorPicker && selectedStopIndex != null) {
        ColorPickerDialog(
            initialColor = transferFunction.gradientStops[selectedStopIndex!!].let {
                Color(it.r, it.g, it.b, it.alpha)
            },
            onDismiss = { showColorPicker = false },
            onColorSelected = { color ->
                val stops = transferFunction.gradientStops.toMutableList()
                val stop = stops[selectedStopIndex!!]
                stops[selectedStopIndex!!] = stop.copy(
                    red = color.red,
                    green = color.green,
                    blue = color.blue
                )
                onTransferFunctionChange(transferFunction.copy(gradientStops = stops))
                showColorPicker = false
            }
        )
    }
}

@Composable
private fun GradientPreview(
    gradientStops: List<GradientStop>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        // Create gradient brush
        val colors = gradientStops.map { stop ->
            Color(stop.r, stop.g, stop.b, 1f)
        }
        val positions = gradientStops.map { it.position }
        
        val brush = Brush.horizontalGradient(
            colors = colors,
            startX = 0f,
            endX = width
        )
        
        drawRect(
            brush = brush,
            size = Size(width, height)
        )
        
        // Draw stop markers
        gradientStops.forEach { stop ->
            val x = stop.position * width
            drawCircle(
                color = Color.White,
                radius = 6f,
                center = Offset(x, height / 2),
                style = Stroke(width = 2f)
            )
            drawCircle(
                color = Color(stop.r, stop.g, stop.b),
                radius = 4f,
                center = Offset(x, height / 2)
            )
        }
    }
}

@Composable
private fun OpacityCurveEditor(
    gradientStops: List<GradientStop>,
    histogram: IntArray?,
    selectedStopIndex: Int?,
    onStopSelected: (Int?) -> Unit,
    onStopMoved: (Int, Float) -> Unit,
    onStopOpacityChanged: (Int, Float) -> Unit,
    onAddStop: (Float) -> Unit,
    onDeleteStop: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var draggedStopIndex by remember { mutableStateOf<Int?>(null) }
    
    val density = LocalDensity.current
    
    Canvas(
        modifier = modifier
            .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val width = size.width
                        val height = size.height
                        val hitThreshold = with(density) { 30.dp.toPx() }
                        
                        // Check if clicked on a stop
                        val clickedStopIndex = gradientStops.indexOfFirst { stop ->
                            val x = stop.position * width
                            val y = (1f - stop.alpha) * height
                            val dx = offset.x - x
                            val dy = offset.y - y
                            val distance = kotlin.math.sqrt(dx*dx + dy*dy)
                            distance < hitThreshold
                        }
                        
                        if (clickedStopIndex >= 0) {
                            onStopSelected(clickedStopIndex)
                        } else {
                            // Add new stop at click position
                            val position = (offset.x / width).coerceIn(0f, 1f)
                            onAddStop(position)
                        }
                    },
                    onDoubleTap = { offset ->
                        val width = size.width
                        val height = size.height
                        val hitThreshold = with(density) { 30.dp.toPx() }
                        
                        // Check if double-clicked on a stop to delete
                        val clickedStopIndex = gradientStops.indexOfFirst { stop ->
                            val x = stop.position * width
                            val y = (1f - stop.alpha) * height
                            val dx = offset.x - x
                            val dy = offset.y - y
                            val distance = kotlin.math.sqrt(dx*dx + dy*dy)
                            distance < hitThreshold
                        }
                        
                        if (clickedStopIndex >= 0 && gradientStops.size > 2) {
                            onDeleteStop(clickedStopIndex)
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val width = size.width
                        val height = size.height
                        val hitThreshold = with(density) { 30.dp.toPx() }
                        
                        // Find closest stop
                        draggedStopIndex = gradientStops.indexOfFirst { stop ->
                            val x = stop.position * width
                            val y = (1f - stop.alpha) * height
                            val dx = offset.x - x
                            val dy = offset.y - y
                            val distance = kotlin.math.sqrt(dx*dx + dy*dy)
                            distance < hitThreshold
                        }
                        val foundIndex = draggedStopIndex
                        if (foundIndex != null && foundIndex >= 0) {
                            onStopSelected(foundIndex)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        val index = draggedStopIndex
                        if (index != null && index >= 0) {
                            val width = size.width
                            val height = size.height
                            
                            // Update position and opacity
                            val currentStop = gradientStops[index]
                            val newPosition = currentStop.position + (dragAmount.x / width)
                            val newOpacity = currentStop.alpha - (dragAmount.y / height)
                            
                            onStopMoved(index, newPosition)
                            onStopOpacityChanged(index, newOpacity)
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        draggedStopIndex = null
                    }
                )
            }
    ) {
        val width = size.width
        val height = size.height
        
        // Draw histogram if available
        histogram?.let { hist ->
            val maxCount = hist.maxOrNull() ?: 1
            hist.forEachIndexed { index, count ->
                val x = (index.toFloat() / hist.size) * width
                val barHeight = (count.toFloat() / maxCount) * height * 0.3f
                drawLine(
                    color = Color(0xFF404040),
                    start = Offset(x, height),
                    end = Offset(x, height - barHeight),
                    strokeWidth = width / hist.size
                )
            }
        }
        
        // Draw grid
        for (i in 0..10) {
            val y = (i / 10f) * height
            drawLine(
                color = Color(0xFF303030),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
        }
        
        // Draw opacity curve
        val path = Path()
        gradientStops.forEachIndexed { index, stop ->
            val x = stop.position * width
            val y = (1f - stop.alpha) * height
            
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        drawPath(
            path = path,
            color = Color.Cyan,
            style = Stroke(width = 3f)
        )
        
        // Draw stops
        gradientStops.forEachIndexed { index, stop ->
            val x = stop.position * width
            val y = (1f - stop.alpha) * height
            val isSelected = index == selectedStopIndex
            val isDragged = index == draggedStopIndex
            
            // Stop circle
            drawCircle(
                color = if (isSelected || isDragged) Color.Yellow else Color.White,
                radius = if (isSelected || isDragged) 10f else 7f,
                center = Offset(x, y),
                style = Stroke(width = 2f)
            )
            drawCircle(
                color = Color(stop.r, stop.g, stop.b, stop.alpha),
                radius = if (isSelected || isDragged) 8f else 5f,
                center = Offset(x, y)
            )
        }
        
    }
}

@Composable
private fun SelectedStopControls(
    stop: GradientStop,
    onColorClick: () -> Unit,
    onPositionChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Selected Stop",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        
        // Color selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Color:")
            Button(
                onClick = onColorClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(stop.r, stop.g, stop.b)
                ),
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp)
            ) {}
        }
        
        // Position slider
        Column {
            Text("Position: ${String.format("%.2f", stop.position)}")
            Slider(
                value = stop.position,
                onValueChange = onPositionChange,
                valueRange = 0f..1f
            )
        }
        
        // Opacity slider
        Column {
            Text("Opacity: ${String.format("%.2f", stop.alpha)}")
            Slider(
                value = stop.alpha,
                onValueChange = onOpacityChange,
                valueRange = 0f..1f
            )
        }
        
        // Delete button
        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.Delete, "Delete")
            Spacer(Modifier.width(8.dp))
            Text("Delete Stop")
        }
    }
}

@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    var selectedColor by remember { mutableStateOf(initialColor) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.width(400.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Select Color",
                    style = MaterialTheme.typography.titleLarge
                )
                
                // Simple RGB sliders (can be replaced with a proper color picker library)
                ColorSlider(
                    label = "Red",
                    value = selectedColor.red,
                    onValueChange = { selectedColor = selectedColor.copy(red = it) },
                    color = Color.Red
                )
                ColorSlider(
                    label = "Green",
                    value = selectedColor.green,
                    onValueChange = { selectedColor = selectedColor.copy(green = it) },
                    color = Color.Green
                )
                ColorSlider(
                    label = "Blue",
                    value = selectedColor.blue,
                    onValueChange = { selectedColor = selectedColor.copy(blue = it) },
                    color = Color.Blue
                )
                
                // Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(selectedColor, RoundedCornerShape(8.dp))
                )
                
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = { onColorSelected(selectedColor) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    color: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label)
            Text(String.format("%.2f", value))
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color
            )
        )
    }
}

/**
 * Interpolate color and opacity at a given position
 */
private fun interpolateStop(stops: List<GradientStop>, position: Float): GradientStop {
    if (stops.isEmpty()) return GradientStop(position, 0.5f, 0.5f, 0.5f, 0.5f)
    
    // Find surrounding stops
    val before = stops.lastOrNull { it.position <= position } ?: stops.first()
    val after = stops.firstOrNull { it.position >= position } ?: stops.last()
    
    if (before == after) return before.copy(position = position)
    
    // Linear interpolation
    val t = (position - before.position) / (after.position - before.position)
    
    return GradientStop(
        position = position,
        red = before.red + (after.red - before.red) * t,
        green = before.green + (after.green - before.green) * t,
        blue = before.blue + (after.blue - before.blue) * t,
        alpha = before.alpha + (after.alpha - before.alpha) * t
    )
}
