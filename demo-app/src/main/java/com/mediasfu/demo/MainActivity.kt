package com.mediasfu.demo

import android.app.Activity
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mediasfu.sdk.MediaSfuEngine
import com.mediasfu.sdk.methods.MediasfuParameters
import com.mediasfu.sdk.model.Credentials
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.Stream
import com.mediasfu.sdk.ui.mediasfu.MediasfuGeneric
import com.mediasfu.sdk.ui.mediasfu.MediasfuGenericOptions
import com.mediasfu.sdk.ui.mediasfu.MediasfuGenericState
import com.mediasfu.sdk.ui.mediasfu.PreJoinPage
import com.mediasfu.sdk.ui.components.display.CardVideoDisplayOptions
import com.mediasfu.sdk.ui.components.display.DefaultCardVideoDisplay
import com.mediasfu.sdk.ui.components.display.renderCompose
import com.mediasfu.sdk.webrtc.WebRtcFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch

/**
 * MediaSFU Demo App
 * 
 * Demonstrates all SDK usage modes:
 * 1. High-Level (Default UI) - Using MediasfuGeneric with full UI
 * 2. Custom UI (returnUI = false) - Using SDK backend with custom UI
 * 3. Custom Component Override - Replacing specific UI components
 * 4. Community Edition - Using local/self-hosted server
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ScreenCaptureService.ensureChannelExists(this)
        
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DemoApp()
                }
            }
        }
    }
}

/**
 * Demo mode selection
 */
enum class DemoMode {
    MENU,                    // Main menu
    HIGH_LEVEL_DEFAULT,      // Default MediaSFU UI
    HIGH_LEVEL_CE,           // Community Edition with default UI  
    CUSTOM_UI_NO_RETURN,     // returnUI = false mode
    CUSTOM_COMPONENT,        // customComponent override
    PREJOIN_ONLY,            // Just PreJoinPage
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoApp() {
    var currentMode by remember { mutableStateOf(DemoMode.MENU) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Initialize MediaSFU Engine
    val engine = remember(context) {
        MediaSfuEngine(deviceProvider = {
            runCatching { WebRtcFactory.createDevice(context) }.getOrNull()
        })
    }
    val engineParameters = remember(engine) { engine.getParameters() }
    
    // Permission handling
    var pendingAudioPermission by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    var pendingCameraPermission by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    var pendingScreenCapturePermission by remember { mutableStateOf<CompletableDeferred<Map<String, Any?>?>?>(null) }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        engineParameters.hasAudioPermission = granted
        pendingAudioPermission?.complete(granted)
        pendingAudioPermission = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        engineParameters.hasCameraPermission = granted
        pendingCameraPermission?.complete(granted)
        pendingCameraPermission = null
    }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val permissionData = if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            mapOf<String, Any?>("resultCode" to result.resultCode, "data" to result.data)
        } else {
            null
        }
        pendingScreenCapturePermission?.complete(permissionData)
        pendingScreenCapturePermission = null
    }

    // Setup permission callbacks
    LaunchedEffect(Unit) {
        engine.initializeDevice(context)
        
        engineParameters.hasAudioPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        
        engineParameters.hasCameraPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        engineParameters.requestPermissionAudio = suspend {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                engineParameters.hasAudioPermission = true
                true
            } else {
                val deferred = CompletableDeferred<Boolean>()
                pendingAudioPermission?.cancel()
                pendingAudioPermission = deferred
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                deferred.await()
            }
        }

        engineParameters.requestPermissionCamera = suspend {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                engineParameters.hasCameraPermission = true
                true
            } else {
                val deferred = CompletableDeferred<Boolean>()
                pendingCameraPermission?.cancel()
                pendingCameraPermission = deferred
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                deferred.await()
            }
        }

        engineParameters.requestScreenCapturePermission = suspend {
            ScreenCaptureService.start(context)
            val deferred = CompletableDeferred<Map<String, Any?>?>()
            pendingScreenCapturePermission?.cancel()
            pendingScreenCapturePermission = deferred
            val mediaProjectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
            screenCaptureLauncher.launch(captureIntent)
            val result = deferred.await()
            if (result == null) {
                ScreenCaptureService.stop(context)
            }
            result
        }

        engineParameters.stopScreenCaptureService = {
            ScreenCaptureService.stop(context)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            pendingAudioPermission?.cancel()
            pendingCameraPermission?.cancel()
            pendingScreenCapturePermission?.cancel()
            engineParameters.requestPermissionAudio = null
            engineParameters.requestPermissionCamera = null
            engineParameters.requestScreenCapturePermission = null
        }
    }

    // Render based on mode
    when (currentMode) {
        DemoMode.MENU -> {
            DemoMenu(
                onModeSelected = { currentMode = it }
            )
        }
        DemoMode.HIGH_LEVEL_DEFAULT -> {
            HighLevelDefaultDemo(
                engineParameters = engineParameters,
                onBack = { currentMode = DemoMode.MENU }
            )
        }
        DemoMode.HIGH_LEVEL_CE -> {
            HighLevelCommunityEditionDemo(
                engineParameters = engineParameters,
                onBack = { currentMode = DemoMode.MENU }
            )
        }
        DemoMode.CUSTOM_UI_NO_RETURN -> {
            CustomUINoReturnDemo(
                engineParameters = engineParameters,
                onBack = { currentMode = DemoMode.MENU }
            )
        }
        DemoMode.CUSTOM_COMPONENT -> {
            CustomComponentDemo(
                engineParameters = engineParameters,
                onBack = { currentMode = DemoMode.MENU }
            )
        }
        DemoMode.PREJOIN_ONLY -> {
            PreJoinOnlyDemo(
                engineParameters = engineParameters,
                onBack = { currentMode = DemoMode.MENU }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoMenu(onModeSelected: (DemoMode) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MediaSFU SDK Demo") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Select Demo Mode",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "This demo app showcases different ways to use the MediaSFU SDK, similar to the examples in React and Flutter.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // High-Level Default UI
            DemoModeCard(
                title = "1. High-Level (Default UI)",
                description = "Full MediaSFU experience with default UI. Uses MediasfuGeneric with PreJoinPage. Best for quick integration.",
                onClick = { onModeSelected(DemoMode.HIGH_LEVEL_DEFAULT) }
            )
            
            // Community Edition
            DemoModeCard(
                title = "2. Community Edition",
                description = "Connect to a self-hosted MediaSFU Community Edition server with full default UI.",
                onClick = { onModeSelected(DemoMode.HIGH_LEVEL_CE) }
            )
            
            // Custom UI (returnUI = false)
            DemoModeCard(
                title = "3. Custom UI (returnUI = false)",
                description = "Use MediaSFU's backend engine while building your own completely custom UI. Access all state and methods via sourceParameters.",
                onClick = { onModeSelected(DemoMode.CUSTOM_UI_NO_RETURN) }
            )
            
            // Custom Component Override
            DemoModeCard(
                title = "4. Custom Component Override",
                description = "Replace the main workspace with your custom component while keeping the PreJoin flow. Uses customComponent option.",
                onClick = { onModeSelected(DemoMode.CUSTOM_COMPONENT) }
            )
            
            // PreJoin Only
            DemoModeCard(
                title = "5. PreJoin Page Only",
                description = "Just the PreJoinPage with custom handling. Use preJoinPageWidget to customize the entry flow.",
                onClick = { onModeSelected(DemoMode.PREJOIN_ONLY) }
            )
        }
    }
}

@Composable
fun DemoModeCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ============================================================================
// Demo Mode 1: High-Level Default UI
// ============================================================================

@Composable
fun HighLevelDefaultDemo(
    engineParameters: MediasfuParameters,
    onBack: () -> Unit
) {
    // Credentials - Replace with your actual MediaSFU Cloud credentials
    val credentials = Credentials(
        apiUserName = "yourDevUser",
        apiKey = "yourDevApiKey1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
    )
    
    val options = remember(engineParameters) {
        MediasfuGenericOptions(
            preJoinPageWidget = { state -> PreJoinPage(state) },
            connectMediaSFU = true,
            credentials = credentials,
            sourceParameters = engineParameters,
            // returnUI = true (default) - shows full MediaSFU UI
        )
    }
    
    BackHandler { onBack() }
    
    MediasfuGeneric(
        options = options,
        modifier = Modifier.fillMaxSize()
    )
}

// ============================================================================
// Demo Mode 2: Community Edition
// ============================================================================

@Composable
fun HighLevelCommunityEditionDemo(
    engineParameters: MediasfuParameters,
    onBack: () -> Unit
) {
    val options = remember(engineParameters) {
        MediasfuGenericOptions(
            preJoinPageWidget = { state -> PreJoinPage(state) },
            localLink = "http://YOUR_CE_SERVER:3000", // Your CE server
            connectMediaSFU = false, // Don't use cloud
            sourceParameters = engineParameters,
        )
    }
    
    BackHandler { onBack() }
    
    MediasfuGeneric(
        options = options,
        modifier = Modifier.fillMaxSize()
    )
}

// ============================================================================
// Demo Mode 3: Custom UI (returnUI = false)
// ============================================================================

// Configuration for auto-connect (like Flutter - no PreJoinPage)
private val demoLocalLink = "http://YOUR_CE_SERVER:3000"  // Your Community Edition server
private val demoDummyCredentials = Credentials(
    apiUserName = "yourDevUser",
    apiKey = "yourDevApiKey1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomUINoReturnDemo(
    engineParameters: MediasfuParameters,
    onBack: () -> Unit
) {
    // Track connection state reactively
    // Like Flutter: check roomName.isNotEmpty() && roomName != "none" for connection status
    var isConnected by remember { mutableStateOf(false) }
    var userName by remember { mutableStateOf("DemoUser-${System.currentTimeMillis() % 1000}") }
    
    // Poll engine state to detect when connection completes - like Flutter's _updateStateParameters
    LaunchedEffect(Unit) {
        while (true) {
            val roomName = engineParameters.roomName
            val validated = engineParameters.validated
            val socket = engineParameters.socket
            val localSocket = engineParameters.localSocket
            
            // Check connection like Flutter: roomName must be non-empty and not "none"
            val hasRoom = roomName.isNotEmpty() && roomName != "none"
            val hasSocket = socket != null || localSocket != null
            val wasConnected = isConnected
            isConnected = hasRoom && validated
            
            if (isConnected != wasConnected) {
                println("DEMO-APP CustomUI: Connection state changed to $isConnected")
            }
            println("DEMO-APP CustomUI: polling - validated=$validated, roomName=$roomName, hasSocket=$hasSocket, isConnected=$isConnected")
            kotlinx.coroutines.delay(500)
        }
    }
    
    // Like Flutter: returnUI=false with noUIPreJoinOptionsCreate for auto-connect
    // No PreJoinPage - MediaSFU creates/joins room automatically under the hood
    val options = remember(engineParameters) {
        MediasfuGenericOptions(
            preJoinPageWidget = null,  // No PreJoinPage
            localLink = demoLocalLink,
            connectMediaSFU = false,  // Use local CE server only
            credentials = demoDummyCredentials,
            sourceParameters = engineParameters,
            updateSourceParameters = { params ->
                println("DEMO-APP CustomUI: updateSourceParameters called, validated=${params.validated}")
            },
            returnUI = false,  // No MediaSFU UI at all
            noUIPreJoinOptionsCreate = mapOf(
                "action" to "create",
                "duration" to 30,
                "capacity" to 10,
                "userName" to userName,
                "eventType" to "conference"  // Video conference for demo
            )
        )
    }
    
    BackHandler { onBack() }
    
    // MediasfuGeneric runs the engine and handles all SDK logic
    // We use customComponent to inject our own custom UI
    MediasfuGeneric(
        options = options.copy(
            customComponent = { state ->
                // Custom UI renders here inside MediasfuGeneric
                CustomVideoConferenceUI(
                    parameters = engineParameters,
                    isConnected = isConnected,
                    onBack = onBack
                )
            }
        ),
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * A complete custom video conference UI built from scratch.
 * Demonstrates accessing all MediaSFU state and controls via sourceParameters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomVideoConferenceUI(
    parameters: MediasfuParameters,
    isConnected: Boolean,
    onBack: () -> Unit
) {
    // Observe state from MediaSFU engine
    val roomName = parameters.roomName
    val member = parameters.member
    val participants = parameters.participants
    val allVideoStreams = parameters.allVideoStreamsState
    val isVideoOn = parameters.videoAlreadyOn
    val isAudioOn = parameters.audioAlreadyOn
    val isScreenSharing = parameters.screenAlreadyOn
    val isRecording = parameters.recordStarted
    val isRecordingPaused = parameters.recordPaused
    val islevel = parameters.islevel
    val validated = parameters.validated
    val messages = parameters.messages
    
    val scope = rememberCoroutineScope()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Custom UI Mode", style = MaterialTheme.typography.titleMedium)
                        if (isConnected && roomName.isNotEmpty()) {
                            Text(
                                "Room: $roomName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (!isConnected) {
                            Text(
                                "Connecting...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Connection status indicator
                    if (!isConnected) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    // Recording indicator
                    if (isRecording) {
                        Surface(
                            color = if (isRecordingPaused) Color.Yellow else Color.Red,
                            shape = CircleShape,
                            modifier = Modifier.size(12.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    // Host badge
                    if (islevel == "2") {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "HOST",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            )
        },
        bottomBar = {
            // Custom control bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Video toggle - In returnUI=false mode, you control the engine yourself
                    // The MediasfuParameters has videoAlreadyOn state but control requires 
                    // using clickVideo/clickAudio from the SDK stream_methods package
                    ControlButton(
                        icon = if (isVideoOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                        label = if (isVideoOn) "Camera" else "Camera Off",
                        isActive = isVideoOn,
                        onClick = {
                            // Note: In production, you'd wire up clickVideo from
                            // com.mediasfu.sdk.methods.stream_methods.clickVideo
                            // with proper ClickVideoParameters adapter
                        }
                    )
                    
                    // Audio toggle
                    ControlButton(
                        icon = if (isAudioOn) Icons.Filled.Mic else Icons.Filled.MicOff,
                        label = if (isAudioOn) "Mic" else "Mic Off",
                        isActive = isAudioOn,
                        onClick = {
                            // Note: In production, use clickAudio with ClickAudioParameters
                        }
                    )
                    
                    // Screen share toggle
                    ControlButton(
                        icon = if (isScreenSharing) Icons.Filled.StopScreenShare else Icons.Filled.ScreenShare,
                        label = if (isScreenSharing) "Stop Share" else "Share",
                        isActive = isScreenSharing,
                        onClick = {
                            // Note: In production, use clickScreenShare with ClickScreenShareParameters
                        }
                    )
                    
                    // Chat button
                    ControlButton(
                        icon = Icons.Filled.Chat,
                        label = "Chat (${messages.size})",
                        isActive = false,
                        onClick = { /* Open chat modal */ }
                    )
                    
                    // End call
                    ControlButton(
                        icon = Icons.Filled.CallEnd,
                        label = "End",
                        isActive = false,
                        isDestructive = true,
                        onClick = {
                            // Note: In production, you'd call disconnect on socket
                        }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFF1C1C1E))
        ) {
            if (!isConnected) {
                // Waiting for connection
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Connecting to room...",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else if (allVideoStreams.isEmpty()) {
                // No video streams yet
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            Icons.Filled.VideoCall,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Waiting for video streams...",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Participants: ${participants.size}",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Show participant list
                        ParticipantList(participants = participants)
                    }
                }
            } else {
                // Video grid
                CustomVideoGrid(
                    streams = allVideoStreams,
                    participants = participants,
                    parameters = parameters,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        FilledIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = when {
                    isDestructive -> Color.Red
                    isActive -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = when {
                    isDestructive -> Color.White
                    isActive -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            ),
            modifier = Modifier.size(48.dp)
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ParticipantList(participants: List<Participant>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Participants (${participants.size})",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            if (participants.isEmpty()) {
                Text(
                    "No participants yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            } else {
                participants.take(10).forEach { participant ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Avatar
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    participant.name.take(1).uppercase(),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        // Name and status
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                participant.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Muted indicator
                                if (participant.muted == true) {
                                    Icon(
                                        Icons.Filled.MicOff,
                                        contentDescription = "Muted",
                                        tint = Color.Red,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                // Video off indicator
                                if (participant.videoOn == false) {
                                    Icon(
                                        Icons.Filled.VideocamOff,
                                        contentDescription = "Video off",
                                        tint = Color.Red,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                        
                        // Level badge
                        val levelBadge = when (participant.islevel) {
                            "2" -> "Host"
                            "1" -> "Co-Host"
                            else -> null
                        }
                        levelBadge?.let {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    it,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
                
                if (participants.size > 10) {
                    Text(
                        "... and ${participants.size - 10} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CustomVideoGrid(
    streams: List<Stream>,
    participants: List<Participant>,
    parameters: MediasfuParameters,
    modifier: Modifier = Modifier
) {
    // Determine grid layout based on number of streams
    val columns = when {
        streams.size <= 1 -> 1
        streams.size <= 4 -> 2
        else -> 3
    }
    
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(streams) { stream ->
            // Find participant for this stream
            val participant = participants.find { it.name == stream.name }
            
            CustomVideoCard(
                stream = stream,
                participant = participant,
                parameters = parameters,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )
        }
    }
}

@Composable
fun CustomVideoCard(
    stream: Stream,
    participant: Participant?,
    parameters: MediasfuParameters,
    modifier: Modifier = Modifier
) {
    val displayName = participant?.name ?: stream.name ?: "Unknown"
    val isMuted = participant?.muted ?: false
    val isVideoOn = participant?.videoOn ?: (stream.stream != null)
    
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C678F))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Render video if available using SDK's CardVideoDisplay
            if (stream.stream != null) {
                val cardVideoDisplay = DefaultCardVideoDisplay(
                    CardVideoDisplayOptions(
                        videoStream = stream.stream,
                        remoteProducerId = stream.producerId,
                        displayLabel = displayName,
                        doMirror = stream.producerId == "youyou" || stream.name == parameters.member,
                        forceFullDisplay = false,
                        showInfo = true,
                        showControls = false,
                        participant = participant,
                        audioDecibels = parameters.audioDecibels
                    )
                )
                cardVideoDisplay.renderCompose()
            } else {
                // Audio-only or no stream - show avatar
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Avatar circle
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.2f),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                displayName.take(2).uppercase(),
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        displayName,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // Overlay with name and mute status
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(
                        Color.Black.copy(alpha = 0.5f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    displayName,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
                if (isMuted) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.MicOff,
                        contentDescription = "Muted",
                        tint = Color.Red,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// ============================================================================
// Demo Mode 4: Custom Component Override
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomComponentDemo(
    engineParameters: MediasfuParameters,
    onBack: () -> Unit
) {
    val credentials = Credentials(
        apiUserName = "yourDevUser",
        apiKey = "yourDevApiKey1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
    )
    
    val options = remember(engineParameters) {
        MediasfuGenericOptions(
            preJoinPageWidget = { state -> PreJoinPage(state) },
            connectMediaSFU = true,
            credentials = credentials,
            sourceParameters = engineParameters,
            // Replace the main workspace component with custom implementation
            customComponent = { state -> 
                CustomMainWorkspace(state = state, onBack = onBack)
            }
        )
    }
    
    BackHandler { onBack() }
    
    MediasfuGeneric(
        options = options,
        modifier = Modifier.fillMaxSize()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomMainWorkspace(
    state: MediasfuGenericState,
    onBack: () -> Unit
) {
    // Access state through public API
    val roomName = state.room.roomName
    val participants = state.room.participants
    // Use nonAlVideoStreams or paginatedStreams for video streams
    val videoStreams = state.streams.nonAlVideoStreams
    val isVideoOn = state.media.videoAlreadyOn
    val isAudioOn = state.media.audioAlreadyOn
    val isScreenSharing = state.media.screenAlreadyOn
    val messages = state.messaging.messages
    
    val scope = rememberCoroutineScope()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Custom Workspace", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Room: $roomName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Text(
                        "${participants.size} participants",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        },
        bottomBar = {
            // Custom bottom control bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Video toggle
                    Button(
                        onClick = { 
                            scope.launch { state.toggleVideo() }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isVideoOn) 
                                MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            if (isVideoOn) Icons.Filled.Videocam else Icons.Filled.VideocamOff,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isVideoOn) "Camera" else "Camera Off")
                    }
                    
                    // Audio toggle
                    Button(
                        onClick = { 
                            scope.launch { state.toggleAudio() }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAudioOn) 
                                MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            if (isAudioOn) Icons.Filled.Mic else Icons.Filled.MicOff,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isAudioOn) "Mic" else "Mic Off")
                    }
                    
                    // Screen share toggle
                    Button(
                        onClick = { 
                            scope.launch { state.toggleScreenShare() }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isScreenSharing) 
                                MaterialTheme.colorScheme.tertiary 
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Icon(
                            if (isScreenSharing) Icons.Filled.StopScreenShare else Icons.Filled.ScreenShare,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    // End call
                    Button(
                        onClick = { 
                            scope.launch { state.closeAndReset() }
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Filled.CallEnd,
                            contentDescription = "End Call",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFF1C1C1E))
        ) {
            // Video grid using state.streams.nonAlVideoStreams
            if (videoStreams.isEmpty()) {
                // No streams yet - show waiting state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Groups,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Waiting for participants...",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Show current participants
                        if (participants.isNotEmpty()) {
                            Text(
                                "${participants.size} in room:",
                                color = Color.White.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodySmall
                            )
                            participants.take(5).forEach { p ->
                                Text(
                                    "• ${p.name}",
                                    color = Color.White.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            } else {
                // Show video grid
                val columns = when {
                    videoStreams.size <= 1 -> 1
                    videoStreams.size <= 4 -> 2
                    else -> 3
                }
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier
                        .weight(1f)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(videoStreams) { stream ->
                        val participant = participants.find { it.name == stream.name }
                        val displayName = participant?.name ?: stream.name ?: "Unknown"
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C678F))
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (stream.stream != null) {
                                    // Render actual video using SDK's CardVideoDisplay
                                    val cardVideoDisplay = DefaultCardVideoDisplay(
                                        CardVideoDisplayOptions(
                                            videoStream = stream.stream,
                                            remoteProducerId = stream.producerId,
                                            displayLabel = displayName,
                                            doMirror = stream.producerId == "youyou",
                                            forceFullDisplay = false,
                                            showInfo = true,
                                            showControls = false,
                                            participant = participant
                                        )
                                    )
                                    cardVideoDisplay.renderCompose()
                                } else {
                                    // No video - show avatar
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.White.copy(alpha = 0.2f),
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    displayName.take(2).uppercase(),
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            displayName,
                                            color = Color.White,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                
                                // Name badge
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp)
                                        .background(
                                            Color.Black.copy(alpha = 0.5f),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        displayName,
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    if (participant?.muted == true) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            Icons.Filled.MicOff,
                                            contentDescription = "Muted",
                                            tint = Color.Red,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Info bar at bottom
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF2C2C2E)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Custom Workspace via customComponent",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        "Chat: ${messages.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

// ============================================================================
// Demo Mode 5: PreJoin Only
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreJoinOnlyDemo(
    engineParameters: MediasfuParameters,
    onBack: () -> Unit
) {
    val credentials = Credentials(
        apiUserName = "yourDevUser",
        apiKey = "yourDevApiKey1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
    )
    
    val options = remember(engineParameters) {
        MediasfuGenericOptions(
            // Custom PreJoin that wraps the default with extra UI
            preJoinPageWidget = { state -> 
                CustomPreJoinWrapper(state = state, onBack = onBack)
            },
            connectMediaSFU = true,
            credentials = credentials,
            sourceParameters = engineParameters,
        )
    }
    
    BackHandler { onBack() }
    
    MediasfuGeneric(
        options = options,
        modifier = Modifier.fillMaxSize()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPreJoinWrapper(
    state: MediasfuGenericState,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Custom PreJoin") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Custom header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Welcome to MediaSFU",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "This is a custom wrapper around PreJoinPage",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            // Default PreJoinPage
            Box(modifier = Modifier.weight(1f)) {
                PreJoinPage(state)
            }
        }
    }
}
