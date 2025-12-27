package com.mediasfu.sdk.background

import com.mediasfu.sdk.model.VirtualBackground
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.webrtc.MediaStreamTrack
import com.mediasfu.sdk.webrtc.WebRtcDevice

/**
 * Interface for virtual background processing.
 * 
 * Platform implementations use ML Kit Selfie Segmentation to:
 * 1. Capture video frames from the camera
 * 2. Segment the person from the background
 * 3. Composite the person with a virtual background
 * 4. Output processed frames to a new video track
 */
interface VirtualBackgroundProcessor {
    
    /**
     * Whether the processor is currently active
     */
    val isProcessing: Boolean
    
    /**
     * The currently applied virtual background
     */
    val currentBackground: VirtualBackground?
    
    /**
     * Start processing the input video stream with the given background.
     * 
     * @param inputStream The camera video stream to process
     * @param background The virtual background to apply
     * @param onProcessedFrame Optional callback for each processed frame (for preview)
     * @return The processed video stream with virtual background applied
     */
    suspend fun startProcessing(
        inputStream: MediaStream,
        background: VirtualBackground,
        onProcessedFrame: ((ProcessedFrame) -> Unit)? = null
    ): MediaStream?
    
    /**
     * Start processing with a WebRtcDevice to create output stream.
     * This method creates a virtual video source that can be used with mediasoup
     * to send processed frames to remote participants.
     * 
     * @param inputStream The camera video stream to process
     * @param background The virtual background to apply
     * @param device The WebRtcDevice to create virtual video source
     * @param onProcessedFrame Optional callback for each processed frame (for preview)
     * @return The processed video stream with virtual background applied
     */
    suspend fun startProcessingWithDevice(
        inputStream: MediaStream,
        background: VirtualBackground,
        device: WebRtcDevice,
        onProcessedFrame: ((ProcessedFrame) -> Unit)? = null
    ): MediaStream? = startProcessing(inputStream, background, onProcessedFrame)
    
    /**
     * Update the virtual background while processing continues.
     * 
     * @param background The new background to apply
     */
    suspend fun updateBackground(background: VirtualBackground)
    
    /**
     * Stop processing and release resources.
     * 
     * @return The original unprocessed stream
     */
    suspend fun stopProcessing(): MediaStream?
    
    /**
     * Update the frame callback for preview while processing continues.
     * This allows registering a preview callback when the processor is already running.
     * 
     * @param onProcessedFrame Callback for each processed frame
     */
    fun setFrameCallback(onProcessedFrame: ((ProcessedFrame) -> Unit)?)
    
    /**
     * Release all resources. Call when done with the processor.
     */
    fun release()
}

/**
 * Represents a processed video frame
 */
data class ProcessedFrame(
    val width: Int,
    val height: Int,
    val timestamp: Long,
    /** Platform-specific bitmap/image data */
    val imageData: Any?
)

/**
 * Factory for creating platform-specific VirtualBackgroundProcessor instances.
 */
expect object VirtualBackgroundProcessorFactory {
    /**
     * Create a new VirtualBackgroundProcessor instance.
     * 
     * @param context Platform-specific context (Android Context, etc.)
     * @return A new processor instance, or null if not supported on this platform
     */
    fun create(context: Any?): VirtualBackgroundProcessor?
    
    /**
     * Check if virtual background processing is supported on this platform.
     */
    fun isSupported(): Boolean
}
