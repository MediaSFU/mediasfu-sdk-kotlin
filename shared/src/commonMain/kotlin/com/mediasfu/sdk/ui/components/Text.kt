package com.mediasfu.sdk.ui.components

import kotlinx.datetime.Clock
import com.mediasfu.sdk.ui.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Text - A UI component for displaying text content.
 * 
 * This component provides:
 * - Text display with customizable styling
 * - Support for different text alignments
 * - Text overflow handling
 * - Support for rich text formatting
 * - Responsive text sizing
 * - Text selection support
 */
class Text(
    private val options: TextOptions
) : BaseMediaSfuUIComponent("text_${Clock.System.now().toEpochMilliseconds()}"),
    StylableComponent {
    
    private var _currentStyle = options.style
    override val currentStyle: ComponentStyle
        get() = _currentStyle
    
    private val _text = MutableStateFlow(options.text)
    val text: StateFlow<String> = _text.asStateFlow()
    
    override fun applyStyle(style: ComponentStyle) {
        _currentStyle = style
        onStyleApplied(style)
    }
    
    /**
     * Set the text content.
     */
    fun setText(text: String) {
        _text.value = text
        onTextChanged(text)
    }
    
    /**
     * Get the current text content.
     */
    fun getText(): String = _text.value
    
    /**
     * Append text to the current content.
     */
    fun appendText(text: String) {
        _text.value += text
        onTextChanged(_text.value)
    }
    
    /**
     * Clear the text content.
     */
    fun clearText() {
        _text.value = ""
        onTextChanged("")
    }
    
    /**
     * Set the text alignment.
     */
    fun setAlignment(alignment: TextAlignment) {
        onAlignmentChanged(alignment)
    }
    
    /**
     * Set the text color.
     */
    fun setTextColor(color: Color) {
        val newStyle = _currentStyle.copy(textColor = color)
        applyStyle(newStyle)
    }
    
    /**
     * Set the font size.
     */
    fun setFontSize(size: Float) {
        val newStyle = _currentStyle.copy(fontSize = size)
        applyStyle(newStyle)
    }
    
    /**
     * Set the font weight.
     */
    fun setFontWeight(weight: FontWeight) {
        val newStyle = _currentStyle.copy(fontWeight = weight)
        applyStyle(newStyle)
    }
    
    /**
     * Get the text metrics (width, height, etc.).
     */
    fun getTextMetrics(): TextMetrics {
        return calculateTextMetrics(_text.value)
    }
    
    // Private methods for internal functionality
    
    private fun onTextChanged(text: String) {
        // Platform-specific text change logic
        platformOnTextChanged(text)
    }
    
    private fun onAlignmentChanged(alignment: TextAlignment) {
        // Platform-specific alignment change logic
        platformOnAlignmentChanged(alignment)
    }
    
    private fun onStyleApplied(style: ComponentStyle) {
        // Platform-specific style application
        platformApplyStyle(style)
    }
    
    private fun calculateTextMetrics(text: String): TextMetrics {
        // Calculate text metrics based on current style and text content
        // This would typically be implemented in platform-specific code
        return TextMetrics(
            width = text.length * _currentStyle.fontSize * 0.6f, // Approximate
            height = _currentStyle.fontSize * 1.2f, // Approximate
            lineCount = text.split('\n').size,
            characterCount = text.length
        )
    }
    
    // Platform-specific methods (to be implemented in platform-specific code)
    private fun platformOnTextChanged(text: String) {
        // Platform-specific text change logic
    }
    
    private fun platformOnAlignmentChanged(alignment: TextAlignment) {
        // Platform-specific alignment change logic
    }
    
    private fun platformApplyStyle(style: ComponentStyle) {
        // Platform-specific style application
    }
}

/**
 * Configuration options for the Text component.
 */
data class TextOptions(
    val text: String = "",
    val style: ComponentStyle = ComponentStyle(),
    val alignment: TextAlignment = TextAlignment.Start,
    val maxLines: Int? = null,
    val overflow: TextOverflow = TextOverflow.Clip,
    val selectable: Boolean = false,
    val color: Color = Color.Black,
    val fontSize: Float = 14f,
    val fontWeight: FontWeight = FontWeight.Normal,
    val fontFamily: String? = null,
    val lineHeight: Float = 1.2f,
    val letterSpacing: Float = 0f,
    val wordSpacing: Float = 0f,
    val textDecoration: TextDecoration = TextDecoration.None,
    val backgroundColor: Color = Color.Transparent,
    val padding: EdgeInsets = EdgeInsets.zero,
    val margin: EdgeInsets = EdgeInsets.zero
)

/**
 * Represents different text alignment options.
 */
enum class TextAlignment {
    Start, Center, End, Justify
}

/**
 * Represents different text overflow handling options.
 */
enum class TextOverflow {
    Clip, Ellipsis, Fade, Visible
}

/**
 * Represents different text decoration options.
 */
enum class TextDecoration {
    None, Underline, Overline, LineThrough
}

/**
 * Represents text metrics information.
 */
data class TextMetrics(
    val width: Float,
    val height: Float,
    val lineCount: Int,
    val characterCount: Int
)
