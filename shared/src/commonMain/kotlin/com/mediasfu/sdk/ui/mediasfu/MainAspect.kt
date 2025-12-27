package com.mediasfu.sdk.ui.mediasfu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediasfu.sdk.consumers.ComponentSizes
import com.mediasfu.sdk.consumers.GeneratePageContentOptions
import com.mediasfu.sdk.consumers.generatePageContent
import com.mediasfu.sdk.consumers.updateMiniCardsGridImpl
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.Stream
import com.mediasfu.sdk.ui.MediaSfuUIComponent
import com.mediasfu.sdk.ui.components.display.DefaultFlexibleGrid
import com.mediasfu.sdk.ui.components.display.DefaultFlexibleVideo
import com.mediasfu.sdk.ui.components.display.DefaultMainAspectComponent
import com.mediasfu.sdk.ui.components.display.DefaultMainContainerComponent
import com.mediasfu.sdk.ui.components.display.DefaultMainGridComponent
import com.mediasfu.sdk.ui.components.display.DefaultMainScreenComponent
import com.mediasfu.sdk.ui.components.display.DefaultOtherGridComponent
import com.mediasfu.sdk.ui.components.display.DefaultPagination
import com.mediasfu.sdk.ui.components.display.DefaultSubAspectComponent
import com.mediasfu.sdk.ui.components.display.ControlButtonsComponentTouchOptions
import com.mediasfu.sdk.ui.components.display.DefaultControlButtonsComponentTouch
import com.mediasfu.sdk.ui.components.display.FlexibleGrid
import com.mediasfu.sdk.ui.components.display.FlexibleGridOptions
import com.mediasfu.sdk.ui.components.display.FlexibleVideo
import com.mediasfu.sdk.ui.components.display.FlexibleVideoOptions
import com.mediasfu.sdk.ui.components.display.MainAspectComponentOptions
import com.mediasfu.sdk.ui.components.display.MainContainerComponentOptions
import com.mediasfu.sdk.ui.components.display.MainGridComponentOptions
import com.mediasfu.sdk.ui.components.display.MainScreenComponentOptions
import com.mediasfu.sdk.ui.components.display.OtherGridComponentOptions
import com.mediasfu.sdk.ui.components.display.PaginationOptions
import com.mediasfu.sdk.ui.components.display.PaginationParameters
import com.mediasfu.sdk.ui.components.display.SubAspectComponentOptions
import com.mediasfu.sdk.ui.components.display.renderCompose
import com.mediasfu.sdk.model.BreakoutParticipant as ModelBreakoutParticipant
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * ConferenceRoomContent - Main wrapper for the video conference UI
 * Sets up the proper hierarchy: MainContainer -> MainAspect + SubAspect
 * This matches the React and Flutter implementations
 */
@Composable
internal fun ConferenceRoomContent(state: MediasfuGenericState) {
    val mainContainerComponent = remember {
        DefaultMainContainerComponent(
            MainContainerComponentOptions(
                backgroundColor = 0xFF172645.toInt()
            )
        )
    }

    mainContainerComponent.renderCompose {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            val shouldShowSubAspect = state.room.eventType == EventType.WEBINAR ||
                state.room.eventType == EventType.CONFERENCE
            val controlFraction = if (shouldShowSubAspect) {
                state.display.controlHeight.toFloat().coerceIn(0f, 0.5f)
            } else {
                0f
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f - controlFraction, fill = true)
            ) {
                MainAspectContent(state, shouldShowSubAspect)
            }

            if (shouldShowSubAspect) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    SubAspectContent(state)
                }
            }
        }
    }
}

/**
 * MainAspectContent - Main video grid layout component
 * Handles main grid, mini-view grid, and pagination
 * This component should NOT handle SubAspect (control buttons) - that's handled by ConferenceRoomContent
 */
@Composable
private fun MainAspectContent(
    state: MediasfuGenericState,
    shouldShowSubAspect: Boolean
) {
    val display = state.display
    val streams = state.streams
    val participants = state.room.filteredParticipants
    val participantList = participants.toList()

    val customComponent = state.options.customComponent
    if (customComponent != null) {
        customComponent.invoke(state)
        return
    }

    val placeholderStreams = remember(participantList) { buildPlaceholderStreams(participantList) }

    val totalPagesCount = state.totalPages()
    val totalPagesLastIndex = (totalPagesCount - 1).coerceAtLeast(0)
    val showPagination = display.doPaginate && totalPagesCount > 1

    // DEBUG: Pagination state

    val paginatedStreamsForPage = if (display.doPaginate) {
        streams.paginatedStreams.getOrNull(display.currentUserPage)?.toList().orEmpty()
    } else {
        emptyList()
    }

    val activeMainStreams = when {
        paginatedStreamsForPage.isNotEmpty() -> paginatedStreamsForPage
        streams.lStreams.isNotEmpty() -> streams.lStreams.toList()
        streams.currentStreams.isNotEmpty() -> streams.currentStreams.toList()
        else -> emptyList()
    }

    // DEBUG: Log stream sources

    val displayMainStreams = if (activeMainStreams.isNotEmpty()) {
        activeMainStreams
    } else {
        placeholderStreams
    }

    val baseMiniViewStreams = when {
        streams.mixedAlVideoStreams.isNotEmpty() -> streams.mixedAlVideoStreams.toList()
        streams.nonAlVideoStreams.isNotEmpty() -> streams.nonAlVideoStreams.toList()
        activeMainStreams.size > 1 -> activeMainStreams.drop(1)
        placeholderStreams.size > 1 -> placeholderStreams.drop(1)
        displayMainStreams.isNotEmpty() -> listOf(displayMainStreams.first())
        else -> emptyList()
    }

    val webinarSelfViewStream = if (state.room.eventType == EventType.WEBINAR) {
        val mainIdentifier = streams.mainScreenPerson
        if (mainIdentifier.isNullOrBlank()) {
            null
        } else {
            val candidates = buildList {
                addAll(streams.currentStreams)
                addAll(streams.lStreams)
                addAll(streams.nonAlVideoStreams)
                addAll(placeholderStreams)
            }
            candidates.firstOrNull { stream ->
                val identifierMatchesProducer = stream.producerId.equals(mainIdentifier, ignoreCase = true)
                val identifierMatchesName = stream.name?.equals(mainIdentifier, ignoreCase = true) == true
                val identifierMatchesId = stream.id?.equals(mainIdentifier, ignoreCase = true) == true
                identifierMatchesProducer || identifierMatchesName || identifierMatchesId
            }?.takeIf { candidate ->
                baseMiniViewStreams.none { it.producerId == candidate.producerId }
            }
        }
    } else {
        null
    }

    val miniViewStreams = remember(baseMiniViewStreams, webinarSelfViewStream) {
        buildList {
            webinarSelfViewStream?.let { add(it) }
            addAll(baseMiniViewStreams)
        }
    }

    val audioDecibels = remember(state.parameters.audioDecibels) {
        state.parameters.audioDecibels.toList()
    }

    val mainGridComponents = remember(displayMainStreams, participantList, audioDecibels, state.room.eventType) {
        displayMainStreams.forEachIndexed { i, stream ->
        }
        
        displayMainStreams.toDisplayComponents(
            participants = participants,
            audioDecibels = audioDecibels,
            isVideoCard = true,
            showControls = false,
            eventType = state.room.eventType
        ).also { components ->
        }
    }

    val miniViewComponents = remember(miniViewStreams, participantList, audioDecibels, state.room.eventType) {
        miniViewStreams.forEachIndexed { i, stream ->
        }
        
        miniViewStreams.toDisplayComponents(
            participants = participants,
            audioDecibels = audioDecibels,
            isVideoCard = false,
            showControls = false,
            eventType = state.room.eventType
        ).also { components ->
        }
    }

    // DO NOT call updateMainGridComponents() or updateMiniGridComponents()
    // These would overwrite the OtherGrid components stored by addVideosGrid
    // mainGridComponents here is for MainScreen (active speaker), not OtherGrid

    val mainAspectComponent = remember(shouldShowSubAspect, display.controlHeight) {
        DefaultMainAspectComponent(
            MainAspectComponentOptions(
                backgroundColor = 0xFF172645.toInt(),
                defaultFraction = 1.0 - display.controlHeight,
                showControls = shouldShowSubAspect,
                updateIsWideScreen = display::setWideScreenFlag,
                updateIsMediumScreen = display::setMediumScreenFlag,
                updateIsSmallScreen = display::setSmallScreenFlag
            )
        )
    }

    val mainScreenComponent = remember(display.mainHeightWidth) {
        DefaultMainScreenComponent(
            MainScreenComponentOptions(
                backgroundColor = 0xFF172645.toInt(),
                mainSize = display.mainHeightWidth,
                showAspect = true
            )
        )
    }

    mainAspectComponent.renderCompose {
        Box(modifier = Modifier.fillMaxSize()) {
            mainScreenComponent.renderCompose {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val density = LocalDensity.current

                    val widthDp = maxWidth
                    val heightDp = maxHeight.coerceAtLeast(0.dp)

                    val widthPx = with(density) { widthDp.toPx().coerceAtLeast(0f) }
                    val heightPx = with(density) { heightDp.toPx().coerceAtLeast(0f) }

                    val wideThresholdPx = with(density) { 768.dp.toPx() }
                    val mediumThresholdPx = with(density) { 576.dp.toPx() }

                    val isWideScreen = when {
                        widthPx.isNaN() || widthPx.isInfinite() -> false
                        heightPx.isNaN() || heightPx.isInfinite() -> widthPx >= wideThresholdPx
                        heightPx <= 0f -> widthPx >= wideThresholdPx
                        else -> widthPx >= wideThresholdPx || widthPx > 1.5f * heightPx
                    }
                    val isMediumScreen = widthPx in mediumThresholdPx..wideThresholdPx
                    val isSmallScreen = widthPx < mediumThresholdPx

                    LaunchedEffect(isWideScreen, isMediumScreen, isSmallScreen) {
                        display.setWideScreenFlag(isWideScreen)
                        display.setMediumScreenFlag(isMediumScreen && !isWideScreen)
                        display.setSmallScreenFlag(isSmallScreen)
                    }

                    val configuredMainPercent = display.mainHeightWidth
                    val clampedMainPercent = when {
                        configuredMainPercent.isNaN() || configuredMainPercent.isInfinite() -> 100.0
                        configuredMainPercent < 0.0 -> 0.0
                        configuredMainPercent > 100.0 -> 100.0
                        else -> configuredMainPercent
                    }

                    val mainFraction = (clampedMainPercent / 100.0).toFloat().coerceIn(0f, 1f)
                    val otherFraction = (1f - mainFraction).coerceIn(0f, 1f)
                    val doStack = true
                    val isWideStack = doStack && isWideScreen

                    val mainWidthDp = when {
                        !doStack -> widthDp
                        isWideStack -> widthDp * mainFraction
                        else -> widthDp
                    }
                    val otherWidthDp = when {
                        !doStack -> widthDp
                        isWideStack -> widthDp * otherFraction
                        else -> widthDp
                    }
                    val mainHeightDp = when {
                        !doStack -> heightDp
                        isWideStack -> heightDp
                        else -> heightDp * mainFraction
                    }
                    val otherHeightDp = when {
                        !doStack -> heightDp
                        isWideStack -> heightDp
                        else -> heightDp * otherFraction
                    }

                    val componentSizes = remember(mainWidthDp, mainHeightDp, otherWidthDp, otherHeightDp) {
                        ComponentSizes(
                            mainWidth = with(density) { mainWidthDp.toPx().coerceAtLeast(0f) }.toDouble(),
                            mainHeight = with(density) { mainHeightDp.toPx().coerceAtLeast(0f) }.toDouble(),
                            otherWidth = with(density) { otherWidthDp.toPx().coerceAtLeast(0f) }.toDouble(),
                            otherHeight = with(density) { otherHeightDp.toPx().coerceAtLeast(0f) }.toDouble()
                        )
                    }

                    LaunchedEffect(componentSizes) {
                        display.updateComponentSizes(componentSizes)
                    }

                    val shouldRenderMainGrid = mainFraction > 0f && mainHeightDp > 0.dp && mainWidthDp > 0.dp
                    val shouldRenderMiniGrid = otherFraction > 0f && otherHeightDp > 0.dp && otherWidthDp > 0.dp

                    val mainGridWidth = mainWidthDp.value.roundToInt().coerceAtLeast(0)
                    val mainGridHeight = mainHeightDp.value.roundToInt().coerceAtLeast(0)
                    val miniGridWidth = otherWidthDp.value.roundToInt().coerceAtLeast(0)
                    val miniGridHeight = otherHeightDp.value.roundToInt().coerceAtLeast(0)

                    val hasMainHeight = shouldRenderMainGrid && mainGridHeight > 0 && mainGridWidth > 0
                    val hasMiniHeight = shouldRenderMiniGrid && miniGridHeight > 0 && miniGridWidth > 0

                    val mainWeight = when {
                        !shouldRenderMainGrid -> 0f
                        shouldRenderMiniGrid -> mainFraction.coerceAtLeast(0.001f)
                        else -> 1f
                    }
                    val miniWeight = when {
                        !shouldRenderMiniGrid -> 0f
                        shouldRenderMainGrid -> (otherFraction).coerceAtLeast(0.001f)
                        else -> 1f
                    }

                    val (mainRows, mainColumns) = remember(mainGridComponents) {
                        val count = mainGridComponents.size.coerceAtLeast(1)
                        val estimatedRows = ceil(sqrt(count.toDouble())).toInt().coerceAtLeast(1)
                        val estimatedColumns = ((count + estimatedRows - 1) / estimatedRows).coerceAtLeast(1)
                        estimatedRows to estimatedColumns
                    }

                    val flexibleVideo = remember(
                        mainGridComponents,
                        mainGridWidth,
                        mainGridHeight,
                        mainRows,
                        mainColumns,
                        shouldRenderMainGrid
                    ) {
                        DefaultFlexibleVideo(
                            FlexibleVideoOptions(
                                customWidth = mainGridWidth,
                                customHeight = mainGridHeight,
                                rows = mainRows,
                                columns = mainColumns,
                                componentsToRender = mainGridComponents,
                                backgroundColor = 0xFF172645.toInt(),
                                showAspect = shouldRenderMainGrid && mainGridComponents.isNotEmpty()
                            )
                        )
                    }

                    // Read directly from SnapshotStateList for proper Compose reactivity
                    // (display.otherGridStreams creates new lists, breaking change detection)
                    val statePrimaryComponents = display.mainGridComponents.toList()
                    val stateAlternateComponents = display.miniGridComponents.toList()

                    val resolvedPrimaryComponents = if (statePrimaryComponents.isNotEmpty()) {
                        statePrimaryComponents
                    } else {
                        miniViewComponents
                    }
                    val resolvedAlternateComponents = stateAlternateComponents

                    val shouldShowPrimaryOtherGrid = resolvedPrimaryComponents.isNotEmpty()
                    val shouldShowAlternateOtherGrid = resolvedAlternateComponents.isNotEmpty()
                    val hasBothOtherGrids = shouldShowPrimaryOtherGrid && shouldShowAlternateOtherGrid

                    val (fallbackPrimaryRows, fallbackPrimaryCols) = remember(resolvedPrimaryComponents) {
                        calculateGridDimensions(resolvedPrimaryComponents.size)
                    }
                    val (fallbackAltRows, fallbackAltCols) = remember(resolvedAlternateComponents) {
                        calculateGridDimensions(resolvedAlternateComponents.size)
                    }

                    // Ensure minimum 1x1 grid when mini pane is allocated space (matches React/Flutter)
                    // This ensures the mini pane is always visible even when empty, just like React
                    val minGridDim = if (shouldRenderMiniGrid) 1 else 0
                    val primaryRows = if (display.gridRows > 0) {
                        display.gridRows
                    } else {
                        fallbackPrimaryRows.coerceAtLeast(minGridDim)
                    }
                    val primaryCols = if (display.gridCols > 0) {
                        display.gridCols
                    } else {
                        fallbackPrimaryCols.coerceAtLeast(minGridDim)
                    }
                    val altRows = if (display.altGridRows > 0) display.altGridRows else fallbackAltRows
                    val altCols = if (display.altGridCols > 0) display.altGridCols else fallbackAltCols

                    val densityFactor = density.density
                    val effectiveMiniGridWidth = miniGridWidth.coerceAtLeast(1)
                    val effectiveMiniGridHeight = miniGridHeight.coerceAtLeast(1)

                    val baseOtherHeight = effectiveMiniGridHeight.coerceAtLeast(0)
                    val primaryContainerHeight = when {
                        hasBothOtherGrids -> (baseOtherHeight / 2).coerceAtLeast(1)
                        shouldShowPrimaryOtherGrid -> baseOtherHeight
                        else -> 0
                    }
                    val alternateContainerHeight = when {
                        hasBothOtherGrids -> (baseOtherHeight - primaryContainerHeight).coerceAtLeast(0)
                        shouldShowAlternateOtherGrid -> baseOtherHeight
                        else -> 0
                    }

                    val gridSizesState = display.gridSizes
                    val primaryGridWidthForOptions = if (gridSizesState.gridWidth > 0) {
                        (gridSizesState.gridWidth / densityFactor).roundToInt().coerceAtLeast(1)
                    } else {
                        effectiveMiniGridWidth
                    }
                    val primaryGridHeightForOptions = if (gridSizesState.gridHeight > 0) {
                        (gridSizesState.gridHeight / densityFactor).roundToInt().coerceAtLeast(1)
                    } else {
                        primaryContainerHeight.coerceAtLeast(1)
                    }

                    val alternateGridWidthForOptions = if (gridSizesState.altGridWidth > 0) {
                        (gridSizesState.altGridWidth / densityFactor).roundToInt().coerceAtLeast(1)
                    } else {
                        effectiveMiniGridWidth
                    }
                    val alternateGridHeightForOptions = if (gridSizesState.altGridHeight > 0) {
                        (gridSizesState.altGridHeight / densityFactor).roundToInt().coerceAtLeast(1)
                    } else {
                        alternateContainerHeight.coerceAtLeast(1)
                    }

                    val primaryFlexGrid = remember(
                        resolvedPrimaryComponents,
                        primaryRows,
                        primaryCols,
                        primaryGridWidthForOptions,
                        primaryGridHeightForOptions,
                        shouldShowPrimaryOtherGrid,
                        display.gridRows,
                        display.gridCols,
                        gridSizesState.gridWidth,
                        gridSizesState.gridHeight
                    ) {
                        DefaultFlexibleGrid(
                            FlexibleGridOptions(
                                customWidth = primaryGridWidthForOptions,
                                customHeight = primaryGridHeightForOptions,
                                rows = primaryRows,
                                columns = primaryCols,
                                componentsToRender = resolvedPrimaryComponents,
                                backgroundColor = 0xFF0F1A2D.toInt(),
                                showAspect = shouldShowPrimaryOtherGrid
                            )
                        )
                    }

                    val alternateFlexGrid = remember(
                        resolvedAlternateComponents,
                        altRows,
                        altCols,
                        alternateGridWidthForOptions,
                        alternateGridHeightForOptions,
                        shouldShowAlternateOtherGrid,
                        display.altGridRows,
                        display.altGridCols,
                        gridSizesState.altGridWidth,
                        gridSizesState.altGridHeight
                    ) {
                        DefaultFlexibleGrid(
                            FlexibleGridOptions(
                                customWidth = alternateGridWidthForOptions,
                                customHeight = alternateGridHeightForOptions,
                                rows = altRows,
                                columns = altCols,
                                componentsToRender = resolvedAlternateComponents,
                                backgroundColor = 0xFF0F1A2D.toInt(),
                                showAspect = shouldShowAlternateOtherGrid
                            )
                        )
                    }

                    LaunchedEffect(
                        primaryRows,
                        primaryCols,
                        altRows,
                        altCols,
                        display.componentSizes,
                        display.doPaginate,
                        display.paginationDirection,
                        display.paginationHeightWidth,
                        resolvedPrimaryComponents.size,
                        resolvedAlternateComponents.size
                    ) {
                        if (display.componentSizes.otherWidth > 0.0) {
                            val primaryActualRows = if (resolvedPrimaryComponents.isNotEmpty()) primaryRows.coerceAtLeast(1) else 0
                            updateMiniCardsGridImpl(
                                rows = primaryRows,
                                cols = primaryCols,
                                defal = true,
                                actualRows = primaryActualRows,
                                gridSizes = display.gridSizes,
                                paginationDirection = display.paginationDirection,
                                paginationHeightWidth = display.paginationHeightWidth,
                                doPaginate = display.doPaginate,
                                componentSizes = display.componentSizes,
                                eventType = state.room.eventType.name,
                                updateGridRows = display::updateGridRows,
                                updateGridCols = display::updateGridCols,
                                updateAltGridRows = display::updateAltGridRows,
                                updateAltGridCols = display::updateAltGridCols,
                                updateGridSizes = display::updateGridSizes
                            )

                            val altActualRows = if (resolvedAlternateComponents.isNotEmpty()) altRows.coerceAtLeast(1) else 0
                            updateMiniCardsGridImpl(
                                rows = altRows,
                                cols = altCols,
                                defal = false,
                                actualRows = altActualRows,
                                gridSizes = display.gridSizes,
                                paginationDirection = display.paginationDirection,
                                paginationHeightWidth = display.paginationHeightWidth,
                                doPaginate = display.doPaginate,
                                componentSizes = display.componentSizes,
                                eventType = state.room.eventType.name,
                                updateGridRows = display::updateGridRows,
                                updateGridCols = display::updateGridCols,
                                updateAltGridRows = display::updateAltGridRows,
                                updateAltGridCols = display::updateAltGridCols,
                                updateGridSizes = display::updateGridSizes
                            )
                        }
                    }

                    LaunchedEffect(shouldShowPrimaryOtherGrid) {
                        display.setAddGridEnabled(shouldShowPrimaryOtherGrid)
                    }

                    LaunchedEffect(shouldShowAlternateOtherGrid) {
                        display.setAddAltGridEnabled(shouldShowAlternateOtherGrid)
                    }

                    val hasMainPane = hasMainHeight
                    val hasOtherPane = shouldRenderMiniGrid && hasMiniHeight
                    val hasBothPanes = hasMainPane && hasOtherPane
                    val resolvedMainWeight = if (hasBothPanes) mainWeight.coerceAtLeast(0.001f) else 1f
                    val resolvedMiniWeight = if (hasBothPanes) miniWeight.coerceAtLeast(0.001f) else 1f

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (isWideStack) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = true),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (hasMainPane) {
                                    Box(
                                        modifier = Modifier
                                            .weight(resolvedMainWeight, fill = true)
                                            .fillMaxHeight()
                                    ) {
                                        MainGridPane(
                                            state = state,
                                            display = display,
                                            mainGridComponents = mainGridComponents,
                                            flexibleVideo = flexibleVideo,
                                            mainGridWidth = mainGridWidth,
                                            mainGridHeight = mainGridHeight,
                                            shouldRenderMainGrid = shouldRenderMainGrid
                                        )
                                    }
                                }

                                if (hasOtherPane) {
                                    Box(
                                        modifier = Modifier
                                            .weight(resolvedMiniWeight, fill = true)
                                            .fillMaxHeight()
                                    ) {
                                        OtherGridPane(
                                            display = display,
                                            state = state,
                                            resolvedPrimaryComponents = resolvedPrimaryComponents,
                                            resolvedAlternateComponents = resolvedAlternateComponents,
                                            miniGridWidth = miniGridWidth,
                                            miniGridHeight = miniGridHeight,
                                            shouldRenderMiniGrid = shouldRenderMiniGrid,
                                            shouldShowPrimaryOtherGrid = shouldShowPrimaryOtherGrid,
                                            shouldShowAlternateOtherGrid = shouldShowAlternateOtherGrid,
                                            primaryGridHeight = primaryGridHeightForOptions,
                                            alternateGridHeight = alternateGridHeightForOptions,
                                            primaryFlexGrid = primaryFlexGrid,
                                            alternateFlexGrid = alternateFlexGrid,
                                            showPagination = showPagination,
                                            totalPages = totalPagesCount
                                        )
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = true),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (hasMainPane) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(resolvedMainWeight, fill = true)
                                    ) {
                                        MainGridPane(
                                            state = state,
                                            display = display,
                                            mainGridComponents = mainGridComponents,
                                            flexibleVideo = flexibleVideo,
                                            mainGridWidth = mainGridWidth,
                                            mainGridHeight = mainGridHeight,
                                            shouldRenderMainGrid = shouldRenderMainGrid
                                        )
                                    }
                                }

                                if (hasOtherPane) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(resolvedMiniWeight, fill = true)
                                    ) {
                                        OtherGridPane(
                                            display = display,
                                            state = state,
                                            resolvedPrimaryComponents = resolvedPrimaryComponents,
                                            resolvedAlternateComponents = resolvedAlternateComponents,
                                            miniGridWidth = miniGridWidth,
                                            miniGridHeight = miniGridHeight,
                                            shouldRenderMiniGrid = shouldRenderMiniGrid,
                                            shouldShowPrimaryOtherGrid = shouldShowPrimaryOtherGrid,
                                            shouldShowAlternateOtherGrid = shouldShowAlternateOtherGrid,
                                            primaryGridHeight = primaryGridHeightForOptions,
                                            alternateGridHeight = alternateGridHeightForOptions,
                                            primaryFlexGrid = primaryFlexGrid,
                                            alternateFlexGrid = alternateFlexGrid,
                                            showPagination = showPagination,
                                            totalPages = totalPagesCount
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.MainGridPane(
    state: MediasfuGenericState,
    display: DisplayState,
    mainGridComponents: List<MediaSfuUIComponent>,
    flexibleVideo: FlexibleVideo,
    mainGridWidth: Int,
    mainGridHeight: Int,
    shouldRenderMainGrid: Boolean
) {
    val mainGridComponent = remember(
        mainGridComponents,
        mainGridWidth,
        mainGridHeight,
        display.mainHeightWidth,
        state.meeting.isVisible,
        shouldRenderMainGrid
    ) {
        DefaultMainGridComponent(
            MainGridComponentOptions(
                height = mainGridHeight,
                width = mainGridWidth,
                backgroundColor = 0xFF172645.toInt(),
                mainSize = display.mainHeightWidth,
                showAspect = shouldRenderMainGrid,
                timeBackgroundColor = 0xFF2E7D32.toInt(),
                showTimer = state.meeting.isVisible
            )
        )
    }

    val timerSlot: @Composable BoxScope.() -> Unit = {
        MeetingProgressTimerBadge(
            state = state,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        )
    }

    mainGridComponent.renderCompose(
        renderTimer = state.meeting.isVisible,
        timer = if (state.meeting.isVisible) timerSlot else null
    ) {
        if (shouldRenderMainGrid && mainGridComponents.isNotEmpty()) {
            flexibleVideo.renderCompose()
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1C2B4A)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Waiting for streams...",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
    
    // Broadcast recording controls overlay - shown for broadcast events when host
    val isBroadcast = state.room.eventType == EventType.BROADCAST
    val isHost = state.room.islevel == "2" // "2" = host level
    val showRecordButtons = state.recording.showRecordButtons
    
    if (isBroadcast && isHost) {
        BroadcastRecordingControls(
            state = state,
            showExpandedControls = showRecordButtons,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

/**
 * BroadcastRecordingControls - Recording controls overlay for broadcast events
 * Shows either a single "Record" button or expanded controls (pause/stop/timer/status/settings)
 * Only visible for host (islevel == '2') in broadcast mode
 */
@Composable
private fun BroadcastRecordingControls(
    state: MediasfuGenericState,
    showExpandedControls: Boolean,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val isPaused = state.recording.recordPaused
    val progressTime = state.recording.recordingProgressTime
    val recordState = state.recording.recordState
    
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!showExpandedControls) {
            // Single "Record" button to start recording
            IconButton(
                onClick = { state.toggleRecording() },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.FiberManualRecord,
                    contentDescription = "Start Recording",
                    tint = Color(0xFFFF4D4F), // Red
                    modifier = Modifier.size(28.dp)
                )
            }
        } else {
            // Expanded controls: Play/Pause, Stop, Timer, Status, Settings
            
            // Play/Pause Button
            IconButton(
                onClick = { 
                    coroutineScope.launch {
                        state.handleUpdateRecording()
                    }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Rounded.PlayCircle else Icons.Rounded.PauseCircle,
                    contentDescription = if (isPaused) "Resume Recording" else "Pause Recording",
                    tint = Color(0xFF52C41A), // Green
                    modifier = Modifier.size(28.dp)
                )
            }
            
            // Stop Button
            IconButton(
                onClick = { 
                    coroutineScope.launch {
                        state.handleStopRecording()
                    }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.StopCircle,
                    contentDescription = "Stop Recording",
                    tint = Color(0xFFFF4D4F), // Red
                    modifier = Modifier.size(28.dp)
                )
            }
            
            // Timer Display
            Text(
                text = progressTime.ifEmpty { "00:00:00" },
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            
            // Status Indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        when (recordState) {
                            "yellow" -> Color(0xFFFAAD14) // Paused - yellow
                            "red" -> Color(0xFFFF4D4F) // Recording - red
                            else -> Color(0xFF52C41A) // Stopped - green
                        }
                    )
            )
            
            // Settings Button
            IconButton(
                onClick = { 
                    if (isPaused) {
                        state.toggleRecording() 
                    }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Recording Settings",
                    tint = if (isPaused) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun BoxScope.OtherGridPane(
    display: DisplayState,
    state: MediasfuGenericState,
    resolvedPrimaryComponents: List<MediaSfuUIComponent>,
    resolvedAlternateComponents: List<MediaSfuUIComponent>,
    miniGridWidth: Int,
    miniGridHeight: Int,
    shouldRenderMiniGrid: Boolean,
    shouldShowPrimaryOtherGrid: Boolean,
    shouldShowAlternateOtherGrid: Boolean,
    primaryGridHeight: Int,
    alternateGridHeight: Int,
    primaryFlexGrid: FlexibleGrid,
    alternateFlexGrid: FlexibleGrid,
    showPagination: Boolean = false,
    totalPages: Int = 0
) {
    val otherGridComponent = remember(
        resolvedPrimaryComponents,
        resolvedAlternateComponents,
        miniGridWidth,
        miniGridHeight,
        display.altGridRows,
        display.altGridCols,
        shouldShowPrimaryOtherGrid,
        shouldShowAlternateOtherGrid,
        shouldRenderMiniGrid
    ) {
        DefaultOtherGridComponent(
            OtherGridComponentOptions(
                height = miniGridHeight,
                width = miniGridWidth,
                backgroundColor = 0xFF0F1A2D.toInt(),
                gridSize = display.altGridRows.toDouble(),
                showAspect = shouldRenderMiniGrid
            )
        )
    }

    otherGridComponent.renderCompose {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Pagination ON TOP OF the first FlexibleGrid (inside OtherGrid)
            if (showPagination && state.paginationParameters != null) {
                val coroutineScope = rememberCoroutineScope()
                val pagination = remember(display.currentUserPage, totalPages, state.parameters) {
                    DefaultPagination(
                        PaginationOptions(
                            totalPages = totalPages,
                            currentUserPage = display.currentUserPage,
                            handlePageChange = { options ->
                                coroutineScope.launch {
                                    generatePageContent(options)
                                }
                            },
                            parameters = state.paginationParameters,
                            position = "middle",
                            location = "top",
                            direction = "horizontal",
                            backgroundColor = 0x331E88E5,
                            activeColor = 0xFF1E88E5.toInt(),
                            inactiveColor = 0xFFFFFFFF.toInt()
                        )
                    )
                }
                pagination.renderCompose()
            }

            // First FlexibleGrid (below pagination)
            if (primaryGridHeight > 0) {
                primaryFlexGrid.renderCompose()
            }

            // Alternate grid below primary
            if (shouldShowAlternateOtherGrid && alternateGridHeight > 0) {
                alternateFlexGrid.renderCompose()
            }
        }
    }
}

/**
 * SubAspectContent - Control buttons component
 * Only rendered for webinar and conference event types
 * Should be a sibling to MainAspectContent at the ConferenceRoomContent level
 */
@Composable
private fun SubAspectContent(state: MediasfuGenericState) {
    val subAspectComponent = remember {
        DefaultSubAspectComponent(
            SubAspectComponentOptions(
                backgroundColor = 0xFF0F1A2D.toInt(),
                showControls = true,
                defaultFraction = 40.0 // 40px height
            )
        )
    }

    subAspectComponent.renderCompose {
        val controlButtons = remember(
            state.media.audioAlreadyOn,
            state.media.videoAlreadyOn,
            state.media.screenAlreadyOn,
            state.recording.recordStarted,
            state.messaging.showMessagesBadge,
            state.room.participantsCounter,
            state.requests.totalPending
        ) {
            state.primaryControlButtons(includeExtended = false)
        }

        val visibleButtons = remember(controlButtons) {
            controlButtons.filter { it.isVisible }
        }

        if (visibleButtons.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                visibleButtons.forEach { button ->
                    val iconVector = if (button.isActive && button.alternateIcon != null) {
                        button.alternateIcon
                    } else {
                        button.icon
                    }
                    val baseTint = if (button.isActive) button.activeTint else button.inactiveTint
                    val iconTint = if (button.isEnabled) baseTint else baseTint.copy(alpha = 0.4f)
                    val backgroundTint = if (button.isActive) {
                        button.activeTint.copy(alpha = 0.2f)
                    } else {
                        Color.Transparent
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(backgroundTint)
                            .clickable(enabled = button.isEnabled) { button.onClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = button.label,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp)
                        )

                        button.badgeText?.let { badgeText ->
                            if (badgeText.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .background(button.badgeColor, CircleShape)
                                        .size(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = badgeText,
                                        color = Color.White,
                                        fontSize = 8.sp
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .background(button.badgeColor, CircleShape)
                                        .size(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun calculateGridDimensions(componentCount: Int): Pair<Int, Int> {
    if (componentCount <= 0) return 0 to 0
    val columns = ceil(sqrt(componentCount.toDouble())).toInt().coerceAtLeast(1)
    val rows = ((componentCount + columns - 1) / columns).coerceAtLeast(1)
    return rows to columns
}