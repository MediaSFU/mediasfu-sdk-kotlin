package com.mediasfu.sdk.ui.components.background
import com.mediasfu.sdk.util.Logger

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import coil3.ImageLoader
import coil3.PlatformContext
import com.mediasfu.sdk.model.BackgroundType
import com.mediasfu.sdk.model.PresetBackgrounds
import com.mediasfu.sdk.model.VirtualBackground
import com.mediasfu.sdk.ui.components.whiteboard.decodeImageBitmap
import com.mediasfu.sdk.ui.components.whiteboard.rememberImagePickerLauncher
import com.mediasfu.sdk.ui.components.display.PlatformVideoRenderer
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.webrtc.MediaStreamTrack
import com.mediasfu.sdk.webrtc.WebRtcDevice
import kotlinx.datetime.Clock
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Parameters for the BackgroundModal widget.
 */
interface BackgroundModalParameters {
    val showAlert: ((message: String, type: String, duration: Long) -> Unit)?
    val selectedBackground: VirtualBackground?
    val targetResolution: String
    
    /** The current local video stream from the main app for preview */
    val localStreamVideo: com.mediasfu.sdk.webrtc.MediaStream?
    
    /** The virtual background processor for ML Kit segmentation */
    val backgroundProcessor: com.mediasfu.sdk.background.VirtualBackgroundProcessor?

    /** Whether the video is already on (camera already streaming) */
    val videoAlreadyOn: Boolean
        get() = false
    
    /** WebRTC device for camera initialization when video is off */
    val device: WebRtcDevice?
        get() = null
    
    /** Video constraints for camera initialization */
    val vidCons: Map<String, Any?>?
        get() = null
    
    /** Target frame rate for camera preview */
    val frameRate: Int
        get() = 15

    val updateSelectedBackground: (VirtualBackground?) -> Unit
    val updateIsBackgroundModalVisible: (Boolean) -> Unit
    val updateKeepBackground: (Boolean) -> Unit
    val updateBackgroundHasChanged: (Boolean) -> Unit
    
    /** Callback to store the created processor for use in Apply */
    val updateBackgroundProcessor: ((com.mediasfu.sdk.background.VirtualBackgroundProcessor?) -> Unit)?
        get() = null

    /** Callback when background is applied */
    val onBackgroundApply: (suspend (VirtualBackground) -> Unit)?

    /** Callback when background is previewed */
    val onBackgroundPreview: (suspend (VirtualBackground) -> Unit)?

    fun getUpdatedAllParams(): BackgroundModalParameters
}

/**
 * Options for the BackgroundModal widget.
 */
data class BackgroundModalOptions(
    val isVisible: Boolean,
    val onClose: () -> Unit,
    val parameters: BackgroundModalParameters,
    val backgroundColor: Color = Color(0xFFF5F5F5),
    val position: String = "center",
    val customBackgrounds: List<VirtualBackground>? = null,
    val allowCustomUpload: Boolean = true,
    val showColorPicker: Boolean = true,
    val showPreview: Boolean = true
)

internal fun shouldProcessBackgroundPreview(
    hasEffectiveVideoStream: Boolean,
    selectedBackground: VirtualBackground?,
    isPlatformSupported: Boolean
): Boolean {
    return hasEffectiveVideoStream &&
        isPlatformSupported &&
        selectedBackground != null &&
        selectedBackground.type != BackgroundType.NONE
}

/**
 * BackgroundModal - Modal for selecting virtual backgrounds.
 *
 * This component provides an interface for selecting and applying virtual
 * backgrounds during video calls, matching the React MediaSFU implementation.
 *
 * Features:
 * - Default preset images from MediaSFU (wall, shelf, clock, desert, flower)
 * - Preset blur backgrounds with adjustable intensity
 * - Preset color backgrounds
 * - Custom image upload
 * - Live camera preview with background applied
 * - Platform compatibility checking
 *
 * Platform Support:
 * - ✅ Android: Full support via ML Kit Selfie Segmentation
 * - ⚠️ iOS: Camera preview works, but virtual background processing depends on native segmentation support
 * - ❌ Web: Not supported (no ML Kit)
 */
@Composable
fun BackgroundModal(
    options: BackgroundModalOptions,
    modifier: Modifier = Modifier
) {
    if (!options.isVisible) return

    // Set up Coil ImageLoader with network support (OkHttp is automatically used)
    val platformContext = LocalPlatformContext.current
    val imageLoader = remember(platformContext) {
        ImageLoader.Builder(platformContext)
            .crossfade(true)
            .build()
    }

    val params = options.parameters
    val modalScope = rememberCoroutineScope()
    
    // Initialize selectedBackground from params.selectedBackground when modal opens
    // This ensures the previously applied background is auto-selected when reopening
    var selectedBackground by remember(params.selectedBackground) { 
        mutableStateOf(params.selectedBackground) 
    }
    
    var isProcessing by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var customBackgrounds by remember(options.customBackgrounds, params.selectedBackground) {
        mutableStateOf(seedCustomBackgrounds(options.customBackgrounds, params.selectedBackground))
    }

    val launchImagePicker = rememberImagePickerLauncher { result ->
        if (result == null) return@rememberImagePickerLauncher

        val uploadedBackground = createCustomBackgroundFromPickerResult(result)
        customBackgrounds = (customBackgrounds + uploadedBackground)
            .distinctBy { it.id }
        selectedBackground = uploadedBackground

        params.showAlert?.invoke(
            "Custom background added",
            "success",
            2000
        )
    }

    val isPlatformSupported = com.mediasfu.sdk.background.VirtualBackgroundProcessorFactory.isSupported()

    // Tab titles
    val tabs = buildList {
        add("Presets")
        add("Blur")
        if (options.showColorPicker) add("Colors")
        add("Custom")
    }

    fun selectBackground(background: VirtualBackground) {
        selectedBackground = background
    }

    suspend fun applyBackground() {
        val bg = selectedBackground ?: run {
            return
        }

        if (bg.type != BackgroundType.NONE && !isPlatformSupported) {
            params.showAlert?.invoke(
                "Virtual background processing is not available on this platform build yet.",
                "danger",
                3000
            )
            return
        }

        isProcessing = true

        try {
            if (bg.type != BackgroundType.NONE && params.backgroundProcessor == null) {
                params.updateBackgroundProcessor?.invoke(
                    com.mediasfu.sdk.background.VirtualBackgroundProcessorFactory.create(platformContext)
                )
            }

            val backgroundEnabled = bg.type != BackgroundType.NONE

            params.updateSelectedBackground(bg)
            params.updateBackgroundHasChanged(true)
            params.updateKeepBackground(backgroundEnabled)

            if (!params.videoAlreadyOn) {
                params.showAlert?.invoke(
                    if (backgroundEnabled) {
                        "Background saved. It will be applied when you turn on your camera."
                    } else {
                        "Background cleared."
                    },
                    "success",
                    3000
                )
                options.onClose()
                return
            }

            // Wrap the apply with a timeout so that a slow/stuck produce() call
            // on the native bridge never freezes the spinner indefinitely.
            val applied = kotlinx.coroutines.withTimeoutOrNull(8_000L) {
                params.onBackgroundApply?.invoke(bg)
            }

            if (applied == null) {
                params.showAlert?.invoke(
                    "Background saved – still applying in background",
                    "warning",
                    3000
                )
            } else {
                params.showAlert?.invoke(
                    "Background applied successfully",
                    "success",
                    2000
                )
            }

            options.onClose()
        } catch (e: Exception) {
            Logger.e("BackgroundModal", "MediaSFU - BackgroundModal: Error applying background: ${e.message}")
            e.printStackTrace()
            params.showAlert?.invoke(
                "Failed to apply background: ${e.message}",
                "danger",
                3000
            )
        } finally {
            isProcessing = false
        }
    }

    /**
     * Save the selected background for later use without applying it now.
     * This stores the selection so it can be automatically applied when video is turned on.
     * Matches the React implementation's "Save for Later" functionality.
     */
    fun saveBackgroundForLater() {
        val bg = selectedBackground ?: run {
            params.showAlert?.invoke(
                "Please select a background first",
                "danger",
                2000
            )
            return
        }
        
        
        // CRITICAL: Stop any running processor to prevent crash when camera turns on later
        // The processor may have an old stream reference that will be disposed when we close modal
        // When camera turns on later, the background will be reapplied with the new stream
        val existingProcessor = params.backgroundProcessor
        if (existingProcessor != null && existingProcessor.isProcessing) {
            modalScope.launch {
                try {
                    existingProcessor.stopProcessing()
                } catch (e: Exception) {
                    Logger.e("BackgroundModal", "MediaSFU - BackgroundModal: Error stopping processor: ${e.message}")
                }
            }
        }
        
        // Update the selected background state without applying to the room
        // producer. This confirms the user's saved-for-later choice so
        // streamSuccessVideo can auto-apply it when video is produced later.
        if (bg.type != BackgroundType.NONE && params.backgroundProcessor == null) {
            params.updateBackgroundProcessor?.invoke(
                com.mediasfu.sdk.background.VirtualBackgroundProcessorFactory.create(platformContext)
            )
        }

        params.updateSelectedBackground(bg)
        params.updateBackgroundHasChanged(bg.type != BackgroundType.NONE)
        
        // Mark that background has been selected (but not applied yet)
        if (bg.type != BackgroundType.NONE) {
            params.updateKeepBackground(true)
        } else {
            params.updateKeepBackground(false)
        }
        
        params.showAlert?.invoke(
            "Background saved. It will be applied when you turn on your camera.",
            "success",
            3000
        )
        
        options.onClose()
    }

    Dialog(
        onDismissRequest = options.onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.75f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = options.backgroundColor)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Virtual Background",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = options.onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Platform warning (if not supported)
                if (!isPlatformSupported) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFE65100)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Virtual background processing is not available on this platform build yet. You can still save a background for later.",
                                color = Color(0xFFE65100),
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // Video Preview Area
                if (options.showPreview) {
                    VideoPreviewSection(
                        localStreamVideo = params.localStreamVideo,
                        selectedBackground = selectedBackground,
                        backgroundProcessor = params.backgroundProcessor,
                        onProcessorCreated = params.updateBackgroundProcessor,
                        videoAlreadyOn = params.videoAlreadyOn,
                        device = params.device,
                        vidCons = params.vidCons,
                        frameRate = params.frameRate
                    )
                }

                HorizontalDivider()

                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) },
                            icon = {
                                Icon(
                                    when (index) {
                                        0 -> Icons.Default.Image
                                        1 -> Icons.Default.BlurOn
                                        2 -> if (options.showColorPicker) Icons.Default.Palette else Icons.Default.Add
                                        else -> Icons.Default.Add
                                    },
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }

                // Tab content
                // Clicking a background selects it for local preview only.
                // The VideoPreviewSection reacts to selectedBackground and shows the effect.
                // The actual room-level apply happens only when the user taps "Apply".
                val onClickBackground: (VirtualBackground) -> Unit = { bg ->
                    selectBackground(bg)
                }
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> PresetImagesTab(
                            selectedBackground = selectedBackground,
                            onSelectBackground = onClickBackground,
                            imageLoader = imageLoader
                        )
                        1 -> BlurTab(
                            selectedBackground = selectedBackground,
                            onSelectBackground = onClickBackground
                        )
                        2 -> if (options.showColorPicker) {
                            ColorsTab(
                                selectedBackground = selectedBackground,
                                onSelectBackground = onClickBackground
                            )
                        } else {
                            CustomImagesTab(
                                customBackgrounds = customBackgrounds,
                                selectedBackground = selectedBackground,
                                onSelectBackground = onClickBackground,
                                allowCustomUpload = options.allowCustomUpload,
                                onUploadImage = launchImagePicker
                            )
                        }
                        3 -> CustomImagesTab(
                            customBackgrounds = customBackgrounds,
                            selectedBackground = selectedBackground,
                            onSelectBackground = onClickBackground,
                            allowCustomUpload = options.allowCustomUpload,
                            onUploadImage = launchImagePicker
                        )
                    }
                }

                HorizontalDivider()

                // Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Selected background indicator
                    Text(
                        text = "Selected: ${selectedBackground?.name ?: "None"}",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )

                    Row {
                        TextButton(onClick = options.onClose) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // "Save for Later" button - only shown when video is off
                        // This allows users to select a background that will be applied
                        // automatically when they turn on their camera
                        if (!params.videoAlreadyOn) {
                            OutlinedButton(
                                onClick = { saveBackgroundForLater() },
                                enabled = selectedBackground != null && 
                                         selectedBackground?.type != BackgroundType.NONE
                            ) {
                                Text("Save for Later")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        
                        val scope = rememberCoroutineScope()
                        Button(
                            onClick = {
                                scope.launch {
                                    applyBackground()
                                }
                            },
                            enabled = !isProcessing &&
                                selectedBackground != null &&
                                (selectedBackground?.type == BackgroundType.NONE || isPlatformSupported)
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text("Apply")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetImagesTab(
    selectedBackground: VirtualBackground?,
    onSelectBackground: (VirtualBackground) -> Unit,
    imageLoader: ImageLoader
) {
    val presetImages = remember { PresetBackgrounds.images }
    val allBackgrounds = remember { listOf(VirtualBackground.none()) + presetImages }
    
    // Debug logging
    LaunchedEffect(Unit) {
        allBackgrounds.forEach { bg ->
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(allBackgrounds) { bg ->
            BackgroundCard(
                background = bg,
                isSelected = selectedBackground?.id == bg.id || (selectedBackground == null && bg.type == BackgroundType.NONE),
                onClick = { onSelectBackground(bg) }
            ) {
                if (bg.type == BackgroundType.NONE) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFF0F0F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "None",
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    // Load network image using Coil
                    val imageUrl = bg.thumbnailUrl ?: bg.imageUrl ?: ""
                    if (imageUrl.isNotEmpty()) {
                        var isLoading by remember { mutableStateOf(true) }
                        var isError by remember { mutableStateOf(false) }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = imageUrl,
                                contentDescription = bg.name,
                                imageLoader = imageLoader,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                onState = { state ->
                                    isLoading = state is AsyncImagePainter.State.Loading
                                    isError = state is AsyncImagePainter.State.Error
                                    if (state is AsyncImagePainter.State.Error) {
                                        Logger.e("BackgroundModal", "MediaSFU - BackgroundModal: Image load error for $imageUrl: ${state.result.throwable}")
                                    }
                                }
                            )
                            
                            // Loading indicator
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                            
                            // Error fallback
                            if (isError) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.BrokenImage,
                                        contentDescription = null,
                                        tint = Color.Gray
                                    )
                                    Text(
                                        text = bg.name,
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    } else {
                        // Fallback for images without URL
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFE0E0E0)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Image,
                                    contentDescription = null,
                                    tint = Color.Gray
                                )
                                Text(
                                    text = bg.name,
                                    fontSize = 10.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlurTab(
    selectedBackground: VirtualBackground?,
    onSelectBackground: (VirtualBackground) -> Unit
) {
    val blurOptions = remember { PresetBackgrounds.blurs }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Select blur intensity",
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(blurOptions) { blur ->
                BackgroundCard(
                    background = blur,
                    isSelected = selectedBackground?.id == blur.id,
                    onClick = { onSelectBackground(blur) }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Color.Gray.copy(alpha = blur.blurIntensity)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.BlurOn,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Text(
                                text = blur.name,
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorsTab(
    selectedBackground: VirtualBackground?,
    onSelectBackground: (VirtualBackground) -> Unit
) {
    val colorOptions = remember { PresetBackgrounds.colors }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "Select background color",
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(colorOptions) { colorBg ->
                BackgroundCard(
                    background = colorBg,
                    isSelected = selectedBackground?.id == colorBg.id,
                    onClick = { onSelectBackground(colorBg) },
                    aspectRatio = 1f
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colorBg.color ?: Color.Gray)
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomImagesTab(
    customBackgrounds: List<VirtualBackground>,
    selectedBackground: VirtualBackground?,
    onSelectBackground: (VirtualBackground) -> Unit,
    allowCustomUpload: Boolean,
    onUploadImage: () -> Unit
) {
    Column(modifier = Modifier.padding(16.dp)) {
        if (allowCustomUpload) {
            OutlinedButton(
                onClick = onUploadImage,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Upload Custom Image")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (customBackgrounds.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No custom backgrounds",
                        color = Color.Gray
                    )
                    Text(
                        text = "Upload an image to get started",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(customBackgrounds) { bg ->
                    BackgroundCard(
                        background = bg,
                        isSelected = selectedBackground?.id == bg.id,
                        onClick = { onSelectBackground(bg) }
                    ) {
                        CustomBackgroundThumbnail(background = bg)
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomBackgroundThumbnail(background: VirtualBackground) {
    val imageBitmap = remember(background.id, background.imageBytes) {
        background.imageBytes?.let(::decodeImageBitmap)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE0E0E0)),
        contentAlignment = Alignment.Center
    ) {
        when {
            imageBitmap != null -> {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = background.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            !background.imageUrl.isNullOrBlank() || !background.thumbnailUrl.isNullOrBlank() -> {
                AsyncImage(
                    model = background.thumbnailUrl ?: background.imageUrl,
                    contentDescription = background.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            else -> {
                Text(background.name, fontSize = 10.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

private fun seedCustomBackgrounds(
    supplied: List<VirtualBackground>?,
    selectedBackground: VirtualBackground?
): List<VirtualBackground> {
    val seeded = mutableListOf<VirtualBackground>()
    supplied?.let { seeded += it }
    if (selectedBackground != null &&
        selectedBackground.type == BackgroundType.IMAGE &&
        !selectedBackground.isPreset &&
        selectedBackground.imageBytes != null
    ) {
        seeded += selectedBackground
    }
    return seeded.distinctBy { it.id }
}

private fun createCustomBackgroundFromPickerResult(
    result: com.mediasfu.sdk.ui.components.whiteboard.WhiteboardImageResult
): VirtualBackground {
    val source = result.imageSrc?.substringAfterLast('/')?.substringAfterLast('\\')
    val displayName = source
        ?.substringBeforeLast('.')
        ?.takeIf { it.isNotBlank() }
        ?.replace('_', ' ')
        ?.replace('-', ' ')
        ?.replaceFirstChar { it.uppercase() }
        ?: "Custom Image"

    val uniqueId = "custom_${Clock.System.now().toEpochMilliseconds()}_${displayName.hashCode()}"

    return VirtualBackground.image(
        id = uniqueId,
        name = displayName,
        imageUrl = result.imageSrc,
        imageBytes = result.imageData,
        thumbnailUrl = result.imageSrc,
        isPreset = false
    )
}

@Composable
private fun BackgroundCard(
    background: VirtualBackground,
    isSelected: Boolean,
    onClick: () -> Unit,
    aspectRatio: Float = 1.2f,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Box {
            content()

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * Video preview section showing live camera feed with virtual background processing.
 * 
 * This component handles PREVIEW ONLY - showing the user what the background will look like.
 * The actual production flow (sending to remote participants) is handled by onBackgroundApply.
 * 
 * Flow per React/Flutter:
 * 1. Preview mode: Clone the localStreamVideo and process it separately for preview
 * 2. When background changes: Update the processor's background in real-time
 * 3. Apply: Start producing the processed stream to others
 * 4. Remove background: Restore original stream to producer
 * 
 * Camera initialization (when video is off):
 * If videoAlreadyOn is false and device is provided, we initialize a temporary camera
 * stream just for preview. This stream is stopped when the modal closes.
 */
@Composable
private fun VideoPreviewSection(
    localStreamVideo: com.mediasfu.sdk.webrtc.MediaStream?,
    selectedBackground: VirtualBackground?,
    backgroundProcessor: com.mediasfu.sdk.background.VirtualBackgroundProcessor?,
    onProcessorCreated: ((com.mediasfu.sdk.background.VirtualBackgroundProcessor?) -> Unit)? = null,
    videoAlreadyOn: Boolean = false,
    device: WebRtcDevice? = null,
    vidCons: Map<String, Any?>? = null,
    frameRate: Int = 15
) {
    // State for camera initialization when video is not already on
    var previewStream by remember { mutableStateOf<MediaStream?>(null) }
    var isInitializingCamera by remember { mutableStateOf(false) }
    var cameraInitError by remember { mutableStateOf<String?>(null) }
    val initScope = rememberCoroutineScope()
    
    val localVideoTrack = remember(localStreamVideo) {
        localStreamVideo?.getVideoTracks()?.firstOrNull()
    }
    val hasActiveRoomVideoTrack = localVideoTrack?.enabled == true && localStreamVideo?.active == true
    val shouldUseRoomStream = hasActiveRoomVideoTrack || (videoAlreadyOn && localVideoTrack != null)

    // Prefer the actual room stream whenever it exists. Do not rely solely on
    // videoAlreadyOn here: if that flag is stale false, starting a temporary
    // iOS camera preview would replace the active room capturer and freeze the
    // transmitted/video-card media.
    val effectiveStream = remember(localStreamVideo, previewStream, shouldUseRoomStream) {
        if (shouldUseRoomStream) localStreamVideo else previewStream
    }
    
    // Initialize camera when video is not already on
    LaunchedEffect(videoAlreadyOn, shouldUseRoomStream, device) {
        if (!shouldUseRoomStream && device != null && previewStream == null && !isInitializingCamera) {
            isInitializingCamera = true
            cameraInitError = null
            
            try {
                // Build video constraints based on vidCons or defaults
                val videoConstraints = buildMap<String, Any?> {
                    put("facingMode", "user")
                    put("frameRate", frameRate)
                    
                    // Apply custom constraints if provided
                    vidCons?.forEach { (key, value) ->
                        put(key, value)
                    }
                }
                
                val constraints = mapOf(
                    "audio" to false,
                    "video" to videoConstraints
                )
                
                val stream = device.getUserMedia(constraints)
                previewStream = stream
            } catch (e: Exception) {
                Logger.e("BackgroundModal", "MediaSFU - VideoPreviewSection: Failed to initialize preview camera: ${e.message}")
                cameraInitError = e.message ?: "Failed to initialize camera"
            } finally {
                isInitializingCamera = false
            }
        }
    }
    
    // Cleanup preview stream when modal closes (component disposes)
    DisposableEffect(Unit) {
        onDispose {
            previewStream?.let { stream ->
                try {
                    stream.getVideoTracks().forEach { track ->
                        track.stop()
                    }
                } catch (e: Exception) {
                    Logger.e("BackgroundModal", "MediaSFU - VideoPreviewSection: Error stopping preview tracks: ${e.message}")
                }
            }
        }
    }
    
    val videoTrack = remember(effectiveStream) {
        effectiveStream?.getVideoTracks()?.firstOrNull()
    }
    
    // Get platform context for processor creation
    val platformContext = LocalPlatformContext.current
    
    // Create/reuse processor for PREVIEW only. Do not publish it to the
    // production apply path: active-room VB and saved-for-later VB have their
    // own lifecycle, while this processor is disposable modal UI state.
    val previewProcessor = remember(platformContext) {
        com.mediasfu.sdk.background.VirtualBackgroundProcessorFactory.create(platformContext)
    }
    
    // Track if processor is active
    val isProcessorSupported = com.mediasfu.sdk.background.VirtualBackgroundProcessorFactory.isSupported()
    val hasActiveBackground = selectedBackground != null && selectedBackground.type != BackgroundType.NONE

    // Processed frame preview. This is intentionally preview-only: Apply uses the
    // platform processor lifecycle, while selecting thumbnails must not modify the
    // live producer track before the user confirms.
    var processedBitmap by remember { mutableStateOf<Any?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    // Track the last background we started processing with
    var lastProcessedBackgroundId by remember { mutableStateOf<String?>(null) }
    val shouldProcessPreview = shouldProcessBackgroundPreview(
        hasEffectiveVideoStream = effectiveStream != null,
        selectedBackground = selectedBackground,
        isPlatformSupported = isProcessorSupported
    )

    // Start/update processing for preview when background or stream changes
    LaunchedEffect(selectedBackground, effectiveStream, previewProcessor, shouldProcessPreview) {
        val processor = previewProcessor
        if (!shouldProcessPreview || processor == null || effectiveStream == null) {
            if (processor?.isProcessing == true) {
                try {
                    processor.stopProcessing()
                } catch (e: Exception) {
                    Logger.e("BackgroundModal", "MediaSFU - VideoPreviewSection: Error stopping processor: ${e.message}")
                }
            }
            processedBitmap = null
            isProcessing = false
            return@LaunchedEffect
        }

        val backgroundToProcess = selectedBackground
        if (backgroundToProcess == null || backgroundToProcess.type == BackgroundType.NONE) {
            processedBitmap = null
            isProcessing = false
            return@LaunchedEffect
        }

        val previewCallback: (com.mediasfu.sdk.background.ProcessedFrame) -> Unit = { frame ->
            processedBitmap = frame.imageData
            isProcessing = false
        }

        if (hasActiveBackground) {
            if (!processor.isProcessing) {
                // Preview processing only: attach a temporary frame renderer and
                // update this modal from processed frame snapshots.
                isProcessing = true
                lastProcessedBackgroundId = backgroundToProcess.id
                try {
                    processor.startProcessing(
                        inputStream = effectiveStream,
                        background = backgroundToProcess,
                        onProcessedFrame = previewCallback
                    )
                } catch (e: Exception) {
                    Logger.e("BackgroundModal", "MediaSFU - VideoPreviewSection: Error starting processor: ${e.message}")
                    isProcessing = false
                }
            } else if (lastProcessedBackgroundId != backgroundToProcess.id) {
                // Processor already running – just swap the background in real-time.
                lastProcessedBackgroundId = backgroundToProcess.id
                try {
                    processor.updateBackground(backgroundToProcess)
                    processor.setFrameCallback(previewCallback)
                } catch (e: Exception) {
                    Logger.e("BackgroundModal", "MediaSFU - VideoPreviewSection: Error updating background: ${e.message}")
                }
            } else {
                processor.setFrameCallback(previewCallback)
            }
        } else {
            // NONE selected – stop processor and fall back to raw video
            if (processor.isProcessing) {
                try {
                    processor.stopProcessing()
                } catch (e: Exception) {
                    Logger.e("BackgroundModal", "MediaSFU - VideoPreviewSection: Error stopping processor: ${e.message}")
                }
            }
            processedBitmap = null
            isProcessing = false
            lastProcessedBackgroundId = null
        }
    }
    
    val currentVideoAlreadyOn by rememberUpdatedState(videoAlreadyOn)
    val currentSelectedBackground by rememberUpdatedState(selectedBackground)

    // Clean up on dispose - stop preview processing
    DisposableEffect(previewProcessor) {
        onDispose {
            val isVideoOn = currentVideoAlreadyOn
            val bg = currentSelectedBackground
            val hasVB = bg != null && bg.type != com.mediasfu.sdk.model.BackgroundType.NONE
            if (!(isVideoOn && hasVB)) {
                if (previewProcessor?.isProcessing == true) {
                    try {
                        runBlocking {
                            previewProcessor.stopProcessing()
                        }
                    } catch (e: Exception) {
                        Logger.e("BackgroundModal", "MediaSFU - VideoPreviewSection: Error stopping processor on dispose: ${e.message}")
                    }
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(160.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when {
                isInitializingCamera -> {
                    // Camera is being initialized
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp),
                            color = Color.White,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Initializing camera for preview...",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                }
                cameraInitError != null -> {
                    // Camera initialization failed
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = Color(0xFFE57373),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Camera initialization failed",
                            color = Color(0xFFE57373),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = cameraInitError ?: "",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
                hasActiveBackground && processedBitmap != null -> {
                    ProcessedFrameView(
                        bitmap = processedBitmap,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                videoTrack != null -> {
                    // Raw camera feed (no background active or processor not ready yet)
                    PlatformVideoRenderer(
                        track = videoTrack,
                        doMirror = true,
                        forceFullDisplay = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                !videoAlreadyOn && device != null -> {
                    // Camera is off but we have a device - show helpful message about Save for Later
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Videocam,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Your camera is currently off",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Select a background and tap 'Save for Later'",
                            color = Color.Gray.copy(alpha = 0.8f),
                            fontSize = 12.sp
                        )
                        Text(
                            text = "It will be applied when you turn on your camera",
                            color = Color.Gray.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "🔒 Preview only - nothing is being sent",
                            color = Color(0xFF4CAF50).copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                }
                else -> {
                    // No video available and can't initialize
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.VideocamOff,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Camera not available",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Turn on your camera to preview backgrounds",
                            color = Color.Gray.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Label showing selected background
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasActiveBackground) {
                        Icon(
                            when (selectedBackground?.type) {
                                BackgroundType.IMAGE -> Icons.Default.Image
                                BackgroundType.BLUR -> Icons.Default.BlurOn
                                BackgroundType.COLOR -> Icons.Default.Palette
                                BackgroundType.VIDEO -> Icons.Default.Videocam
                                else -> Icons.Default.Block
                            },
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = selectedBackground?.name ?: "No background",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }

            // Processing indicator
            if (hasActiveBackground && isProcessing && processedBitmap == null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(
                            Color.Black.copy(alpha = 0.6f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                }
            }

            // Status indicator
            if (hasActiveBackground) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp)
                        .background(
                            if (isProcessorSupported) Color(0xFF4CAF50).copy(alpha = 0.9f) 
                            else Color(0xFFFFA000).copy(alpha = 0.9f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (processedBitmap != null) 
                            "Background preview active"
                        else if (isProcessorSupported) 
                            "Processing background..."
                        else 
                            "Background processing unavailable",
                        color = Color.White,
                        fontSize = 10.sp
                    )
                }
            }
            
            // Show "Preview only" message when camera is off (using temporary preview)
            if (!videoAlreadyOn) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp)
                        .background(
                            Color(0xFF4CAF50).copy(alpha = 0.85f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "🔒 Preview only - nothing is being sent",
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/**
 * Platform-specific composable to render a processed bitmap frame.
 * On Android, this renders an android.graphics.Bitmap.
 * On iOS, this renders a UIImage (not implemented yet).
 */
@Composable
expect fun ProcessedFrameView(
    bitmap: Any?,
    modifier: Modifier = Modifier
)
