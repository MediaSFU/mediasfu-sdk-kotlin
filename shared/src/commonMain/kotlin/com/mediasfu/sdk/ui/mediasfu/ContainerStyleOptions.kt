package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ContainerStyleOptions mirrors the Flutter SDK's container styling hooks so that
 * higher level parity work can flow without inventing new terminology. The class is
 * intentionally descriptive rather than prescriptive – downstream code can decide
 * how much of this metadata to honor in Compose.
 */
@Immutable
data class ContainerStyleOptions(
    val backgroundColor: Color? = null,
    val widthFraction: Float? = null,
    val heightFraction: Float? = null,
    val margin: ContainerSpacing? = null,
    val padding: ContainerSpacing? = null,
    val decoration: ContainerDecoration? = null,
    val alignment: Alignment? = null,
    val clipBehavior: ContainerClipBehavior = ContainerClipBehavior.None,
)

/** Describes margin/padding style with directional control. */
@Immutable
data class ContainerSpacing(
    val start: Dp = 0.dp,
    val top: Dp = 0.dp,
    val end: Dp = 0.dp,
    val bottom: Dp = 0.dp
) {
    companion object {
        val Zero = ContainerSpacing()
    }
}

/** Basic decoration metadata to keep parity with Flutter's BoxDecoration usage. */
@Immutable
data class ContainerDecoration(
    val border: BorderStroke? = null,
    val borderRadius: Dp = 0.dp,
    val shadowElevation: Dp = 0.dp,
    val tonalElevation: Dp = 0.dp,
    val shape: Shape? = null,
    val gradient: Brush? = null,
    val foregroundColor: Color? = null,
    val fontSizeOverride: Float? = null,
)

/** Compose-friendly clip behavior enum matching the Flutter names where possible. */
enum class ContainerClipBehavior {
    None,
    HardEdge,
    AntiAlias,
    AntiAliasWithSaveLayer
}

/** Applies the container style metadata to a modifier chain. */
fun Modifier.applyContainerStyle(style: ContainerStyleOptions): Modifier {
    var updated = this

    style.widthFraction?.let { fraction ->
        updated = updated.fillMaxWidth(fraction.coerceIn(0f, 1f))
    }

    style.heightFraction?.let { fraction ->
        updated = updated.fillMaxHeight(fraction.coerceIn(0f, 1f))
    }

    style.margin?.let { spacing ->
        updated = updated.padding(
            start = spacing.start,
            top = spacing.top,
            end = spacing.end,
            bottom = spacing.bottom
        )
    }

    val decoration = style.decoration
    val resolvedShape: Shape? = when {
        decoration?.shape != null -> decoration.shape
        decoration != null && decoration.borderRadius > 0.dp ->
            RoundedCornerShape(decoration.borderRadius)
        else -> null
    }

    decoration?.border?.let { border ->
        val shape = resolvedShape ?: RectangleShape
        updated = updated.border(border, shape)
    }

    decoration?.foregroundColor?.let { color ->
        resolvedShape?.let { shape ->
            updated = updated.background(color, shape)
        } ?: run {
            updated = updated.background(color)
        }
    }

    resolvedShape?.let { shape ->
        updated = updated.clip(shape)
    }

    when (style.clipBehavior) {
        ContainerClipBehavior.None -> Unit
        ContainerClipBehavior.HardEdge,
        ContainerClipBehavior.AntiAlias,
        ContainerClipBehavior.AntiAliasWithSaveLayer -> {
            updated = updated.clipToBounds()
        }
    }

    style.padding?.let { spacing ->
        updated = updated.padding(
            start = spacing.start,
            top = spacing.top,
            end = spacing.end,
            bottom = spacing.bottom
        )
    }

    val backgroundColor = style.backgroundColor
    if (backgroundColor != null && decoration?.foregroundColor == null) {
        resolvedShape?.let { shape ->
            updated = updated.background(backgroundColor, shape)
        } ?: run {
            updated = updated.background(backgroundColor)
        }
    }

    return updated
}
