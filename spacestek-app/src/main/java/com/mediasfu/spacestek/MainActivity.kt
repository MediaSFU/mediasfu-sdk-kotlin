package com.mediasfu.spacestek

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mediasfu.spacestek.model.*
import com.mediasfu.spacestek.ui.theme.SpacesTekTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred

// ============================================================
// MediaSFU SDK Imports - These are the core SDK components
// ============================================================
import com.mediasfu.sdk.MediaSfuEngine
import com.mediasfu.sdk.webrtc.WebRtcFactory
import com.mediasfu.sdk.model.EventType
import com.mediasfu.sdk.model.Credentials
import com.mediasfu.sdk.ui.mediasfu.MediasfuGeneric
import com.mediasfu.sdk.ui.mediasfu.MediasfuGenericOptions
import com.mediasfu.sdk.ui.mediasfu.PreJoinPage
import com.mediasfu.sdk.methods.MediasfuParameters
import androidx.activity.compose.BackHandler
// ============================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SpacesTekTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SpacesTekApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpacesTekApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // ============================================================
    // MediaSFU SDK Engine Setup
    // ============================================================
    val engine = remember(context) {
        MediaSfuEngine(deviceProvider = {
            runCatching { WebRtcFactory.createDevice(context) }
                .getOrNull()
        })
    }
    val engineParameters = remember(engine) { engine.getParameters() }
    
    // Permission handling for SDK
    var pendingAudioPermission by remember { mutableStateOf<CompletableDeferred<Boolean>?>(null) }
    
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        engineParameters.hasAudioPermission = granted
        pendingAudioPermission?.complete(granted)
        pendingAudioPermission = null
    }
    
    LaunchedEffect(context) {
        engine.initializeDevice(context)
        engineParameters.hasAudioPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            
        // Setup audio permission request callback for SDK
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
    }
    
    DisposableEffect(Unit) {
        onDispose {
            pendingAudioPermission?.cancel()
            engineParameters.requestPermissionAudio = null
        }
    }
    
    // ============================================================
    // MediaSFU Configuration - Like Flutter, use dummy credentials and local server
    // ============================================================
    val localLink = "http://YOUR_CE_SERVER:3000"  // Your Community Edition server
    val dummyCredentials = Credentials(
        apiUserName = "yourDevUser",
        apiKey = "yourDevApiKey1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
    )
    
    // MediaSFU SDK state for real audio rooms
    var isInSpace by remember { mutableStateOf(false) }
    var currentSpaceTitle by remember { mutableStateOf("") }
    var mediasfuOptions by remember { mutableStateOf<MediasfuGenericOptions?>(null) }
    
    // Observe connection state reactively
    // Like Flutter: check roomName.isNotEmpty() && roomName != "none" for connection status
    var isConnected by remember { mutableStateOf(false) }
    LaunchedEffect(isInSpace) {
        while (isInSpace) {
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
                println("SPACESTEK-APP: Connection state changed to $isConnected")
            }
            println("SPACESTEK-APP: polling - validated=$validated, roomName=$roomName, hasSocket=$hasSocket, isConnected=$isConnected")
            kotlinx.coroutines.delay(500)
        }
    }
    
    // Function to launch MediaSFU audio room - uses returnUI = false (no PreJoinPage, auto-connect)
    fun launchMediaSfuSpace(spaceName: String) {
        currentSpaceTitle = spaceName
        isInSpace = true
        isConnected = false
        
        // Generate a room ID
        val roomId = "space-${spaceName.replace(" ", "-").lowercase()}-${System.currentTimeMillis() % 10000}"
        
        // Configure for audio-only webinar (Twitter Spaces style)
        engineParameters.eventType = EventType.WEBINAR
        
        // Like Flutter: returnUI=false with noUIPreJoinOptionsCreate for auto-connect
        // No PreJoinPage - MediaSFU creates/joins room automatically
        mediasfuOptions = MediasfuGenericOptions(
            preJoinPageWidget = null,  // No PreJoinPage
            localLink = localLink,
            connectMediaSFU = false,  // Use local CE server
            credentials = dummyCredentials,
            sourceParameters = engineParameters,
            updateSourceParameters = { params ->
                println("SPACESTEK-APP: updateSourceParameters called, validated=${params.validated}")
            },
            returnUI = false,  // No MediaSFU UI at all
            noUIPreJoinOptionsCreate = mapOf(
                "action" to "create",
                "duration" to 60,
                "capacity" to 50,
                "userName" to "SpaceHost",
                "eventType" to "webinar"
            )
        )
    }
    
    fun leaveSpace() {
        isInSpace = false
        isConnected = false
        currentSpaceTitle = ""
        mediasfuOptions = null
        engineParameters.validated = false
    }
    // ============================================================
    
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    var selectedTab by remember { mutableStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
    }
    
    val liveSpaces = remember {
        listOf(
            Space("1", "Tech Talk: AI in 2025", "Elon Musk", "@elonmusk", isLive = true, listenerCount = 15420, speakerCount = 5, topics = listOf("AI", "Tech", "Future")),
            Space("2", "Crypto Market Update", "CZ Binance", "@caborsssu", isLive = true, listenerCount = 8340, speakerCount = 3, topics = listOf("Crypto", "Finance")),
            Space("3", "Music Production Tips", "Marshmello", "@marshmello", isLive = true, listenerCount = 4200, speakerCount = 2, topics = listOf("Music", "Production")),
            Space("4", "Startup Funding Q&A", "Marc Andreessen", "@pmarca", isLive = true, listenerCount = 6800, speakerCount = 4, topics = listOf("Startups", "VC")),
        )
    }
    
    val scheduledSpaces = remember {
        listOf(
            Space("5", "Web3 Development Workshop", "Vitalik Buterin", "@vitalik", isScheduled = true, scheduledTime = System.currentTimeMillis() + 3600000, topics = listOf("Web3", "Ethereum")),
            Space("6", "Gaming Industry Trends", "Phil Spencer", "@xbox", isScheduled = true, scheduledTime = System.currentTimeMillis() + 7200000, topics = listOf("Gaming", "Xbox")),
        )
    }
    
    // ============================================================
    // Show native SpaceRoomScreen when in a Space
    // MediasfuGeneric runs the SDK engine but we use customComponent to render our own UI
    // ============================================================
    if (isInSpace && mediasfuOptions != null) {
        BackHandler { leaveSpace() }
        
        // MediasfuGeneric runs the engine and handles all SDK logic
        // We use customComponent to inject our own UI on top
        MediasfuGeneric(
            options = mediasfuOptions!!.copy(
                customComponent = { state ->
                    // Our custom SpaceRoomScreen renders here inside MediasfuGeneric
                    SpaceRoomScreen(
                        spaceTitle = currentSpaceTitle,
                        hostName = "You",
                        engineParameters = engineParameters,
                        isConnected = isConnected,
                        onToggleAudio = { state.toggleAudio() },
                        onLeave = { 
                            // Properly disconnect from the room via SDK
                            state.exitSession()
                            // Then reset local UI state
                            leaveSpace() 
                        }
                    )
                }
            ),
            modifier = Modifier.fillMaxSize()
        )
        return
    }
    // ============================================================
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Podcasts,
                            contentDescription = null,
                            tint = SpacesPurple,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "SpacesTek",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = { /* Search */ }) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = { /* Profile */ }) {
                        Icon(Icons.Rounded.AccountCircle, contentDescription = "Profile")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Rounded.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Rounded.Explore, contentDescription = "Discover") },
                    label = { Text("Discover") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = "Scheduled") },
                    label = { Text("Scheduled") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = SpacesPurple,
                contentColor = Color.White
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Space")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Directly navigate to MediaSFU audio room - no intermediate simulated screen
            when (selectedTab) {
                0 -> {
                    HomeScreen(
                        liveSpaces = liveSpaces,
                        scheduledSpaces = scheduledSpaces,
                        onSpaceClick = { space ->
                            if (!hasAudioPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                // Directly launch MediaSFU audio room
                                launchMediaSfuSpace(space.title)
                            }
                        },
                        modifier = Modifier.padding(padding)
                    )
                }
                1 -> {
                    DiscoverScreen(
                        spaces = liveSpaces + scheduledSpaces,
                        onSpaceClick = { space ->
                            if (space.isLive) {
                                // Directly launch MediaSFU audio room
                                launchMediaSfuSpace(space.title)
                            }
                        },
                        modifier = Modifier.padding(padding)
                    )
                }
                2 -> {
                    ScheduledScreen(
                        spaces = scheduledSpaces,
                        onRemind = { /* Set reminder */ },
                        modifier = Modifier.padding(padding)
                    )
                }
            }
        }
    }
    
    if (showCreateDialog) {
        CreateSpaceDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title, topics ->
                showCreateDialog = false
                // Directly launch MediaSFU space when creating
                launchMediaSfuSpace(title)
            }
        )
    }
}

val SpacesPurple = Color(0xFF8B5CF6)
val SpacesPurpleDark = Color(0xFF6D28D9)

@Composable
fun HomeScreen(
    liveSpaces: List<Space>,
    scheduledSpaces: List<Space>,
    onSpaceClick: (Space) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text(
                "🔴 Live Now",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        
        items(liveSpaces) { space ->
            SpaceCard(
                space = space,
                onClick = { onSpaceClick(space) }
            )
        }
        
        if (scheduledSpaces.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "📅 Coming Up",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            items(scheduledSpaces) { space ->
                ScheduledSpaceCard(
                    space = space,
                    onRemind = { /* Set reminder */ }
                )
            }
        }
    }
}

@Composable
fun SpaceCard(
    space: Space,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Topics
            if (space.topics.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(space.topics) { topic ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = SpacesPurple.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = topic,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = SpacesPurple
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // Title
            Text(
                text = space.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Host info
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(SpacesPurple),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        space.hostName.first().uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        space.hostName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        space.hostHandle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Live indicator
                    if (space.isLive) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "LIVE",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFEF4444),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    
                    Icon(
                        Icons.Rounded.Headphones,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        formatCount(space.listenerCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Icon(
                        Icons.Rounded.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${space.speakerCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                FilledTonalButton(
                    onClick = onClick,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SpacesPurple,
                        contentColor = Color.White
                    )
                ) {
                    Text("Join")
                }
            }
        }
    }
}

@Composable
fun ScheduledSpaceCard(
    space: Space,
    onRemind: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Scheduled time
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = SpacesPurple
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        formatScheduledTime(space.scheduledTime ?: 0),
                        style = MaterialTheme.typography.labelMedium,
                        color = SpacesPurple
                    )
                }
                
                OutlinedButton(
                    onClick = onRemind,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = SpacesPurple
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SpacesPurple)
                ) {
                    Icon(
                        Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Remind")
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                space.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Hosted by ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    space.hostName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun SpaceRoomScreen(
    space: Space,
    spaceState: SpaceState,
    onLeave: () -> Unit,
    onLaunchMediaSfu: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isMuted by remember { mutableStateOf(true) }
    var isHandRaised by remember { mutableStateOf(false) }
    
    val participants = remember {
        listOf(
            SpaceParticipant("1", space.hostName, space.hostHandle, role = ParticipantRole.HOST, isMuted = false, isSpeaking = true),
            SpaceParticipant("2", "Sarah Connor", "@sarahc", avatarColor = Color(0xFFEC4899), role = ParticipantRole.CO_HOST, isMuted = false),
            SpaceParticipant("3", "John Matrix", "@matrix", avatarColor = Color(0xFF10B981), role = ParticipantRole.SPEAKER, isMuted = true),
            SpaceParticipant("4", "Ellen Ripley", "@ripley", avatarColor = Color(0xFFF59E0B), role = ParticipantRole.SPEAKER, isMuted = false, isSpeaking = true),
            SpaceParticipant("5", "You", "@you", avatarColor = Color(0xFF3B82F6), role = ParticipantRole.LISTENER, isMuted = isMuted),
        ) + List(20) { i ->
            SpaceParticipant(
                "${i + 10}",
                "Listener ${i + 1}",
                "@listener${i + 1}",
                avatarColor = Color(
                    listOf(0xFFEF4444, 0xFFF97316, 0xFF84CC16, 0xFF06B6D4, 0xFF8B5CF6).random().toInt()
                ),
                role = ParticipantRole.LISTENER,
                isMuted = true
            )
        }
    }
    
    val speakers = participants.filter { it.role != ParticipantRole.LISTENER }
    val listeners = participants.filter { it.role == ParticipantRole.LISTENER }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SpacesPurple.copy(alpha = 0.1f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { /* Minimize */ }) {
                Icon(Icons.Rounded.ExpandMore, contentDescription = "Minimize")
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    formatCount(space.listenerCount + participants.size),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            
            IconButton(onClick = { /* More options */ }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More")
            }
        }
        
        // Space title
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                space.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(space.topics) { topic ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SpacesPurple.copy(alpha = 0.15f)
                    ) {
                        Text(
                            topic,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = SpacesPurple
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Speakers section
        Text(
            "Speakers",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(speakers) { participant ->
                SpeakerAvatar(participant = participant)
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Listeners section
        Text(
            "Listeners (${listeners.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(listeners) { participant ->
                ListenerAvatar(participant = participant)
            }
        }
        
        // Bottom controls
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Column {
                // Launch MediaSFU button
                Button(
                    onClick = { onLaunchMediaSfu(space.title) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpacesPurple
                    )
                ) {
                    Icon(
                        Icons.Rounded.Podcasts,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Launch Real MediaSFU Space")
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Leave button
                    OutlinedButton(
                        onClick = onLeave,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFEF4444)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444))
                    ) {
                        Text("Leave")
                    }
                
                // Raise hand
                IconButton(
                    onClick = { isHandRaised = !isHandRaised },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isHandRaised) SpacesPurple else MaterialTheme.colorScheme.surfaceVariant
                        )
                ) {
                    Text(
                        "✋",
                        fontSize = 20.sp
                    )
                }
                
                // Mute toggle
                IconButton(
                    onClick = { isMuted = !isMuted },
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            if (isMuted) MaterialTheme.colorScheme.surfaceVariant else SpacesPurple
                        )
                ) {
                    Icon(
                        if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                        contentDescription = if (isMuted) "Unmute" else "Mute",
                        tint = if (isMuted) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                    )
                }
                
                // Share
                IconButton(
                    onClick = { /* Share */ },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        Icons.Rounded.Share,
                        contentDescription = "Share"
                    )
                }
                
                // React
                IconButton(
                    onClick = { /* React */ },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text("❤️", fontSize = 20.sp)
                }
                }
            }
        }
    }
}

@Composable
fun SpeakerAvatar(participant: SpaceParticipant) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(participant.avatarColor)
                    .then(
                        if (participant.isSpeaking) {
                            Modifier.border(3.dp, SpacesPurple, CircleShape)
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    participant.name.first().uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Role badge
            if (participant.role == ParticipantRole.HOST || participant.role == ParticipantRole.CO_HOST) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp),
                    shape = CircleShape,
                    color = if (participant.role == ParticipantRole.HOST) SpacesPurple else Color(0xFF10B981)
                ) {
                    Icon(
                        if (participant.role == ParticipantRole.HOST) Icons.Rounded.Star else Icons.Rounded.Verified,
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(2.dp),
                        tint = Color.White
                    )
                }
            }
            
            // Mute indicator
            if (participant.isMuted) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .offset(x = (-4).dp, y = 4.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error
                ) {
                    Icon(
                        Icons.Rounded.MicOff,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(2.dp),
                        tint = Color.White
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            participant.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            when (participant.role) {
                ParticipantRole.HOST -> "Host"
                ParticipantRole.CO_HOST -> "Co-host"
                else -> "Speaker"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ListenerAvatar(participant: SpaceParticipant) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(participant.avatarColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                participant.name.first().uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            participant.name.split(" ").first(),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun DiscoverScreen(
    spaces: List<Space>,
    onSpaceClick: (Space) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = listOf("All", "Tech", "Music", "Sports", "News", "Gaming", "Crypto")
    var selectedCategory by remember { mutableStateOf("All") }
    
    Column(modifier = modifier.fillMaxSize()) {
        // Categories
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = { Text(category) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SpacesPurple,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
        
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(spaces.filter { it.isLive }) { space ->
                SpaceCard(space = space, onClick = { onSpaceClick(space) })
            }
        }
    }
}

@Composable
fun ScheduledScreen(
    spaces: List<Space>,
    onRemind: (Space) -> Unit,
    modifier: Modifier = Modifier
) {
    if (spaces.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No scheduled Spaces",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(spaces) { space ->
                ScheduledSpaceCard(space = space, onRemind = { onRemind(space) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSpaceDialog(
    onDismiss: () -> Unit,
    onCreate: (String, List<String>) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var topics by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start a Space") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("What do you want to talk about?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = topics,
                    onValueChange = { topics = it },
                    label = { Text("Topics (comma separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onCreate(
                            title,
                            topics.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SpacesPurple)
            ) {
                Text("Start")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun formatCount(count: Int): String {
    return when {
        count >= 1000000 -> String.format("%.1fM", count / 1000000.0)
        count >= 1000 -> String.format("%.1fK", count / 1000.0)
        else -> count.toString()
    }
}

fun formatScheduledTime(timestamp: Long): String {
    val diff = timestamp - System.currentTimeMillis()
    return when {
        diff < 3600000 -> "In ${diff / 60000} min"
        diff < 86400000 -> "In ${diff / 3600000} hours"
        else -> "In ${diff / 86400000} days"
    }
}

// ============================================================
// Native SpaceRoomScreen - Displays Space UI while MediaSFU handles audio
// Shows immediately when entering Space, displays connecting state until validated
// ============================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpaceRoomScreen(
    spaceTitle: String,
    hostName: String,
    engineParameters: MediasfuParameters,
    isConnected: Boolean,
    onToggleAudio: () -> Unit = {},
    onLeave: () -> Unit
) {
    // Observe MediaSFU engine state reactively
    var isMuted by remember { mutableStateOf(!engineParameters.audioAlreadyOn) }
    var participantCount by remember { mutableStateOf(1) }
    var roomName by remember { mutableStateOf(spaceTitle) }
    
    // Poll engine state for reactive updates
    LaunchedEffect(Unit) {
        while (true) {
            isMuted = !engineParameters.audioAlreadyOn
            participantCount = engineParameters.participants.size.coerceAtLeast(1)
            if (engineParameters.roomName.isNotBlank()) {
                roomName = engineParameters.roomName
            }
            
            // Debug: Log audio stream state
            val audioStreams = engineParameters.allAudioStreams
            val audioOnlyStreams = engineParameters.audioOnlyStreams
            if (audioStreams.isNotEmpty() || audioOnlyStreams.isNotEmpty()) {
                println("SPACESTEK-AUDIO: allAudioStreams=${audioStreams.size}, audioOnlyStreams=${audioOnlyStreams.size}")
                audioStreams.forEach { stream ->
                    println("SPACESTEK-AUDIO:   Stream producerId=${stream.producerId}, name=${stream.name}, hasStream=${stream.stream != null}")
                }
            }
            
            delay(200)
        }
    }
    
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onLeave) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Leave Space",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* Share */ }) {
                        Icon(
                            Icons.Rounded.Share,
                            contentDescription = "Share",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = { /* More options */ }) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "More",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            SpacesPurple.copy(alpha = 0.8f),
                            Color(0xFF1A1A2E),
                            Color.Black
                        )
                    )
                )
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                
                // Status indicator - Live or Connecting
                Surface(
                    color = if (isConnected) Color.Red else Color.Gray,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isConnected) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                            Text(
                                "LIVE",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        } else {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                "CONNECTING",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Space title
                Text(
                    if (isConnected) roomName else spaceTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Host info
                Text(
                    "Hosted by $hostName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Participant count
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Rounded.Headphones,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        "$participantCount listening",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Audio waveform visualization placeholder
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(20) { index ->
                        val height = if (isMuted) 8.dp else (16 + (index % 5) * 8).dp
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(height)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (isMuted) Color.White.copy(alpha = 0.3f)
                                    else SpacesPurple.copy(alpha = 0.8f)
                                )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Bottom controls
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF1A1A2E),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Request to speak
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = { /* Request to speak */ },
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(
                                            Color.White.copy(alpha = 0.1f),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        Icons.Rounded.FrontHand,
                                        contentDescription = "Request to speak",
                                        tint = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Request",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            
                            // Mute/Unmute (shows current MediaSFU audio state)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = { 
                                        // Toggle audio via MediaSFU SDK
                                        onToggleAudio()
                                    },
                                    modifier = Modifier
                                        .size(72.dp)
                                        .background(
                                            if (isMuted) Color.White.copy(alpha = 0.2f) else SpacesPurple,
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                        contentDescription = if (isMuted) "Unmute" else "Mute",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    if (isMuted) "Muted" else "Speaking",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            
                            // Leave button
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(
                                    onClick = onLeave,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .background(
                                            MaterialTheme.colorScheme.error,
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        Icons.Rounded.CallEnd,
                                        contentDescription = "Leave Space",
                                        tint = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Leave",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
