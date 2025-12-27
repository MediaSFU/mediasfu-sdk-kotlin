package com.mediasfu.sdk.ui.components.whiteboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mediasfu.sdk.model.LineType
import com.mediasfu.sdk.model.WhiteboardMode
import com.mediasfu.sdk.model.WhiteboardShapeType

/**
 * Options for WhiteboardToolbar component.
 */
data class WhiteboardToolbarOptions(
    val currentMode: WhiteboardMode = WhiteboardMode.PAN,
    val currentShapeType: WhiteboardShapeType = WhiteboardShapeType.RECTANGLE,
    val currentColor: Color = Color.Black,
    val brushThickness: Float = 6f,
    val lineThickness: Float = 6f,
    val eraserThickness: Float = 10f,
    val lineType: LineType = LineType.SOLID,
    val fontSize: Float = 20f,
    val useImageBackground: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val hasSelectedShape: Boolean = false,
    val onModeChanged: (WhiteboardMode) -> Unit = {},
    val onShapeTypeChanged: (WhiteboardShapeType) -> Unit = {},
    val onColorChanged: (Color) -> Unit = {},
    val onBrushThicknessChanged: (Float) -> Unit = {},
    val onLineThicknessChanged: (Float) -> Unit = {},
    val onEraserThicknessChanged: (Float) -> Unit = {},
    val onLineTypeChanged: (LineType) -> Unit = {},
    val onFontSizeChanged: (Float) -> Unit = {},
    val onUndo: () -> Unit = {},
    val onRedo: () -> Unit = {},
    val onDeleteShape: (() -> Unit)? = null,
    val onClear: () -> Unit = {},
    val onZoomIn: () -> Unit = {},
    val onZoomOut: () -> Unit = {},
    val onResetZoom: () -> Unit = {},
    val onToggleBackground: () -> Unit = {},
    val onSave: (() -> Unit)? = null,
    val onUploadImage: (() -> Unit)? = null,
    val onToggleToolbar: () -> Unit = {}
)

/**
 * Toolbar widget for the whiteboard providing drawing tools and controls.
 *
 * This widget renders a horizontal toolbar with buttons for:
 * - Drawing mode selection (pan, draw, freehand, shapes, text, erase, select)
 * - Shape type selection
 * - Color picker
 * - Thickness controls
 * - Line type selection
 * - Undo/redo
 * - Zoom controls
 * - Background toggle
 */
@Composable
fun WhiteboardToolbar(
    options: WhiteboardToolbarOptions,
    modifier: Modifier = Modifier
) {
    var showColorPicker by remember { mutableStateOf(false) }
    var showThicknessMenu by remember { mutableStateOf(false) }
    var showLineTypeMenu by remember { mutableStateOf(false) }
    var showShapeMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp),
        color = Color.White
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Row 1: Drawing tools
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mode buttons - compact
                CompactModeButton(
                    icon = Icons.Default.PanTool,
                    label = "Pan",
                    isSelected = options.currentMode == WhiteboardMode.PAN,
                    onClick = { options.onModeChanged(WhiteboardMode.PAN) }
                )

                CompactModeButton(
                    icon = Icons.Default.TouchApp,
                    label = "Select",
                    isSelected = options.currentMode == WhiteboardMode.SELECT,
                    onClick = { options.onModeChanged(WhiteboardMode.SELECT) }
                )

                CompactModeButton(
                    icon = Icons.Default.Edit,
                    label = "Line",
                    isSelected = options.currentMode == WhiteboardMode.DRAW,
                    onClick = { options.onModeChanged(WhiteboardMode.DRAW) }
                )

                CompactModeButton(
                    icon = Icons.Default.Gesture,
                    label = "Free",
                    isSelected = options.currentMode == WhiteboardMode.FREEHAND,
                    onClick = { options.onModeChanged(WhiteboardMode.FREEHAND) }
                )

                // Shape dropdown
                Box {
                    CompactModeButton(
                        icon = Icons.Default.Category,
                        label = "Shape",
                        isSelected = options.currentMode == WhiteboardMode.SHAPE,
                        onClick = { showShapeMenu = true }
                    )
                    DropdownMenu(
                        expanded = showShapeMenu,
                        onDismissRequest = { showShapeMenu = false }
                    ) {
                        ShapeMenuItem(WhiteboardShapeType.RECTANGLE, "Rect", options.currentShapeType) {
                            options.onShapeTypeChanged(it)
                            options.onModeChanged(WhiteboardMode.SHAPE)
                            showShapeMenu = false
                        }
                        ShapeMenuItem(WhiteboardShapeType.CIRCLE, "Circle", options.currentShapeType) {
                            options.onShapeTypeChanged(it)
                            options.onModeChanged(WhiteboardMode.SHAPE)
                            showShapeMenu = false
                        }
                        ShapeMenuItem(WhiteboardShapeType.TRIANGLE, "Triangle", options.currentShapeType) {
                            options.onShapeTypeChanged(it)
                            options.onModeChanged(WhiteboardMode.SHAPE)
                            showShapeMenu = false
                        }
                        ShapeMenuItem(WhiteboardShapeType.LINE, "Line", options.currentShapeType) {
                            options.onShapeTypeChanged(it)
                            options.onModeChanged(WhiteboardMode.SHAPE)
                            showShapeMenu = false
                        }
                        ShapeMenuItem(WhiteboardShapeType.OVAL, "Oval", options.currentShapeType) {
                            options.onShapeTypeChanged(it)
                            options.onModeChanged(WhiteboardMode.SHAPE)
                            showShapeMenu = false
                        }
                    }
                }

                CompactModeButton(
                    icon = Icons.Default.TextFields,
                    label = "Text",
                    isSelected = options.currentMode == WhiteboardMode.TEXT,
                    onClick = { options.onModeChanged(WhiteboardMode.TEXT) }
                )

                CompactModeButton(
                    icon = Icons.Default.Remove,
                    label = "Erase",
                    isSelected = options.currentMode == WhiteboardMode.ERASE,
                    onClick = { options.onModeChanged(WhiteboardMode.ERASE) }
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Color picker
                Box {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(options.currentColor)
                            .border(2.dp, Color.Gray, CircleShape)
                            .clickable { showColorPicker = true }
                    )
                    DropdownMenu(
                        expanded = showColorPicker,
                        onDismissRequest = { showColorPicker = false }
                    ) {
                        ColorPalette(
                            selectedColor = options.currentColor,
                            onColorSelected = {
                                options.onColorChanged(it)
                                showColorPicker = false
                            }
                        )
                    }
                }

                // Thickness
                Box {
                    IconButton(onClick = { showThicknessMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.LineWeight, contentDescription = "Thickness", modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(
                        expanded = showThicknessMenu,
                        onDismissRequest = { showThicknessMenu = false }
                    ) {
                        ThicknessSelector(
                            currentMode = options.currentMode,
                            brushThickness = options.brushThickness,
                            lineThickness = options.lineThickness,
                            eraserThickness = options.eraserThickness,
                            fontSize = options.fontSize,
                            onBrushThicknessChanged = options.onBrushThicknessChanged,
                            onLineThicknessChanged = options.onLineThicknessChanged,
                            onEraserThicknessChanged = options.onEraserThicknessChanged,
                            onFontSizeChanged = options.onFontSizeChanged
                        )
                    }
                }

                // Line type
                Box {
                    IconButton(onClick = { showLineTypeMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.LinearScale, contentDescription = "Line Type", modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(
                        expanded = showLineTypeMenu,
                        onDismissRequest = { showLineTypeMenu = false }
                    ) {
                        LineTypeSelector(
                            currentLineType = options.lineType,
                            onLineTypeChanged = {
                                options.onLineTypeChanged(it)
                                showLineTypeMenu = false
                            }
                        )
                    }
                }
            }

            // Row 2: Actions (Undo, Redo, Save, Upload, Zoom, etc.)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Undo
                CompactActionButton(
                    icon = Icons.Default.Undo,
                    label = "Undo",
                    enabled = options.canUndo,
                    onClick = options.onUndo
                )

                // Redo
                CompactActionButton(
                    icon = Icons.Default.Redo,
                    label = "Redo",
                    enabled = options.canRedo,
                    onClick = options.onRedo
                )

                // Delete
                if (options.onDeleteShape != null) {
                    CompactActionButton(
                        icon = Icons.Default.Delete,
                        label = "Del",
                        enabled = options.hasSelectedShape,
                        onClick = { options.onDeleteShape.invoke() }
                    )
                }

                // Clear
                CompactActionButton(
                    icon = Icons.Default.Clear,
                    label = "Clear",
                    onClick = options.onClear
                )

                // Share
                options.onSave?.let { onShare ->
                    CompactActionButton(
                        icon = Icons.Default.Share,
                        label = "Share",
                        onClick = onShare
                    )
                }

                // Upload
                options.onUploadImage?.let { onUpload ->
                    CompactActionButton(
                        icon = Icons.Default.Upload,
                        label = "Upload",
                        onClick = onUpload
                    )
                }

                // Zoom controls
                CompactActionButton(
                    icon = Icons.Default.ZoomIn,
                    label = "+",
                    onClick = options.onZoomIn
                )

                CompactActionButton(
                    icon = Icons.Default.ZoomOut,
                    label = "-",
                    onClick = options.onZoomOut
                )

                // Background toggle
                CompactActionButton(
                    icon = if (options.useImageBackground) Icons.Default.Image else Icons.Default.GridOn,
                    label = "BG",
                    onClick = options.onToggleBackground
                )

                // Hide toolbar
                CompactActionButton(
                    icon = Icons.Default.KeyboardArrowUp,
                    label = "Hide",
                    onClick = options.onToggleToolbar
                )
            }
        }
    }
}

@Composable
private fun ToolbarModeButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .padding(2.dp)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray
        )
    }
}

@Composable
private fun ToolbarActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.padding(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) Color.Gray else Color.LightGray
        )
    }
}

@Composable
private fun ToolbarDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(24.dp)
            .background(Color.LightGray)
    )
}

@Composable
private fun ShapeMenuItem(
    shapeType: WhiteboardShapeType,
    label: String,
    currentShapeType: WhiteboardShapeType,
    onSelected: (WhiteboardShapeType) -> Unit
) {
    DropdownMenuItem(
        text = { 
            Text(
                text = label,
                color = if (shapeType == currentShapeType) MaterialTheme.colorScheme.primary else Color.Unspecified
            )
        },
        onClick = { onSelected(shapeType) },
        leadingIcon = {
            if (shapeType == currentShapeType) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

@Composable
private fun ColorPalette(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit
) {
    val colors = listOf(
        Color.Black, Color.White, Color.Gray,
        Color.Red, Color(0xFFFF5722), Color.Yellow,
        Color.Green, Color(0xFF4CAF50), Color.Cyan,
        Color.Blue, Color(0xFF3F51B5), Color(0xFF9C27B0),
        Color(0xFFE91E63), Color(0xFF795548), Color(0xFF607D8B)
    )

    Column(modifier = Modifier.padding(8.dp)) {
        Text("Select Color", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        
        for (row in colors.chunked(5)) {
            Row {
                for (color in row) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (color == selectedColor) 3.dp else 1.dp,
                                color = if (color == selectedColor) MaterialTheme.colorScheme.primary else Color.Gray,
                                shape = CircleShape
                            )
                            .clickable { onColorSelected(color) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ThicknessSelector(
    currentMode: WhiteboardMode,
    brushThickness: Float,
    lineThickness: Float,
    eraserThickness: Float,
    fontSize: Float,
    onBrushThicknessChanged: (Float) -> Unit,
    onLineThicknessChanged: (Float) -> Unit,
    onEraserThicknessChanged: (Float) -> Unit,
    onFontSizeChanged: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        when (currentMode) {
            WhiteboardMode.FREEHAND -> {
                Text("Brush Thickness: ${brushThickness.toInt()}")
                Slider(
                    value = brushThickness,
                    onValueChange = onBrushThicknessChanged,
                    valueRange = 1f..30f
                )
            }
            WhiteboardMode.DRAW, WhiteboardMode.SHAPE -> {
                Text("Line Thickness: ${lineThickness.toInt()}")
                Slider(
                    value = lineThickness,
                    onValueChange = onLineThicknessChanged,
                    valueRange = 1f..30f
                )
            }
            WhiteboardMode.ERASE -> {
                Text("Eraser Size: ${eraserThickness.toInt()}")
                Slider(
                    value = eraserThickness,
                    onValueChange = onEraserThicknessChanged,
                    valueRange = 5f..50f
                )
            }
            WhiteboardMode.TEXT -> {
                Text("Font Size: ${fontSize.toInt()}")
                Slider(
                    value = fontSize,
                    onValueChange = onFontSizeChanged,
                    valueRange = 8f..72f
                )
            }
            else -> {
                Text("Line Thickness: ${lineThickness.toInt()}")
                Slider(
                    value = lineThickness,
                    onValueChange = onLineThicknessChanged,
                    valueRange = 1f..30f
                )
            }
        }
    }
}

@Composable
private fun LineTypeSelector(
    currentLineType: LineType,
    onLineTypeChanged: (LineType) -> Unit
) {
    Column(modifier = Modifier.padding(8.dp)) {
        Text("Line Type", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        
        LineType.values().forEach { lineType ->
            DropdownMenuItem(
                text = { 
                    Text(
                        text = when (lineType) {
                            LineType.SOLID -> "Solid"
                            LineType.DASHED -> "Dashed"
                            LineType.DOTTED -> "Dotted"
                            LineType.DASH_DOT -> "Dash-Dot"
                        },
                        color = if (lineType == currentLineType) MaterialTheme.colorScheme.primary else Color.Unspecified
                    )
                },
                onClick = { onLineTypeChanged(lineType) },
                leadingIcon = {
                    if (lineType == currentLineType) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    }
}

/**
 * Compact mode button for drawing tools (Row 1)
 */
@Composable
private fun CompactModeButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(36.dp)
            .padding(2.dp)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray
        )
    }
}

/**
 * Compact action button for toolbar actions (Row 2)
 */
@Composable
private fun CompactActionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Gray.copy(alpha = 0.5f)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else Color.Gray.copy(alpha = 0.5f)
        )
    }
}
