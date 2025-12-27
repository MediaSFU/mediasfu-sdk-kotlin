package com.mediasfu.sdk.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable

/**
 * Enum representing the different types of shapes that can be drawn on the whiteboard.
 */
enum class WhiteboardShapeType {
    FREEHAND,
    LINE,
    RECTANGLE,
    CIRCLE,
    TRIANGLE,
    PENTAGON,
    HEXAGON,
    RHOMBUS,
    PARALLELOGRAM,
    OCTAGON,
    OVAL,
    TEXT,
    IMAGE;

    companion object {
        fun fromString(str: String): WhiteboardShapeType {
            return when (str.lowercase()) {
                "freehand" -> FREEHAND
                "line" -> LINE
                "rectangle" -> RECTANGLE
                "circle" -> CIRCLE
                "triangle" -> TRIANGLE
                "pentagon" -> PENTAGON
                "hexagon" -> HEXAGON
                "rhombus" -> RHOMBUS
                "parallelogram" -> PARALLELOGRAM
                "octagon" -> OCTAGON
                "oval" -> OVAL
                "text" -> TEXT
                "image" -> IMAGE
                else -> FREEHAND
            }
        }
    }

    override fun toString(): String {
        return when (this) {
            FREEHAND -> "freehand"
            LINE -> "line"
            RECTANGLE -> "rectangle"
            CIRCLE -> "circle"
            TRIANGLE -> "triangle"
            PENTAGON -> "pentagon"
            HEXAGON -> "hexagon"
            RHOMBUS -> "rhombus"
            PARALLELOGRAM -> "parallelogram"
            OCTAGON -> "octagon"
            OVAL -> "oval"
            TEXT -> "text"
            IMAGE -> "image"
        }
    }
}

/**
 * Enum representing the different line types for shapes.
 */
enum class LineType {
    SOLID,
    DASHED,
    DOTTED,
    DASH_DOT;

    companion object {
        fun fromString(str: String): LineType {
            return when (str.lowercase()) {
                "solid" -> SOLID
                "dashed" -> DASHED
                "dotted" -> DOTTED
                "dashdot", "dash_dot" -> DASH_DOT
                else -> SOLID
            }
        }
    }

    override fun toString(): String {
        return when (this) {
            SOLID -> "solid"
            DASHED -> "dashed"
            DOTTED -> "dotted"
            DASH_DOT -> "dashDot"
        }
    }
}

/**
 * Enum representing the different modes of the whiteboard.
 */
enum class WhiteboardMode {
    PAN,
    DRAW,
    FREEHAND,
    SHAPE,
    TEXT,
    ERASE,
    SELECT;

    companion object {
        fun fromString(str: String): WhiteboardMode {
            return when (str.lowercase()) {
                "pan" -> PAN
                "draw" -> DRAW
                "freehand" -> FREEHAND
                "shape" -> SHAPE
                "text" -> TEXT
                "erase" -> ERASE
                "select" -> SELECT
                else -> PAN
            }
        }
    }
}

/**
 * Represents a participant allowed to interact with the collaborative whiteboard.
 */
@Serializable
data class WhiteboardUser(
    val name: String,
    val useBoard: Boolean = false
) {
    companion object {
        fun fromMap(map: Map<String, Any?>): WhiteboardUser {
            return WhiteboardUser(
                name = map["name"] as? String ?: "",
                useBoard = map["useBoard"] as? Boolean ?: false
            )
        }
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "name" to name,
            "useBoard" to useBoard
        )
    }
}

/**
 * Simple 2D point for whiteboard drawing.
 */
data class WhiteboardPoint(
    val x: Float,
    val y: Float
) {
    fun toOffset(): Offset = Offset(x, y)

    companion object {
        fun fromOffset(offset: Offset): WhiteboardPoint = WhiteboardPoint(offset.x, offset.y)
        
        fun fromMap(map: Map<String, Any?>): WhiteboardPoint {
            return WhiteboardPoint(
                x = (map["x"] as? Number)?.toFloat() ?: 0f,
                y = (map["y"] as? Number)?.toFloat() ?: 0f
            )
        }
    }

    fun toMap(): Map<String, Any> = mapOf("x" to x, "y" to y)
}

/**
 * Represents a drawable whiteboard shape.
 */
data class WhiteboardShape(
    val id: String = generateShapeId(),
    val type: WhiteboardShapeType,
    val start: Offset? = null,
    val end: Offset? = null,
    val points: List<Offset> = emptyList(),
    val color: Color = Color.Black,
    val thickness: Float = 6f,
    val lineType: LineType = LineType.SOLID,
    val text: String? = null,
    val fontFamily: String = "Arial",
    val fontSize: Float = 20f,
    val imageData: ByteArray? = null,
    val imageSrc: String? = null
) {
    companion object {
        private var idCounter = 0L
        
        fun generateShapeId(): String = "shape_${Clock.System.now().toEpochMilliseconds()}_${idCounter++}"
        
        fun fromMap(map: Map<String, Any?>): WhiteboardShape {
            val pointsList = (map["points"] as? List<*>)?.mapNotNull { p ->
                when (p) {
                    is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        WhiteboardPoint.fromMap(p as Map<String, Any?>).toOffset()
                    }
                    else -> null
                }
            } ?: emptyList()

            return WhiteboardShape(
                id = map["id"] as? String ?: generateShapeId(),
                type = WhiteboardShapeType.fromString(map["type"] as? String ?: "freehand"),
                start = (map["x1"] as? Number)?.let { x1 ->
                    (map["y1"] as? Number)?.let { y1 ->
                        Offset(x1.toFloat(), y1.toFloat())
                    }
                },
                end = (map["x2"] as? Number)?.let { x2 ->
                    (map["y2"] as? Number)?.let { y2 ->
                        Offset(x2.toFloat(), y2.toFloat())
                    }
                },
                points = pointsList,
                color = parseColor(map["color"] as? String),
                thickness = (map["thickness"] as? Number)?.toFloat() ?: 6f,
                lineType = LineType.fromString(map["lineType"] as? String ?: "solid"),
                text = map["text"] as? String,
                fontFamily = map["fontFamily"] as? String ?: "Arial",
                fontSize = (map["fontSize"] as? Number)?.toFloat() ?: 20f,
                imageSrc = map["src"] as? String
            )
        }

        private fun parseColor(colorString: String?): Color {
            if (colorString == null) return Color.Black
            return try {
                if (colorString.startsWith("#")) {
                    val colorLong = colorString.removePrefix("#").toLong(16)
                    if (colorString.length == 7) {
                        Color(0xFF000000 or colorLong)
                    } else {
                        Color(colorLong)
                    }
                } else if (colorString.startsWith("rgba")) {
                    // Parse rgba(r, g, b, a) format
                    val values = colorString.removePrefix("rgba(").removeSuffix(")")
                        .split(",").map { it.trim() }
                    if (values.size >= 4) {
                        Color(
                            red = values[0].toInt(),
                            green = values[1].toInt(),
                            blue = values[2].toInt(),
                            alpha = (values[3].toFloat() * 255).toInt()
                        )
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
    }

    fun toMap(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>(
            "id" to id,
            "type" to type.toString(),
            "color" to colorToString(color),
            "thickness" to thickness,
            "lineType" to lineType.toString()
        )
        
        if (start != null) {
            map["x1"] = start.x
            map["y1"] = start.y
        }
        if (end != null) {
            map["x2"] = end.x
            map["y2"] = end.y
        }
        if (points.isNotEmpty()) {
            map["points"] = points.map { WhiteboardPoint.fromOffset(it).toMap() }
        }
        if (text != null) {
            map["text"] = text
            map["fontFamily"] = fontFamily
            map["fontSize"] = fontSize
        }
        if (imageSrc != null) {
            map["src"] = imageSrc
        }
        
        return map
    }

    private fun colorToString(color: Color): String {
        val red = (color.red * 255).toInt()
        val green = (color.green * 255).toInt()
        val blue = (color.blue * 255).toInt()
        val alpha = color.alpha
        return if (alpha < 1f) {
            "rgba($red, $green, $blue, $alpha)"
        } else {
            "#${red.toString(16).padStart(2, '0')}${green.toString(16).padStart(2, '0')}${blue.toString(16).padStart(2, '0')}"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as WhiteboardShape

        if (id != other.id) return false
        if (type != other.type) return false
        if (start != other.start) return false
        if (end != other.end) return false
        if (points != other.points) return false
        if (color != other.color) return false
        if (thickness != other.thickness) return false
        if (lineType != other.lineType) return false
        if (text != other.text) return false
        if (fontFamily != other.fontFamily) return false
        if (fontSize != other.fontSize) return false
        if (imageData != null) {
            if (other.imageData == null) return false
            if (!imageData.contentEquals(other.imageData)) return false
        } else if (other.imageData != null) return false
        if (imageSrc != other.imageSrc) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + (start?.hashCode() ?: 0)
        result = 31 * result + (end?.hashCode() ?: 0)
        result = 31 * result + points.hashCode()
        result = 31 * result + color.hashCode()
        result = 31 * result + thickness.hashCode()
        result = 31 * result + lineType.hashCode()
        result = 31 * result + (text?.hashCode() ?: 0)
        result = 31 * result + fontFamily.hashCode()
        result = 31 * result + fontSize.hashCode()
        result = 31 * result + (imageData?.contentHashCode() ?: 0)
        result = 31 * result + (imageSrc?.hashCode() ?: 0)
        return result
    }
}

/**
 * Payload for shape drawing actions.
 */
@Serializable
data class ShapePayload(
    val type: String,
    val x1: Double,
    val y1: Double,
    val x2: Double,
    val y2: Double,
    val color: String,
    val thickness: Double,
    val lineType: String
) {
    companion object {
        fun fromMap(map: Map<String, Any?>): ShapePayload {
            return ShapePayload(
                type = map["type"] as? String ?: "rectangle",
                x1 = (map["x1"] as? Number)?.toDouble() ?: 0.0,
                y1 = (map["y1"] as? Number)?.toDouble() ?: 0.0,
                x2 = (map["x2"] as? Number)?.toDouble() ?: 0.0,
                y2 = (map["y2"] as? Number)?.toDouble() ?: 0.0,
                color = map["color"] as? String ?: "#000000",
                thickness = (map["thickness"] as? Number)?.toDouble() ?: 6.0,
                lineType = map["lineType"] as? String ?: "solid"
            )
        }
    }

    fun toMap(): Map<String, Any> {
        return mapOf(
            "type" to type,
            "x1" to x1,
            "y1" to y1,
            "x2" to x2,
            "y2" to y2,
            "color" to color,
            "thickness" to thickness,
            "lineType" to lineType
        )
    }
}

/**
 * Container for whiteboard actions.
 */
data class WhiteboardAction(
    val action: String,
    val payload: Map<String, Any?>
) {
    companion object {
        fun fromMap(map: Map<String, Any?>): WhiteboardAction {
            @Suppress("UNCHECKED_CAST")
            return WhiteboardAction(
                action = map["action"] as? String ?: "",
                payload = map["payload"] as? Map<String, Any?> ?: emptyMap()
            )
        }
    }

    fun toMap(): Map<String, Any?> {
        return mapOf(
            "action" to action,
            "payload" to payload
        )
    }
}
