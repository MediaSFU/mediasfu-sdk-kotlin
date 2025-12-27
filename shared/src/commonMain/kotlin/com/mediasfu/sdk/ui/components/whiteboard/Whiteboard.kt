package com.mediasfu.sdk.ui.components.whiteboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mediasfu.sdk.model.*

/**
 * Parameters for the Whiteboard widget.
 */
interface WhiteboardParameters {
    /** Function to emit whiteboard actions to the server for real-time sync */
    val emitWhiteboardAction: ((action: String, payload: Map<String, Any?>) -> Unit)?
    val showAlert: ((message: String, type: String, duration: Long) -> Unit)?
    val islevel: String
    val roomName: String
    val shapes: List<WhiteboardShape>
    val useImageBackground: Boolean
    val redoStack: List<WhiteboardShape>
    val undoStack: List<String>
    val whiteboardStarted: Boolean
    val whiteboardEnded: Boolean
    val whiteboardUsers: List<WhiteboardUser>
    val member: String
    val shareScreenStarted: Boolean
    val targetResolution: String?
    val targetResolutionHost: String?

    val updateShapes: (List<WhiteboardShape>) -> Unit
    val updateUseImageBackground: (Boolean) -> Unit
    val updateRedoStack: (List<WhiteboardShape>) -> Unit
    val updateUndoStack: (List<String>) -> Unit
    val updateWhiteboardStarted: (Boolean) -> Unit
    val updateWhiteboardEnded: (Boolean) -> Unit
    val updateWhiteboardUsers: (List<WhiteboardUser>) -> Unit
    val updateScreenId: (String) -> Unit
    val updateShareScreenStarted: (Boolean) -> Unit

    fun getUpdatedAllParams(): WhiteboardParameters
}

/**
 * Options for configuring the Whiteboard widget.
 */
data class WhiteboardOptions(
    val customWidth: Float,
    val customHeight: Float,
    val parameters: WhiteboardParameters,
    val showAspect: Boolean = true
)

/**
 * Whiteboard - Real-time collaborative drawing and annotation canvas
 *
 * A feature-rich whiteboard component for collaborative drawing, annotations, and visual brainstorming.
 * Supports freehand drawing, shapes, text, images, erasers, undo/redo, zoom/pan, and real-time
 * synchronization across participants.
 *
 * Features:
 * - Freehand drawing with customizable brush and thickness
 * - Shape tools (rectangle, circle, line, triangle, polygon, etc.)
 * - Text annotations with font customization
 * - Image uploads and background images
 * - Eraser tool with adjustable size
 * - Undo/redo functionality
 * - Zoom in/out with pan navigation
 * - Color palette selection
 * - Line type selection (solid, dashed, dotted)
 * - Real-time socket synchronization
 * - Multi-user collaboration with user tracking
 */
@Composable
fun Whiteboard(
    options: WhiteboardOptions,
    modifier: Modifier = Modifier
) {
    val params = options.parameters
    val textMeasurer = rememberTextMeasurer()
    val painter = remember { WhiteboardPainter(textMeasurer) }

    // Drawing state
    var mode by remember { mutableStateOf(WhiteboardMode.PAN) }
    var currentShapeType by remember { mutableStateOf(WhiteboardShapeType.RECTANGLE) }
    var isDrawing by remember { mutableStateOf(false) }
    var startPoint by remember { mutableStateOf<Offset?>(null) }
    var currentPoint by remember { mutableStateOf<Offset?>(null) }
    var freehandPoints by remember { mutableStateOf(listOf<Offset>()) }
    var selectedShape by remember { mutableStateOf<WhiteboardShape?>(null) }

    // Canvas state
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableStateOf(1f) }
    val minScale = 0.25f
    val maxScale = 1.75f

    // Tool settings
    var currentColor by remember { mutableStateOf(Color.Black) }
    var brushThickness by remember { mutableStateOf(6f) }
    var lineThickness by remember { mutableStateOf(6f) }
    var eraserThickness by remember { mutableStateOf(10f) }
    var lineType by remember { mutableStateOf(LineType.SOLID) }
    var fontSize by remember { mutableStateOf(20f) }

    // Background
    var useImageBackground by remember { mutableStateOf(params.useImageBackground) }

    // Local shapes list - synced with params.shapes
    var shapes by remember { mutableStateOf(params.shapes) }
    
    // Sync shapes when params.shapes changes (from socket events)
    LaunchedEffect(params.shapes) {
        shapes = params.shapes
    }

    // Undo/Redo stacks
    var undoStack by remember { mutableStateOf(listOf<List<WhiteboardShape>>()) }
    var redoStack by remember { mutableStateOf(listOf<List<WhiteboardShape>>()) }

    // Toolbar visibility
    var toolbarVisible by remember { mutableStateOf(true) }

    // Text input state
    var showTextInput by remember { mutableStateOf(false) }
    var textInputPosition by remember { mutableStateOf(Offset.Zero) }
    var textInputValue by remember { mutableStateOf("") }

    // Eraser cursor position
    var eraserCursorPosition by remember { mutableStateOf<Offset?>(null) }
    
    // Canvas size tracking for image operations
    var canvasSize by remember { mutableStateOf(IntSize(800, 600)) }
    
    // Pending image result for processing after functions are defined
    var pendingImageResult by remember { mutableStateOf<WhiteboardImageResult?>(null) }
    
    // Image picker launcher for upload functionality
    val launchImagePicker = rememberImagePickerLauncher { result ->
        pendingImageResult = result
    }

    // Current shape being drawn
    val currentShape by remember(mode, currentShapeType, startPoint, currentPoint, freehandPoints, currentColor, lineThickness, brushThickness, lineType) {
        derivedStateOf {
            when {
                !isDrawing -> null
                mode == WhiteboardMode.FREEHAND && freehandPoints.isNotEmpty() -> {
                    WhiteboardShape(
                        type = WhiteboardShapeType.FREEHAND,
                        points = freehandPoints,
                        color = currentColor,
                        thickness = brushThickness,
                        lineType = lineType
                    )
                }
                mode == WhiteboardMode.DRAW && startPoint != null && currentPoint != null -> {
                    WhiteboardShape(
                        type = WhiteboardShapeType.LINE,
                        start = startPoint,
                        end = currentPoint,
                        color = currentColor,
                        thickness = lineThickness,
                        lineType = lineType
                    )
                }
                mode == WhiteboardMode.SHAPE && startPoint != null && currentPoint != null -> {
                    WhiteboardShape(
                        type = currentShapeType,
                        start = startPoint,
                        end = currentPoint,
                        color = currentColor,
                        thickness = lineThickness,
                        lineType = lineType
                    )
                }
                else -> null
            }
        }
    }

    // Check if user can draw
    val canDraw by remember(params.islevel, params.whiteboardUsers, params.member, params.whiteboardEnded) {
        derivedStateOf {
            params.islevel == "2" || params.whiteboardUsers.any { it.name == params.member && it.useBoard }
        }
    }

    // Canvas to screen coordinate conversion
    fun canvasToScreen(point: Offset): Offset {
        return Offset(
            point.x * scale + panOffset.x,
            point.y * scale + panOffset.y
        )
    }

    // Screen to canvas coordinate conversion
    fun screenToCanvas(point: Offset): Offset {
        return Offset(
            (point.x - panOffset.x) / scale,
            (point.y - panOffset.y) / scale
        )
    }

    // Save current state for undo
    fun saveToUndoStack() {
        undoStack = undoStack + listOf(shapes.toList())
        redoStack = emptyList()
    }

    // Emit shape to socket
    fun emitShape(action: String, payload: Map<String, Any?>) {
        params.emitWhiteboardAction?.invoke(action, payload)
    }

    // Add shape and sync
    fun addShape(shape: WhiteboardShape) {
        saveToUndoStack()
        shapes = shapes + shape
        params.updateShapes(shapes)
        emitShape(
            when (shape.type) {
                WhiteboardShapeType.FREEHAND -> "draw"
                WhiteboardShapeType.TEXT -> "text"
                else -> "shape"
            },
            shape.toMap()
        )
    }

    // Undo action
    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack = redoStack + listOf(shapes.toList())
            shapes = undoStack.last()
            undoStack = undoStack.dropLast(1)
            params.updateShapes(shapes)
            emitShape("undo", emptyMap())
        }
    }

    // Redo action
    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack = undoStack + listOf(shapes.toList())
            shapes = redoStack.last()
            redoStack = redoStack.dropLast(1)
            params.updateShapes(shapes)
            emitShape("redo", emptyMap())
        }
    }

    // Clear all shapes
    fun clearAll() {
        saveToUndoStack()
        shapes = emptyList()
        params.updateShapes(shapes)
        emitShape("clear", emptyMap())
    }

    // Check if shape is near a point (for erasing)
    fun isShapeNearPoint(shape: WhiteboardShape, point: Offset, radius: Float): Boolean {
        return when (shape.type) {
            WhiteboardShapeType.FREEHAND -> {
                shape.points.any { p ->
                    (p - point).getDistance() < radius
                }
            }
            else -> {
                val start = shape.start ?: return false
                val end = shape.end ?: return false
                val center = Offset((start.x + end.x) / 2f, (start.y + end.y) / 2f)
                (center - point).getDistance() < radius + kotlin.math.max(
                    kotlin.math.abs(end.x - start.x),
                    kotlin.math.abs(end.y - start.y)
                ) / 2f
            }
        }
    }

    // Erase at position
    fun erase(position: Offset) {
        val eraseRadius = eraserThickness / 2f
        val newShapes = shapes.filter { shape ->
            !isShapeNearPoint(shape, position, eraseRadius)
        }
        if (newShapes.size != shapes.size) {
            shapes = newShapes
            params.updateShapes(shapes)
        }
    }

    // Zoom controls
    fun zoomIn() {
        scale = (scale * 1.2f).coerceAtMost(maxScale)
    }

    fun zoomOut() {
        scale = (scale / 1.2f).coerceAtLeast(minScale)
    }

    fun resetZoom() {
        scale = 1f
        panOffset = Offset.Zero
    }
    
    // Process pending image upload result
    LaunchedEffect(pendingImageResult) {
        val result = pendingImageResult
        if (result != null) {
            pendingImageResult = null // Clear immediately
            
            // Create an image shape at center of visible canvas
            val canvasCenter = Offset(
                (canvasSize.width / 2f - panOffset.x) / scale,
                (canvasSize.height / 2f - panOffset.y) / scale
            )
            val imageShape = createImageShape(
                imageData = result.imageData,
                imageSrc = result.imageSrc,
                width = result.width,
                height = result.height,
                canvasCenter = canvasCenter,
                userId = params.member
            )
            
            // Add shape locally and emit to socket
            saveToUndoStack()
            shapes = shapes + imageShape
            params.updateShapes(shapes)
            emitShape("image", imageShape.toMap())
            
            params.showAlert?.invoke("Image added to whiteboard", "success", 2000)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Toolbar
        if (toolbarVisible) {
            WhiteboardToolbar(
                options = WhiteboardToolbarOptions(
                    currentMode = mode,
                    currentShapeType = currentShapeType,
                    currentColor = currentColor,
                    brushThickness = brushThickness,
                    lineThickness = lineThickness,
                    eraserThickness = eraserThickness,
                    lineType = lineType,
                    fontSize = fontSize,
                    useImageBackground = useImageBackground,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty(),
                    hasSelectedShape = selectedShape != null,
                    onModeChanged = { mode = it },
                    onShapeTypeChanged = { currentShapeType = it },
                    onColorChanged = { currentColor = it },
                    onBrushThicknessChanged = { brushThickness = it },
                    onLineThicknessChanged = { lineThickness = it },
                    onEraserThicknessChanged = { eraserThickness = it },
                    onLineTypeChanged = { lineType = it },
                    onFontSizeChanged = { fontSize = it },
                    onUndo = { undo() },
                    onRedo = { redo() },
                    onDeleteShape = selectedShape?.let { {
                        saveToUndoStack()
                        shapes = shapes.filter { it.id != selectedShape?.id }
                        params.updateShapes(shapes)
                        selectedShape = null
                    } },
                    onClear = { clearAll() },
                    onZoomIn = { zoomIn() },
                    onZoomOut = { zoomOut() },
                    onResetZoom = { resetZoom() },
                    onToggleBackground = {
                        useImageBackground = !useImageBackground
                        params.updateUseImageBackground(useImageBackground)
                        emitShape("toggleBackground", emptyMap())
                    },
                    onSave = {
                        // Share whiteboard canvas as image
                        val shapesToShare = shapes.toList()
                        params.showAlert?.invoke("Preparing to share...", "info", 1500)
                        shareWhiteboardCanvas(
                            shapes = shapesToShare,
                            canvasWidth = canvasSize.width,
                            canvasHeight = canvasSize.height,
                            useImageBackground = useImageBackground
                        ) { success, message ->
                            if (success) {
                                // Don't show success alert since share dialog is opening
                                // params.showAlert?.invoke("Share dialog opened", "success", 2000)
                            } else {
                                params.showAlert?.invoke(message, "danger", 3000)
                            }
                        }
                    },
                    onUploadImage = {
                        // Launch image picker to upload image to whiteboard
                        launchImagePicker()
                    },
                    onToggleToolbar = { toolbarVisible = false }
                )
            )
        } else {
            // Show button to reveal toolbar - high contrast design
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(4.dp),
                color = Color(0xFF1976D2), // Blue background for visibility
                shadowElevation = 4.dp,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            ) {
                IconButton(
                    onClick = { toolbarVisible = true }
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowDown, 
                        contentDescription = "Show Toolbar",
                        tint = Color.White // White icon on blue background
                    )
                }
            }
        }

        // Canvas
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .onSizeChanged { size -> canvasSize = size }
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(mode, canDraw) {
                        if (!canDraw && mode != WhiteboardMode.PAN) return@pointerInput

                        detectDragGestures(
                            onDragStart = { offset ->
                                val canvasPoint = screenToCanvas(offset)
                                when (mode) {
                                    WhiteboardMode.PAN -> {
                                        // Pan starts - store offset
                                    }
                                    WhiteboardMode.FREEHAND -> {
                                        isDrawing = true
                                        freehandPoints = listOf(canvasPoint)
                                    }
                                    WhiteboardMode.DRAW, WhiteboardMode.SHAPE -> {
                                        isDrawing = true
                                        startPoint = canvasPoint
                                        currentPoint = canvasPoint
                                    }
                                    WhiteboardMode.ERASE -> {
                                        eraserCursorPosition = offset
                                        erase(canvasPoint)
                                    }
                                    WhiteboardMode.TEXT -> {
                                        textInputPosition = canvasPoint
                                        showTextInput = true
                                    }
                                    WhiteboardMode.SELECT -> {
                                        // Check if clicking on a shape
                                        selectedShape = shapes.findLast { shape ->
                                            isShapeNearPoint(shape, canvasPoint, 10f)
                                        }
                                    }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val offset = change.position
                                val canvasPoint = screenToCanvas(offset)

                                when (mode) {
                                    WhiteboardMode.PAN -> {
                                        panOffset += dragAmount
                                    }
                                    WhiteboardMode.FREEHAND -> {
                                        freehandPoints = freehandPoints + canvasPoint
                                    }
                                    WhiteboardMode.DRAW, WhiteboardMode.SHAPE -> {
                                        currentPoint = canvasPoint
                                    }
                                    WhiteboardMode.ERASE -> {
                                        eraserCursorPosition = offset
                                        erase(canvasPoint)
                                    }
                                    else -> {}
                                }
                            },
                            onDragEnd = {
                                when (mode) {
                                    WhiteboardMode.FREEHAND -> {
                                        if (freehandPoints.size >= 2) {
                                            addShape(
                                                WhiteboardShape(
                                                    type = WhiteboardShapeType.FREEHAND,
                                                    points = freehandPoints,
                                                    color = currentColor,
                                                    thickness = brushThickness,
                                                    lineType = lineType
                                                )
                                            )
                                        }
                                        freehandPoints = emptyList()
                                    }
                                    WhiteboardMode.DRAW -> {
                                        if (startPoint != null && currentPoint != null) {
                                            addShape(
                                                WhiteboardShape(
                                                    type = WhiteboardShapeType.LINE,
                                                    start = startPoint,
                                                    end = currentPoint,
                                                    color = currentColor,
                                                    thickness = lineThickness,
                                                    lineType = lineType
                                                )
                                            )
                                        }
                                    }
                                    WhiteboardMode.SHAPE -> {
                                        if (startPoint != null && currentPoint != null) {
                                            addShape(
                                                WhiteboardShape(
                                                    type = currentShapeType,
                                                    start = startPoint,
                                                    end = currentPoint,
                                                    color = currentColor,
                                                    thickness = lineThickness,
                                                    lineType = lineType
                                                )
                                            )
                                        }
                                    }
                                    else -> {}
                                }
                                isDrawing = false
                                startPoint = null
                                currentPoint = null
                                eraserCursorPosition = null
                            }
                        )
                    }
            ) {
                painter.draw(
                    drawScope = this,
                    shapes = shapes,
                    currentShape = currentShape,
                    panOffset = panOffset,
                    scale = scale,
                    maxWidth = options.customWidth,
                    maxHeight = options.customHeight,
                    useImageBackground = useImageBackground,
                    selectedShape = selectedShape,
                    eraserCursorPosition = eraserCursorPosition,
                    eraserThickness = eraserThickness,
                    transparentBackground = false
                )
            }
        }
    }

    // Text input dialog
    if (showTextInput) {
        AlertDialog(
            onDismissRequest = { showTextInput = false },
            title = { Text("Add Text") },
            text = {
                OutlinedTextField(
                    value = textInputValue,
                    onValueChange = { textInputValue = it },
                    label = { Text("Enter text") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (textInputValue.isNotBlank()) {
                            addShape(
                                WhiteboardShape(
                                    type = WhiteboardShapeType.TEXT,
                                    start = textInputPosition,
                                    text = textInputValue,
                                    color = currentColor,
                                    fontSize = fontSize,
                                    thickness = 1f
                                )
                            )
                        }
                        textInputValue = ""
                        showTextInput = false
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextInput = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
