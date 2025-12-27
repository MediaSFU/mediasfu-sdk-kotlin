package com.mediasfu.sdk.methods.whiteboard_methods
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.consumers.ConnectSendTransportScreenParameters
import com.mediasfu.sdk.model.WhiteboardShape
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.webrtc.WebRtcDevice
import com.mediasfu.sdk.webrtc.WebRtcProducer
import com.mediasfu.sdk.consumers.CreateSendTransportOptions as ConsumerCreateSendTransportOptions
import com.mediasfu.sdk.consumers.ConnectSendTransportScreenOptions as ConsumerConnectSendTransportScreenOptions

/**
 * Parameters for capturing the canvas stream.
 * Extends ConnectSendTransportScreenParameters to enable proper WebRTC transport handling.
 */
interface CaptureCanvasStreamParameters : ConnectSendTransportScreenParameters {
    val canvasStream: MediaStream? // MediaStream for whiteboard capture
    val updateCanvasStream: (MediaStream?) -> Unit
    val shapes: List<WhiteboardShape>
    val useImageBackground: Boolean
    val webRtcDevice: WebRtcDevice?

    override fun getUpdatedAllParams(): CaptureCanvasStreamParameters
}

/**
 * Simplified parameters for stopping the canvas stream.
 * Used when we only need to stop the stream and disconnect transport.
 */
interface StopCanvasStreamParameters {
    val canvasStream: MediaStream?
    val updateCanvasStream: (MediaStream?) -> Unit
    val screenProducer: WebRtcProducer?
    val socket: com.mediasfu.sdk.socket.SocketManager?
    val localScreenProducer: WebRtcProducer?
    val localSocket: com.mediasfu.sdk.socket.SocketManager?
    fun updateScreenProducer(producer: WebRtcProducer?)
    fun updateLocalScreenProducer(producer: WebRtcProducer?)
}

/**
 * Options for stopping the canvas stream.
 */
data class StopCanvasStreamOptions(
    val parameters: StopCanvasStreamParameters
)

/**
 * Stops the canvas stream and disconnects the transport.
 * This is a simplified version that doesn't require the full CaptureCanvasStreamParameters.
 */
suspend fun stopCanvasStream(options: StopCanvasStreamOptions) {
    val params = options.parameters
    val canvasStream = params.canvasStream
    
    if (canvasStream != null) {
        try {
            
            // Stop all tracks in the stream
            canvasStream.getTracks().forEach { track ->
                track.stop()
            }
            
            params.updateCanvasStream(null)

            // Disconnect the transport
            val disconnectParams = object : com.mediasfu.sdk.consumers.DisconnectSendTransportScreenParameters {
                override val screenProducer: WebRtcProducer? get() = params.screenProducer
                override val socket: com.mediasfu.sdk.socket.SocketManager? get() = params.socket
                override val localScreenProducer: WebRtcProducer? get() = params.localScreenProducer
                override val localSocket: com.mediasfu.sdk.socket.SocketManager? get() = params.localSocket
                override val roomName: String get() = "whiteboard"
                override fun updateScreenProducer(producer: WebRtcProducer?) {
                    params.updateScreenProducer(producer)
                }
                override fun updateLocalScreenProducer(producer: WebRtcProducer?) {
                    params.updateLocalScreenProducer(producer)
                }
                override fun getUpdatedAllParams() = this
            }
            
            com.mediasfu.sdk.consumers.disconnectSendTransportScreen(
                com.mediasfu.sdk.consumers.DisconnectSendTransportScreenOptions(
                    parameters = disconnectParams
                )
            )

            Logger.e("CaptureCanvasStream", "StopCanvasStream: Canvas stream stopped and transport disconnected.")
        } catch (e: Exception) {
            Logger.e("CaptureCanvasStream", "StopCanvasStream: Error stopping canvas stream: ${e.message}")
        }
    }
}

/**
 * Options for capturing the canvas stream.
 */
data class CaptureCanvasStreamOptions(
    val parameters: CaptureCanvasStreamParameters,
    val start: Boolean = true
)

/**
 * Type definition for captureCanvasStream function.
 */
typealias CaptureCanvasStreamType = suspend (CaptureCanvasStreamOptions) -> Unit

/**
 * Captures the canvas stream and handles the transport connection for whiteboard streaming.
 *
 * This function manages the lifecycle of canvas stream capture for whiteboard recording:
 * - When [start] is `true`: Captures the whiteboard as a video stream and connects the transport
 * - When [start] is `false`: Stops the canvas stream tracks and disconnects the transport
 *
 * **Platform Support:**
 * - **Android**: Uses WhiteboardVideoCapturer to render shapes to video frames
 * - **iOS**: Uses Core Graphics to render shapes to video frames
 * - **Web**: Uses HTML Canvas's `captureStream()` API via JavaScript interop
 *
 * Example:
 * ```kotlin
 * val options = CaptureCanvasStreamOptions(
 *     parameters = myParameters,
 *     start = true
 * )
 * captureCanvasStream(options)
 * ```
 *
 * To stop the canvas stream:
 * ```kotlin
 * val options = CaptureCanvasStreamOptions(
 *     parameters = myParameters,
 *     start = false
 * )
 * captureCanvasStream(options)
 * ```
 */
suspend fun captureCanvasStream(options: CaptureCanvasStreamOptions) {
    try {
        val parameters = options.parameters
        val start = options.start

        // Get the latest parameters
        val params = parameters.getUpdatedAllParams()

        var canvasStream = params.canvasStream
        val updateCanvasStream = params.updateCanvasStream
        val webRtcDevice = params.webRtcDevice

        if (start && canvasStream == null) {
            // Start capturing the whiteboard as a video stream
            
            if (webRtcDevice != null) {
                try {
                    // Create shapes provider that returns current shapes
                    val shapesProvider: () -> List<Any> = { params.shapes }
                    val useImageBackgroundProvider: () -> Boolean = { params.useImageBackground }
                    
                    // Capture the whiteboard stream
                    val stream = webRtcDevice.captureWhiteboardStream(
                        shapesProvider = shapesProvider,
                        useImageBackgroundProvider = useImageBackgroundProvider,
                        width = 1280,
                        height = 720,
                        frameRate = 30
                    )
                    
                    if (stream != null) {
                        canvasStream = stream
                        updateCanvasStream(stream)
                        
                        // Update localStreamScreen with the canvas stream for transport connection
                        params.updateLocalStreamScreen(stream)
                        
                        // Connect to transport
                        if (!params.transportCreated) {
                            com.mediasfu.sdk.consumers.createSendTransport(
                                ConsumerCreateSendTransportOptions(
                                    option = "screen",
                                    parameters = params
                                )
                            )
                        } else {
                            // Close existing producer if any
                            try {
                                if (params.screenProducer != null) {
                                    // Producer will be closed by connectSendTransportScreen
                                    params.updateScreenProducer(null)
                                    kotlinx.coroutines.delay(500)
                                }
                            } catch (e: Exception) {
                                Logger.e("CaptureCanvasStream", "CaptureCanvasStream: Error closing existing producer: ${e.message}")
                            }
                            
                            // Connect the stream
                            com.mediasfu.sdk.consumers.connectSendTransportScreen(
                                ConsumerConnectSendTransportScreenOptions(
                                    stream = stream,
                                    parameters = params,
                                    targetOption = "all"
                                )
                            )
                        }
                        
                    } else {
                    }
                } catch (e: Exception) {
                    Logger.e("CaptureCanvasStream", "CaptureCanvasStream: Error capturing whiteboard stream: ${e.message}")
                    e.printStackTrace()
                }
            } else {
            }
        } else if (!start && canvasStream != null) {
            // Stop the canvas stream
            try {
                
                // Stop all tracks in the stream
                canvasStream.getTracks().forEach { track ->
                    track.stop()
                }
                
                updateCanvasStream(null)

                // Disconnect the transport - create a minimal adapter for DisconnectSendTransportScreenParameters
                val disconnectParams = object : com.mediasfu.sdk.consumers.DisconnectSendTransportScreenParameters {
                    override val screenProducer: WebRtcProducer? get() = params.screenProducer
                    override val socket: com.mediasfu.sdk.socket.SocketManager? get() = params.socket
                    override val localScreenProducer: WebRtcProducer? get() = params.localScreenProducer
                    override val localSocket: com.mediasfu.sdk.socket.SocketManager? get() = params.localSocket
                    override val roomName: String get() = "whiteboard"
                    override fun updateScreenProducer(producer: WebRtcProducer?) {
                        params.updateScreenProducer(producer)
                    }
                    override fun updateLocalScreenProducer(producer: WebRtcProducer?) {
                        params.updateLocalScreenProducer?.invoke(producer)
                    }
                    override fun getUpdatedAllParams() = this
                }
                
                com.mediasfu.sdk.consumers.disconnectSendTransportScreen(
                    com.mediasfu.sdk.consumers.DisconnectSendTransportScreenOptions(
                        parameters = disconnectParams
                    )
                )

                Logger.e("CaptureCanvasStream", "CaptureCanvasStream: Canvas stream stopped and transport disconnected.")
            } catch (e: Exception) {
                Logger.e("CaptureCanvasStream", "CaptureCanvasStream: Error stopping canvas stream: ${e.message}")
            }
        }
    } catch (e: Exception) {
        Logger.e("CaptureCanvasStream", "CaptureCanvasStream: Error in captureCanvasStream: ${e.message}")
        e.printStackTrace()
    }
}