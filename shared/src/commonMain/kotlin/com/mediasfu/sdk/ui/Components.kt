@file:Suppress("FunctionName")

package com.mediasfu.sdk.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mediasfu.sdk.ui.components.display.*

/**
 * MediaSFU Component Factory Functions
 * 
 * These provide Flutter/React-like API for easy component usage.
 * Instead of: DefaultAudioGrid(options).renderCompose()
 * Just use:   AudioGrid(options)
 * 
 * Example:
 * ```kotlin
 * @Composable
 * fun MyScreen() {
 *     AudioGrid(AudioGridOptions(
 *         participants = participants,
 *         columnsPerRow = 3
 *     ))
 * }
 * ```
 */

// ============ Display Components ============

@Composable
fun AudioGrid(options: AudioGridOptions) {
    DefaultAudioGrid(options).renderCompose()
}

@Composable
fun AudioCard(options: AudioCardOptions, modifier: Modifier = Modifier) {
    DefaultAudioCard(options).renderCompose(modifier)
}

@Composable
fun MiniAudio(options: MiniAudioOptions, modifier: Modifier = Modifier) {
    DefaultMiniAudio(options).renderCompose(modifier)
}

@Composable
fun MiniCard(options: MiniCardOptions, modifier: Modifier = Modifier) {
    DefaultMiniCard(options).renderCompose(modifier)
}

@Composable
fun CardVideoDisplay(options: CardVideoDisplayOptions) {
    DefaultCardVideoDisplay(options).renderCompose()
}

@Composable
fun FlexibleGrid(options: FlexibleGridOptions) {
    DefaultFlexibleGrid(options).renderCompose()
}

@Composable
fun FlexibleVideo(options: FlexibleVideoOptions) {
    DefaultFlexibleVideo(options).renderCompose()
}

@Composable
fun Pagination(options: PaginationOptions) {
    DefaultPagination(options).renderCompose()
}

@Composable
fun AlertComponent(options: AlertComponentOptions) {
    DefaultAlertComponent(options).renderCompose()
}

@Composable
fun ControlButtonsAltComponent(options: ControlButtonsAltComponentOptions) {
    DefaultControlButtonsAltComponent(options).renderCompose()
}

@Composable
fun ControlButtonsComponentTouch(options: ControlButtonsComponentTouchOptions) {
    DefaultControlButtonsComponentTouch(options).renderCompose()
}

@Composable
fun AudioDecibelCheck(options: AudioDecibelCheckOptions) {
    DefaultAudioDecibelCheck(options).renderCompose()
}

// ============ Layout Components ============

@Composable
fun MainAspectComponent(
    options: MainAspectComponentOptions,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    DefaultMainAspectComponent(options).renderCompose(content)
}

@Composable
fun MainContainerComponent(
    options: MainContainerComponentOptions,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    DefaultMainContainerComponent(options).renderCompose(content)
}

@Composable
fun MainScreenComponent(
    options: MainScreenComponentOptions,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    DefaultMainScreenComponent(options).renderCompose(content)
}

@Composable
fun MainGridComponent(
    options: MainGridComponentOptions,
    renderTimer: Boolean = false,
    timer: @Composable (BoxScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    DefaultMainGridComponent(options).renderCompose(renderTimer, timer, content)
}

@Composable
fun OtherGridComponent(
    options: OtherGridComponentOptions,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    DefaultOtherGridComponent(options).renderCompose(content)
}

@Composable
fun SubAspectComponent(
    options: SubAspectComponentOptions,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    DefaultSubAspectComponent(options).renderCompose(content)
}
