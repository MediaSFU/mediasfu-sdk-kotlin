package com.mediasfu.sdk.ui.components

import kotlinx.datetime.Clock
import com.mediasfu.sdk.ui.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Grid - A UI component for arranging child components in a grid layout.
 * 
 * This component provides:
 * - Flexible grid layout with configurable columns and rows
 * - Responsive grid that adapts to content and container size
 * - Support for different grid item sizes and spacing
 * - Alignment and justification options
 * - Dynamic addition and removal of grid items
 * - Support for different grid item aspect ratios
 */
class Grid(
    private val options: GridOptions
) : BaseMediaSfuUIComponent("grid_${Clock.System.now().toEpochMilliseconds()}"),
    LayoutComponent,
    StylableComponent {
    
    private val _children = MutableStateFlow<List<MediaSfuUIComponent>>(emptyList())
    override val children: StateFlow<List<MediaSfuUIComponent>> = _children.asStateFlow()
    
    private var _currentStyle = options.style
    override val currentStyle: ComponentStyle
        get() = _currentStyle
    
    private val gridItems = mutableListOf<GridItem>()
    
    init {
        // Initialize with content if provided
        options.items?.forEach { item ->
            addGridItem(item)
        }
    }
    
    override fun addChild(component: MediaSfuUIComponent) {
        _children.value = _children.value + component
        onChildAdded(component)
    }
    
    override fun removeChild(component: MediaSfuUIComponent) {
        _children.value = _children.value - component
        onChildRemoved(component)
    }
    
    override fun clearChildren() {
        _children.value.forEach { it.dispose() }
        _children.value = emptyList()
        gridItems.clear()
        onChildrenCleared()
    }
    
    override fun applyStyle(style: ComponentStyle) {
        _currentStyle = style
        onStyleApplied(style)
    }
    
    /**
     * Add a grid item to the grid.
     */
    fun addGridItem(item: GridItem) {
        gridItems.add(item)
        addChild(item.component)
        onGridItemAdded(item)
    }
    
    /**
     * Remove a grid item from the grid.
     */
    fun removeGridItem(item: GridItem) {
        gridItems.remove(item)
        removeChild(item.component)
        onGridItemRemoved(item)
    }
    
    /**
     * Get all grid items.
     */
    fun getGridItems(): List<GridItem> = gridItems.toList()
    
    /**
     * Get grid items in a specific row.
     */
    fun getGridItemsInRow(row: Int): List<GridItem> {
        return gridItems.filter { it.row == row }
    }
    
    /**
     * Get grid items in a specific column.
     */
    fun getGridItemsInColumn(column: Int): List<GridItem> {
        return gridItems.filter { it.column == column }
    }
    
    /**
     * Get a grid item at specific row and column.
     */
    fun getGridItemAt(row: Int, column: Int): GridItem? {
        return gridItems.find { it.row == row && it.column == column }
    }
    
    /**
     * Update the grid layout configuration.
     */
    fun updateLayout(columns: Int? = null, rows: Int? = null, spacing: Float? = null) {
        val newColumns = columns ?: options.columns
        val newRows = rows ?: options.rows
        val newSpacing = spacing ?: options.spacing
        
        onLayoutUpdated(newColumns, newRows, newSpacing)
    }
    
    /**
     * Calculate the optimal grid layout based on content.
     */
    fun calculateOptimalLayout(): GridLayout {
        val itemCount = gridItems.size
        val aspectRatio = options.aspectRatio
        
        // Calculate optimal columns and rows based on item count and aspect ratio
        val optimalColumns = when {
            itemCount <= 1 -> 1
            itemCount <= 4 -> 2
            itemCount <= 9 -> 3
            itemCount <= 16 -> 4
            else -> kotlin.math.ceil(kotlin.math.sqrt(itemCount.toDouble())).toInt()
        }
        
        val optimalRows = kotlin.math.ceil(itemCount.toDouble() / optimalColumns).toInt()
        
        return GridLayout(optimalColumns, optimalRows)
    }
    
    /**
     * Get the current grid layout information.
     */
    fun getCurrentLayout(): GridLayout {
        return GridLayout(options.columns, options.rows)
    }
    
    // Private methods for internal functionality
    
    private fun onChildAdded(component: MediaSfuUIComponent) {
        // Platform-specific child addition logic
        platformAddChild(component)
    }
    
    private fun onChildRemoved(component: MediaSfuUIComponent) {
        // Platform-specific child removal logic
        platformRemoveChild(component)
    }
    
    private fun onChildrenCleared() {
        // Platform-specific children clear logic
        platformClearChildren()
    }
    
    private fun onStyleApplied(style: ComponentStyle) {
        // Platform-specific style application
        platformApplyStyle(style)
    }
    
    private fun onGridItemAdded(item: GridItem) {
        // Platform-specific grid item addition logic
        platformAddGridItem(item)
    }
    
    private fun onGridItemRemoved(item: GridItem) {
        // Platform-specific grid item removal logic
        platformRemoveGridItem(item)
    }
    
    private fun onLayoutUpdated(columns: Int, rows: Int, spacing: Float) {
        // Platform-specific layout update logic
        platformUpdateLayout(columns, rows, spacing)
    }
    
    // Platform-specific methods (to be implemented in platform-specific code)
    private fun platformAddChild(component: MediaSfuUIComponent) {
        // Platform-specific child addition
    }
    
    private fun platformRemoveChild(component: MediaSfuUIComponent) {
        // Platform-specific child removal
    }
    
    private fun platformClearChildren() {
        // Platform-specific children clear
    }
    
    private fun platformApplyStyle(style: ComponentStyle) {
        // Platform-specific style application
    }
    
    private fun platformAddGridItem(item: GridItem) {
        // Platform-specific grid item addition
    }
    
    private fun platformRemoveGridItem(item: GridItem) {
        // Platform-specific grid item removal
    }
    
    private fun platformUpdateLayout(columns: Int, rows: Int, spacing: Float) {
        // Platform-specific layout update
    }
    
    override fun onDispose() {
        super.onDispose()
        clearChildren()
    }
}

/**
 * Represents a grid item with its component and position information.
 */
data class GridItem(
    val component: MediaSfuUIComponent,
    val row: Int,
    val column: Int,
    val rowSpan: Int = 1,
    val columnSpan: Int = 1,
    val aspectRatio: Float = 16f / 9f,
    val minWidth: Float = 0f,
    val minHeight: Float = 0f,
    val maxWidth: Float = Float.MAX_VALUE,
    val maxHeight: Float = Float.MAX_VALUE
)

/**
 * Represents grid layout information.
 */
data class GridLayout(
    val columns: Int,
    val rows: Int
)

/**
 * Configuration options for the Grid component.
 */
data class GridOptions(
    val columns: Int = 2,
    val rows: Int = 2,
    val items: List<GridItem>? = null,
    val style: ComponentStyle = ComponentStyle(),
    val spacing: Float = 8f,
    val aspectRatio: Float = 16f / 9f,
    val alignment: Alignment = Alignment.Center,
    val justifyContent: Alignment = Alignment.Center,
    val backgroundColor: Color = Color.Transparent,
    val padding: EdgeInsets = EdgeInsets.zero,
    val margin: EdgeInsets = EdgeInsets.zero,
    val borderRadius: Float = 0f,
    val borderWidth: Float = 0f,
    val borderColor: Color = Color.Transparent,
    val responsive: Boolean = true,
    val maxItemsPerRow: Int = 4,
    val minItemWidth: Float = 200f,
    val minItemHeight: Float = 150f
)
