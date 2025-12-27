package com.mediasfu.sdk.socket

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.mediasfu.sdk.model.LineType
import com.mediasfu.sdk.model.WhiteboardShape
import com.mediasfu.sdk.model.WhiteboardShapeType
import com.mediasfu.sdk.model.WhiteboardUser

/**
 * Whiteboard socket event handlers.
 *
 * These handlers process real-time whiteboard actions from the server,
 * enabling collaborative drawing, annotations, and session management.
 */

/**
 * Options for handling whiteboard action events.
 */
data class WhiteboardActionOptions(
    val action: String,
    val payload: Map<String, Any?>?,
    val shapes: List<WhiteboardShape>,
    val redoStack: List<List<WhiteboardShape>>,
    val undoStack: List<List<WhiteboardShape>>,
    val useImageBackground: Boolean,
    val updateShapes: (List<WhiteboardShape>) -> Unit,
    val updateRedoStack: (List<List<WhiteboardShape>>) -> Unit,
    val updateUndoStack: (List<List<WhiteboardShape>>) -> Unit,
    val updateUseImageBackground: (Boolean) -> Unit
)

/**
 * Process a whiteboard action from the socket.
 */
fun handleWhiteboardAction(options: WhiteboardActionOptions) {
    when (options.action) {
        "draw" -> handleDrawAction(options)
        "shape" -> handleShapeAction(options)
        "erase" -> handleEraseAction(options)
        "clear" -> handleClearAction(options)
        "uploadImage" -> handleUploadImageAction(options)
        "toggleBackground" -> handleToggleBackgroundAction(options)
        "undo" -> handleUndoAction(options)
        "redo" -> handleRedoAction(options)
        "text" -> handleTextAction(options)
        "deleteShape" -> handleDeleteShapeAction(options)
        "shapes" -> handleShapesAction(options)
    }
}

private fun handleDrawAction(options: WhiteboardActionOptions) {
    val payload = options.payload ?: return

    val type = payload["type"] as? String

    val shape = if (type == "freehand") {
        val pointsList = payload["points"] as? List<*>
        val points = pointsList?.mapNotNull { point ->
            when (point) {
                is Map<*, *> -> {
                    val x = (point["x"] as? Number)?.toFloat() ?: 0f
                    val y = (point["y"] as? Number)?.toFloat() ?: 0f
                    Offset(x, y)
                }
                else -> null
            }
        } ?: emptyList()

        WhiteboardShape(
            type = WhiteboardShapeType.FREEHAND,
            points = points,
            color = parseColor(payload["color"]),
            thickness = (payload["thickness"] as? Number)?.toFloat() ?: 6f
        )
    } else {
        // Line
        WhiteboardShape(
            type = WhiteboardShapeType.LINE,
            start = Offset(
                (payload["x1"] as? Number)?.toFloat() ?: 0f,
                (payload["y1"] as? Number)?.toFloat() ?: 0f
            ),
            end = Offset(
                (payload["x2"] as? Number)?.toFloat() ?: 0f,
                (payload["y2"] as? Number)?.toFloat() ?: 0f
            ),
            color = parseColor(payload["color"]),
            thickness = (payload["thickness"] as? Number)?.toFloat() ?: 6f,
            lineType = parseLineType(payload["lineType"])
        )
    }

    val newShapes = options.shapes + shape
    options.updateShapes(newShapes)
}

private fun handleShapeAction(options: WhiteboardActionOptions) {
    val payload = options.payload ?: return

    val shapeType = parseShapeType(payload["type"] as? String)
    val shape = WhiteboardShape(
        type = shapeType,
        start = Offset(
            (payload["x1"] as? Number)?.toFloat() ?: 0f,
            (payload["y1"] as? Number)?.toFloat() ?: 0f
        ),
        end = Offset(
            (payload["x2"] as? Number)?.toFloat() ?: 0f,
            (payload["y2"] as? Number)?.toFloat() ?: 0f
        ),
        color = parseColor(payload["color"]),
        thickness = (payload["thickness"] as? Number)?.toFloat() ?: 6f,
        lineType = parseLineType(payload["lineType"])
    )

    val newShapes = options.shapes + shape
    options.updateShapes(newShapes)
}

private fun handleEraseAction(options: WhiteboardActionOptions) {
    val payload = options.payload ?: return

    val x = (payload["x"] as? Number)?.toFloat() ?: 0f
    val y = (payload["y"] as? Number)?.toFloat() ?: 0f
    val erasePoint = Offset(x, y)
    val eraseRadius = (payload["thickness"] as? Number)?.toFloat()?.div(2f) ?: 10f

    // Remove shapes near the erase point
    val newShapes = options.shapes.filter { shape ->
        !isShapeNearPoint(shape, erasePoint, eraseRadius)
    }

    options.updateShapes(newShapes)
}

private fun handleClearAction(options: WhiteboardActionOptions) {
    options.updateShapes(emptyList())
}

private fun handleUploadImageAction(options: WhiteboardActionOptions) {
    val payload = options.payload ?: return

    val shape = WhiteboardShape(
        type = WhiteboardShapeType.IMAGE,
        start = Offset(
            (payload["x1"] as? Number)?.toFloat() ?: 0f,
            (payload["y1"] as? Number)?.toFloat() ?: 0f
        ),
        end = Offset(
            (payload["x2"] as? Number)?.toFloat() ?: 0f,
            (payload["y2"] as? Number)?.toFloat() ?: 0f
        ),
        imageSrc = payload["src"] as? String,
        color = Color.Black,
        thickness = 1f
    )

    val newShapes = options.shapes + shape
    options.updateShapes(newShapes)
}

private fun handleToggleBackgroundAction(options: WhiteboardActionOptions) {
    options.updateUseImageBackground(!options.useImageBackground)
}

private fun handleUndoAction(options: WhiteboardActionOptions) {
    if (options.shapes.isNotEmpty()) {
        val newRedoStack = options.redoStack + listOf(options.shapes)
        val newShapes = options.shapes.dropLast(1)

        options.updateRedoStack(newRedoStack)
        options.updateShapes(newShapes)
    }
}

private fun handleRedoAction(options: WhiteboardActionOptions) {
    if (options.redoStack.isNotEmpty()) {
        val newUndoStack = options.undoStack + listOf(options.shapes)
        val lastState = options.redoStack.last()
        val newRedoStack = options.redoStack.dropLast(1)

        options.updateUndoStack(newUndoStack)
        options.updateRedoStack(newRedoStack)
        options.updateShapes(lastState)
    }
}

private fun handleTextAction(options: WhiteboardActionOptions) {
    val payload = options.payload ?: return

    val shape = WhiteboardShape(
        type = WhiteboardShapeType.TEXT,
        start = Offset(
            (payload["x"] as? Number)?.toFloat() ?: 0f,
            (payload["y"] as? Number)?.toFloat() ?: 0f
        ),
        text = payload["text"] as? String,
        color = parseColor(payload["color"]),
        thickness = 1f,
        fontFamily = payload["font"] as? String ?: "Arial",
        fontSize = (payload["fontSize"] as? Number)?.toFloat() ?: 20f
    )

    val newShapes = options.shapes + shape
    options.updateShapes(newShapes)
}

private fun handleDeleteShapeAction(options: WhiteboardActionOptions) {
    val payload = options.payload ?: return

    // Find and remove the shape matching the payload
    // Using a simple comparison by converting shape to string
    val payloadStr = payload.toString()
    val newShapes = options.shapes.filter { shape ->
        shape.toMap().toString() != payloadStr
    }

    options.updateShapes(newShapes)
}

private fun handleShapesAction(options: WhiteboardActionOptions) {
    val payload = options.payload ?: return

    val shapesList = payload["shapes"] as? List<*> ?: return
    val newShapes = shapesList.mapNotNull { shapeData ->
        (shapeData as? Map<*, *>)?.let { parseShapeFromMap(it) }
    }

    options.updateShapes(newShapes)
}

/**
 * Options for handling whiteboard updated events.
 */
data class WhiteboardUpdatedOptions(
    val whiteboardUsers: List<Map<String, Any?>>?,
    val whiteboardData: Map<String, Any?>?,
    val status: String?,
    val updateWhiteboardUsers: (List<WhiteboardUser>) -> Unit,
    val updateShapes: (List<WhiteboardShape>) -> Unit,
    val updateWhiteboardStarted: (Boolean) -> Unit,
    val updateWhiteboardEnded: (Boolean) -> Unit,
    val shapes: List<WhiteboardShape>
)

/**
 * Handle whiteboard state updates from the server.
 */
fun handleWhiteboardUpdated(options: WhiteboardUpdatedOptions) {
    // Update whiteboard users
    options.whiteboardUsers?.let { usersList ->
        val users = usersList.mapNotNull { userMap ->
            val name = userMap["name"] as? String ?: return@mapNotNull null
            val useBoard = userMap["useBoard"] as? Boolean ?: true
            WhiteboardUser(name = name, useBoard = useBoard)
        }
        options.updateWhiteboardUsers(users)
    }

    // Update whiteboard data/shapes
    options.whiteboardData?.let { data ->
        val shapesList = data["shapes"] as? List<*>
        if (shapesList != null) {
            val newShapes = shapesList.mapNotNull { shapeData ->
                (shapeData as? Map<*, *>)?.let { parseShapeFromMap(it) }
            }
            options.updateShapes(newShapes)
        }
    }

    // Update status
    options.status?.let { status ->
        when (status) {
            "started" -> {
                options.updateWhiteboardStarted(true)
                options.updateWhiteboardEnded(false)
            }
            "ended", "stopped" -> {
                options.updateWhiteboardStarted(false)
                options.updateWhiteboardEnded(true)
            }
        }
    }
}

/**
 * Options for handling whiteboard started events.
 */
data class WhiteboardStartedOptions(
    val whiteboardUsers: List<Map<String, Any?>>?,
    val updateWhiteboardUsers: (List<WhiteboardUser>) -> Unit,
    val updateWhiteboardStarted: (Boolean) -> Unit,
    val updateWhiteboardEnded: (Boolean) -> Unit
)

/**
 * Handle whiteboard started event.
 */
fun handleWhiteboardStarted(options: WhiteboardStartedOptions) {
    options.updateWhiteboardStarted(true)
    options.updateWhiteboardEnded(false)

    options.whiteboardUsers?.let { usersList ->
        val users = usersList.mapNotNull { userMap ->
            val name = userMap["name"] as? String ?: return@mapNotNull null
            val useBoard = userMap["useBoard"] as? Boolean ?: true
            WhiteboardUser(name = name, useBoard = useBoard)
        }
        options.updateWhiteboardUsers(users)
    }
}

/**
 * Options for handling whiteboard ended events.
 */
data class WhiteboardEndedOptions(
    val updateWhiteboardStarted: (Boolean) -> Unit,
    val updateWhiteboardEnded: (Boolean) -> Unit,
    val updateShapes: (List<WhiteboardShape>) -> Unit
)

/**
 * Handle whiteboard ended event.
 */
fun handleWhiteboardEnded(options: WhiteboardEndedOptions) {
    options.updateWhiteboardStarted(false)
    options.updateWhiteboardEnded(true)
    options.updateShapes(emptyList())
}

// Utility functions

private fun parseColor(colorValue: Any?): Color {
    return when (colorValue) {
        is String -> {
            try {
                if (colorValue.startsWith("#")) {
                    val colorInt = colorValue.removePrefix("#").toLong(16)
                    if (colorValue.length == 7) {
                        Color(0xFF000000 or colorInt)
                    } else {
                        Color(colorInt)
                    }
                } else if (colorValue.startsWith("rgb")) {
                    // Parse rgb(r, g, b) or rgba(r, g, b, a)
                    val values = colorValue
                        .replace("rgba(", "")
                        .replace("rgb(", "")
                        .replace(")", "")
                        .split(",")
                        .map { it.trim().toFloatOrNull() ?: 0f }

                    if (values.size >= 3) {
                        val r = (values[0] / 255f).coerceIn(0f, 1f)
                        val g = (values[1] / 255f).coerceIn(0f, 1f)
                        val b = (values[2] / 255f).coerceIn(0f, 1f)
                        val a = if (values.size >= 4) values[3].coerceIn(0f, 1f) else 1f
                        Color(r, g, b, a)
                    } else {
                        Color.Black
                    }
                } else {
                    Color.Black
                }
            } catch (e: Exception) {
                Color.Black
            }
        }
        is Number -> {
            try {
                Color(colorValue.toLong())
            } catch (e: Exception) {
                Color.Black
            }
        }
        else -> Color.Black
    }
}

private fun parseLineType(lineTypeValue: Any?): LineType {
    return when (lineTypeValue?.toString()?.lowercase()) {
        "dashed" -> LineType.DASHED
        "dotted" -> LineType.DOTTED
        "dashdot", "dash_dot" -> LineType.DASH_DOT
        else -> LineType.SOLID
    }
}

private fun parseShapeType(typeValue: String?): WhiteboardShapeType {
    return when (typeValue?.lowercase()) {
        "freehand" -> WhiteboardShapeType.FREEHAND
        "line" -> WhiteboardShapeType.LINE
        "rectangle", "rect" -> WhiteboardShapeType.RECTANGLE
        "circle", "ellipse" -> WhiteboardShapeType.CIRCLE
        "triangle" -> WhiteboardShapeType.TRIANGLE
        "text" -> WhiteboardShapeType.TEXT
        "image" -> WhiteboardShapeType.IMAGE
        "rhombus" -> WhiteboardShapeType.RHOMBUS
        "pentagon" -> WhiteboardShapeType.PENTAGON
        "hexagon" -> WhiteboardShapeType.HEXAGON
        "parallelogram" -> WhiteboardShapeType.PARALLELOGRAM
        "octagon" -> WhiteboardShapeType.OCTAGON
        "oval" -> WhiteboardShapeType.OVAL
        else -> WhiteboardShapeType.LINE
    }
}

private fun parseShapeFromMap(map: Map<*, *>): WhiteboardShape? {
    val type = parseShapeType(map["type"] as? String)

    return when (type) {
        WhiteboardShapeType.FREEHAND -> {
            val pointsList = map["points"] as? List<*>
            val points = pointsList?.mapNotNull { point ->
                when (point) {
                    is Map<*, *> -> {
                        val x = (point["x"] as? Number)?.toFloat() ?: 0f
                        val y = (point["y"] as? Number)?.toFloat() ?: 0f
                        Offset(x, y)
                    }
                    else -> null
                }
            } ?: emptyList()

            WhiteboardShape(
                type = type,
                points = points,
                color = parseColor(map["color"]),
                thickness = (map["thickness"] as? Number)?.toFloat() ?: 6f,
                lineType = parseLineType(map["lineType"])
            )
        }
        WhiteboardShapeType.TEXT -> {
            WhiteboardShape(
                type = type,
                start = Offset(
                    (map["x"] as? Number)?.toFloat() ?: (map["x1"] as? Number)?.toFloat() ?: 0f,
                    (map["y"] as? Number)?.toFloat() ?: (map["y1"] as? Number)?.toFloat() ?: 0f
                ),
                text = map["text"] as? String,
                color = parseColor(map["color"]),
                thickness = 1f,
                fontFamily = map["font"] as? String ?: map["fontFamily"] as? String ?: "Arial",
                fontSize = (map["fontSize"] as? Number)?.toFloat() ?: 20f
            )
        }
        WhiteboardShapeType.IMAGE -> {
            WhiteboardShape(
                type = type,
                start = Offset(
                    (map["x1"] as? Number)?.toFloat() ?: 0f,
                    (map["y1"] as? Number)?.toFloat() ?: 0f
                ),
                end = Offset(
                    (map["x2"] as? Number)?.toFloat() ?: 0f,
                    (map["y2"] as? Number)?.toFloat() ?: 0f
                ),
                imageSrc = map["src"] as? String ?: map["imageSrc"] as? String,
                color = Color.Black,
                thickness = 1f
            )
        }
        else -> {
            WhiteboardShape(
                type = type,
                start = Offset(
                    (map["x1"] as? Number)?.toFloat() ?: 0f,
                    (map["y1"] as? Number)?.toFloat() ?: 0f
                ),
                end = Offset(
                    (map["x2"] as? Number)?.toFloat() ?: 0f,
                    (map["y2"] as? Number)?.toFloat() ?: 0f
                ),
                color = parseColor(map["color"]),
                thickness = (map["thickness"] as? Number)?.toFloat() ?: 6f,
                lineType = parseLineType(map["lineType"])
            )
        }
    }
}

private fun isShapeNearPoint(shape: WhiteboardShape, point: Offset, radius: Float): Boolean {
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
