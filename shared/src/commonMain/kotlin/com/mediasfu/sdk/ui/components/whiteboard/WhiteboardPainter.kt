package com.mediasfu.sdk.ui.components.whiteboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.sp
import com.mediasfu.sdk.model.LineType
import com.mediasfu.sdk.model.WhiteboardShape
import com.mediasfu.sdk.model.WhiteboardShapeType
import kotlin.math.*

/**
 * WhiteboardPainter - Handles rendering of all shapes on the whiteboard canvas.
 *
 * This painter handles rendering of all shape types including freehand drawings,
 * geometric shapes, text, and images with support for zoom and pan transformations.
 */
class WhiteboardPainter(
    private val textMeasurer: TextMeasurer
) {
    /**
     * Draws all whiteboard content on the canvas.
     */
    fun draw(
        drawScope: DrawScope,
        shapes: List<WhiteboardShape>,
        currentShape: WhiteboardShape?,
        panOffset: Offset,
        scale: Float,
        maxWidth: Float,
        maxHeight: Float,
        useImageBackground: Boolean,
        selectedShape: WhiteboardShape?,
        eraserCursorPosition: Offset?,
        eraserThickness: Float,
        transparentBackground: Boolean
    ) {
        with(drawScope) {
            // Apply zoom and pan transformations
            withTransform({
                translate(panOffset.x, panOffset.y)
                scale(scale, scale, Offset.Zero)
            }) {
                // Draw background
                if (!transparentBackground) {
                    drawBackground(useImageBackground, maxWidth, maxHeight, panOffset, scale)
                }

                // Draw all shapes
                shapes.forEach { shape ->
                    drawShape(shape)
                }

                // Draw current shape being drawn (preview)
                currentShape?.let { drawShape(it) }

                // Draw selection handles if a shape is selected
                selectedShape?.let { drawSelection(it) }
            }

            // Draw eraser cursor (not affected by zoom/pan)
            eraserCursorPosition?.let { drawEraserCursor(it, eraserThickness) }

            // Draw edge markers
            drawEdgeMarkers(maxWidth, maxHeight)
        }
    }

    private fun DrawScope.drawBackground(
        useImageBackground: Boolean,
        maxWidth: Float,
        maxHeight: Float,
        panOffset: Offset,
        scale: Float
    ) {
        // Draw white background
        val bgRect = Rect(
            left = -panOffset.x / scale,
            top = -panOffset.y / scale,
            right = (-panOffset.x + size.width) / scale,
            bottom = (-panOffset.y + size.height) / scale
        )
        drawRect(
            color = Color.White,
            topLeft = Offset(bgRect.left, bgRect.top),
            size = androidx.compose.ui.geometry.Size(bgRect.width, bgRect.height)
        )

        // Draw grid lines if useImageBackground is enabled
        if (useImageBackground) {
            val gridSize = 20f
            val gridColor = Color(0xFFE0E0E0) // Light gray
            
            // Use the actual visible canvas size (accounting for zoom/pan)
            val visibleWidth = size.width / scale
            val visibleHeight = size.height / scale
            val startX = -panOffset.x / scale
            val startY = -panOffset.y / scale
            val endX = startX + visibleWidth
            val endY = startY + visibleHeight
            
            // Draw vertical lines across entire visible area
            var x = (startX / gridSize).toInt() * gridSize
            while (x <= endX) {
                drawLine(
                    color = gridColor,
                    start = Offset(x, startY),
                    end = Offset(x, endY),
                    strokeWidth = 0.5f
                )
                x += gridSize
            }
            
            // Draw horizontal lines across entire visible area
            var y = (startY / gridSize).toInt() * gridSize
            while (y <= endY) {
                drawLine(
                    color = gridColor,
                    start = Offset(startX, y),
                    end = Offset(endX, y),
                    strokeWidth = 0.5f
                )
                y += gridSize
            }
        }
    }

    private fun DrawScope.drawShape(shape: WhiteboardShape) {
        when (shape.type) {
            WhiteboardShapeType.FREEHAND -> drawFreehand(shape)
            WhiteboardShapeType.LINE -> drawLine(shape)
            WhiteboardShapeType.RECTANGLE -> drawRectangle(shape)
            WhiteboardShapeType.CIRCLE -> drawCircle(shape)
            WhiteboardShapeType.OVAL -> drawOval(shape)
            WhiteboardShapeType.TRIANGLE -> drawPolygon(shape, 3)
            WhiteboardShapeType.PENTAGON -> drawPolygon(shape, 5)
            WhiteboardShapeType.HEXAGON -> drawPolygon(shape, 6)
            WhiteboardShapeType.OCTAGON -> drawPolygon(shape, 8)
            WhiteboardShapeType.RHOMBUS -> drawRhombus(shape)
            WhiteboardShapeType.PARALLELOGRAM -> drawParallelogram(shape)
            WhiteboardShapeType.TEXT -> drawText(shape)
            WhiteboardShapeType.IMAGE -> drawImage(shape)
        }
    }

    private fun DrawScope.drawFreehand(shape: WhiteboardShape) {
        if (shape.points.size < 2) return

        val path = Path().apply {
            moveTo(shape.points.first().x, shape.points.first().y)
            for (i in 1 until shape.points.size) {
                lineTo(shape.points[i].x, shape.points[i].y)
            }
        }

        drawPathWithDash(path, shape.color, shape.thickness, shape.lineType)
    }

    private fun DrawScope.drawLine(shape: WhiteboardShape) {
        val start = shape.start ?: return
        val end = shape.end ?: return

        val path = Path().apply {
            moveTo(start.x, start.y)
            lineTo(end.x, end.y)
        }

        drawPathWithDash(path, shape.color, shape.thickness, shape.lineType)
    }

    private fun DrawScope.drawRectangle(shape: WhiteboardShape) {
        val start = shape.start ?: return
        val end = shape.end ?: return

        val rect = Rect(
            left = minOf(start.x, end.x),
            top = minOf(start.y, end.y),
            right = maxOf(start.x, end.x),
            bottom = maxOf(start.y, end.y)
        )

        val path = Path().apply { addRect(rect) }
        drawPathWithDash(path, shape.color, shape.thickness, shape.lineType)
    }

    private fun DrawScope.drawCircle(shape: WhiteboardShape) {
        val start = shape.start ?: return
        val end = shape.end ?: return

        val center = Offset(
            (start.x + end.x) / 2f,
            (start.y + end.y) / 2f
        )
        val radius = minOf(
            abs(end.x - start.x),
            abs(end.y - start.y)
        ) / 2f

        val path = Path().apply {
            addOval(Rect(center = center, radius = radius))
        }
        drawPathWithDash(path, shape.color, shape.thickness, shape.lineType)
    }

    private fun DrawScope.drawOval(shape: WhiteboardShape) {
        val start = shape.start ?: return
        val end = shape.end ?: return

        val rect = Rect(
            left = minOf(start.x, end.x),
            top = minOf(start.y, end.y),
            right = maxOf(start.x, end.x),
            bottom = maxOf(start.y, end.y)
        )

        val path = Path().apply { addOval(rect) }
        drawPathWithDash(path, shape.color, shape.thickness, shape.lineType)
    }

    private fun DrawScope.drawPolygon(shape: WhiteboardShape, sides: Int) {
        val start = shape.start ?: return
        val end = shape.end ?: return

        val centerX = (start.x + end.x) / 2f
        val centerY = (start.y + end.y) / 2f
        val radius = minOf(
            abs(end.x - start.x),
            abs(end.y - start.y)
        ) / 2f

        val angle = (2 * PI) / sides

        val path = Path().apply {
            for (i in 0 until sides) {
                val x = centerX + radius * cos(i * angle - PI / 2).toFloat()
                val y = centerY + radius * sin(i * angle - PI / 2).toFloat()

                if (i == 0) {
                    moveTo(x, y)
                } else {
                    lineTo(x, y)
                }
            }
            close()
        }

        drawPathWithDash(path, shape.color, shape.thickness, shape.lineType)
    }

    private fun DrawScope.drawRhombus(shape: WhiteboardShape) {
        val start = shape.start ?: return
        val end = shape.end ?: return

        val centerX = (start.x + end.x) / 2f
        val centerY = (start.y + end.y) / 2f
        val halfWidth = abs(end.x - start.x) / 2f
        val halfHeight = abs(end.y - start.y) / 2f

        val path = Path().apply {
            moveTo(centerX, centerY - halfHeight)
            lineTo(centerX + halfWidth, centerY)
            lineTo(centerX, centerY + halfHeight)
            lineTo(centerX - halfWidth, centerY)
            close()
        }

        drawPathWithDash(path, shape.color, shape.thickness, shape.lineType)
    }

    private fun DrawScope.drawParallelogram(shape: WhiteboardShape) {
        val start = shape.start ?: return
        val end = shape.end ?: return

        val skew = abs(end.x - start.x) * 0.2f

        val path = Path().apply {
            moveTo(start.x + skew, start.y)
            lineTo(end.x, start.y)
            lineTo(end.x - skew, end.y)
            lineTo(start.x, end.y)
            close()
        }

        drawPathWithDash(path, shape.color, shape.thickness, shape.lineType)
    }

    private fun DrawScope.drawText(shape: WhiteboardShape) {
        val text = shape.text ?: return
        val start = shape.start ?: return

        val textLayoutResult = textMeasurer.measure(
            text = AnnotatedString(text),
            style = TextStyle(
                color = shape.color,
                fontSize = shape.fontSize.sp,
                fontFamily = null // Use default font
            )
        )

        drawText(textLayoutResult, topLeft = start)
    }

    private fun DrawScope.drawImage(shape: WhiteboardShape) {
        // Get shape bounds from points
        val start = shape.start ?: return
        val end = shape.end ?: return

        val rect = Rect(
            left = minOf(start.x, end.x),
            top = minOf(start.y, end.y),
            right = maxOf(start.x, end.x),
            bottom = maxOf(start.y, end.y)
        )
        
        // Try to decode the image from ByteArray
        val imageData = shape.imageData
        if (imageData != null && imageData.isNotEmpty()) {
            // Decode the image using platform-specific implementation
            val imageBitmap = decodeImageBitmap(imageData)
            
            if (imageBitmap != null) {
                // Draw the actual image
                drawImage(
                    image = imageBitmap,
                    dstOffset = androidx.compose.ui.unit.IntOffset(rect.left.toInt(), rect.top.toInt()),
                    dstSize = androidx.compose.ui.unit.IntSize(rect.width.toInt(), rect.height.toInt())
                )
            } else {
                // Image decoding failed - draw error placeholder
                drawImageErrorPlaceholder(rect)
            }
        } else {
            // No image data - draw error placeholder
            drawImageErrorPlaceholder(rect)
        }
    }
    
    private fun DrawScope.drawImageErrorPlaceholder(rect: Rect) {
        drawRect(
            color = Color(0xFFFFEBEE), // Light red background
            topLeft = Offset(rect.left, rect.top),
            size = androidx.compose.ui.geometry.Size(rect.width, rect.height)
        )
        drawRect(
            color = Color(0xFFF44336), // Red border
            topLeft = Offset(rect.left, rect.top),
            size = androidx.compose.ui.geometry.Size(rect.width, rect.height),
            style = Stroke(width = 2f)
        )
        
        // Draw X icon in center
        val centerX = rect.left + rect.width / 2
        val centerY = rect.top + rect.height / 2
        val iconSize = minOf(rect.width, rect.height) * 0.3f
        
        drawLine(
            color = Color(0xFFF44336),
            start = Offset(centerX - iconSize / 2, centerY - iconSize / 2),
            end = Offset(centerX + iconSize / 2, centerY + iconSize / 2),
            strokeWidth = 3f
        )
        drawLine(
            color = Color(0xFFF44336),
            start = Offset(centerX + iconSize / 2, centerY - iconSize / 2),
            end = Offset(centerX - iconSize / 2, centerY + iconSize / 2),
            strokeWidth = 3f
        )
    }

    private fun DrawScope.drawPathWithDash(
        path: Path,
        color: Color,
        thickness: Float,
        lineType: LineType
    ) {
        val pathEffect = getDashPathEffect(lineType, thickness)

        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = thickness,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
                pathEffect = pathEffect
            )
        )
    }

    private fun getDashPathEffect(lineType: LineType, strokeWidth: Float): PathEffect? {
        return when (lineType) {
            LineType.SOLID -> null
            LineType.DASHED -> {
                val dashLength = maxOf(10f, strokeWidth * 2)
                PathEffect.dashPathEffect(floatArrayOf(dashLength, dashLength), 0f)
            }
            LineType.DOTTED -> {
                val dotSize = maxOf(1f, strokeWidth * 0.3f)
                val gapSize = maxOf(6f, strokeWidth * 1.5f)
                PathEffect.dashPathEffect(floatArrayOf(dotSize, gapSize), 0f)
            }
            LineType.DASH_DOT -> {
                val dash = maxOf(10f, strokeWidth * 2)
                val shortGap = maxOf(5f, strokeWidth)
                val dot = maxOf(1f, strokeWidth * 0.3f)
                PathEffect.dashPathEffect(floatArrayOf(dash, shortGap, dot, shortGap), 0f)
            }
        }
    }

    private fun DrawScope.drawSelection(shape: WhiteboardShape) {
        val bounds = when {
            shape.start != null && shape.end != null -> {
                Rect(
                    left = minOf(shape.start!!.x, shape.end!!.x),
                    top = minOf(shape.start!!.y, shape.end!!.y),
                    right = maxOf(shape.start!!.x, shape.end!!.x),
                    bottom = maxOf(shape.start!!.y, shape.end!!.y)
                )
            }
            shape.points.isNotEmpty() -> {
                var minX = Float.MAX_VALUE
                var minY = Float.MAX_VALUE
                var maxX = Float.MIN_VALUE
                var maxY = Float.MIN_VALUE

                shape.points.forEach { point ->
                    minX = minOf(minX, point.x)
                    minY = minOf(minY, point.y)
                    maxX = maxOf(maxX, point.x)
                    maxY = maxOf(maxY, point.y)
                }

                Rect(minX, minY, maxX, maxY)
            }
            else -> return
        }

        // Draw selection rectangle
        drawRect(
            color = Color.Blue,
            topLeft = Offset(bounds.left, bounds.top),
            size = androidx.compose.ui.geometry.Size(bounds.width, bounds.height),
            style = Stroke(width = 2f)
        )

        // Draw corner handles
        val handleSize = 8f
        val handles = listOf(
            Offset(bounds.left, bounds.top),
            Offset(bounds.right, bounds.top),
            Offset(bounds.left, bounds.bottom),
            Offset(bounds.right, bounds.bottom)
        )

        handles.forEach { handle ->
            drawCircle(
                color = Color.Blue,
                radius = handleSize / 2f,
                center = handle
            )
        }

        // Draw center handle (for moving)
        drawCircle(
            color = Color.Red,
            radius = handleSize / 2f,
            center = Offset(bounds.center.x, bounds.center.y)
        )
    }

    private fun DrawScope.drawEraserCursor(position: Offset, thickness: Float) {
        // Draw eraser cursor outline
        drawCircle(
            color = Color.Red.copy(alpha = 0.5f),
            radius = thickness / 2f,
            center = position,
            style = Stroke(width = 2f)
        )

        // Draw filled semi-transparent circle
        drawCircle(
            color = Color.Red.copy(alpha = 0.1f),
            radius = thickness / 2f,
            center = position
        )
    }

    private fun DrawScope.drawEdgeMarkers(maxWidth: Float, maxHeight: Float) {
        // Draw subtle edge markers to show canvas bounds
        val markerColor = Color.LightGray.copy(alpha = 0.5f)
        val markerLength = 20f
        val markerWidth = 2f

        // Top-left corner
        drawLine(markerColor, Offset(0f, 0f), Offset(markerLength, 0f), markerWidth)
        drawLine(markerColor, Offset(0f, 0f), Offset(0f, markerLength), markerWidth)

        // Top-right corner
        drawLine(markerColor, Offset(size.width - markerLength, 0f), Offset(size.width, 0f), markerWidth)
        drawLine(markerColor, Offset(size.width, 0f), Offset(size.width, markerLength), markerWidth)

        // Bottom-left corner
        drawLine(markerColor, Offset(0f, size.height - markerLength), Offset(0f, size.height), markerWidth)
        drawLine(markerColor, Offset(0f, size.height), Offset(markerLength, size.height), markerWidth)

        // Bottom-right corner
        drawLine(markerColor, Offset(size.width, size.height - markerLength), Offset(size.width, size.height), markerWidth)
        drawLine(markerColor, Offset(size.width - markerLength, size.height), Offset(size.width, size.height), markerWidth)
    }
}
