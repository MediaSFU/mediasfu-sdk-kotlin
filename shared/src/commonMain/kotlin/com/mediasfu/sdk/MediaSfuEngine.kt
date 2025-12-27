package com.mediasfu.sdk
import com.mediasfu.sdk.util.Logger

import com.mediasfu.sdk.consumers.ChangeVidsParameters
import com.mediasfu.sdk.consumers.ConnectSendTransportParameters
import com.mediasfu.sdk.consumers.ConnectSendTransportAudioOptions
import com.mediasfu.sdk.consumers.ConnectSendTransportScreenOptions
import com.mediasfu.sdk.consumers.ConnectSendTransportVideoOptions
import com.mediasfu.sdk.consumers.CreateSendTransportOptions
import com.mediasfu.sdk.consumers.DispStreamsOptions
import com.mediasfu.sdk.consumers.CloseAndResizeOptions
import com.mediasfu.sdk.consumers.CloseAndResizeParameters
import com.mediasfu.sdk.consumers.PrepopulateUserMediaOptions
import com.mediasfu.sdk.consumers.PrepopulateUserMediaParameters
import com.mediasfu.sdk.consumers.connectSendTransportAudio
import com.mediasfu.sdk.consumers.connectSendTransportScreen
import com.mediasfu.sdk.consumers.connectSendTransportVideo
import com.mediasfu.sdk.consumers.prepopulateUserMedia
import com.mediasfu.sdk.consumers.closeAndResize
import com.mediasfu.sdk.consumers.CompareActiveNamesOptions
import com.mediasfu.sdk.consumers.CompareActiveNamesParameters
import com.mediasfu.sdk.consumers.compareActiveNames
import com.mediasfu.sdk.consumers.CompareScreenStatesOptions
import com.mediasfu.sdk.consumers.CompareScreenStatesParameters
import com.mediasfu.sdk.socket.ConnectionState
import com.mediasfu.sdk.socket.CreateJoinLocalRoomResponse
import com.mediasfu.sdk.socket.CreateLocalRoomOptions
import com.mediasfu.sdk.socket.ConnectLocalSocketOptions
import com.mediasfu.sdk.socket.CreateLocalRoomParameters
import com.mediasfu.sdk.socket.JoinEventRoomOptions
import com.mediasfu.sdk.socket.JoinEventRoomParameters
import com.mediasfu.sdk.socket.JoinConRoomOptions
import com.mediasfu.sdk.socket.JoinLocalRoomOptions
import com.mediasfu.sdk.socket.JoinRoomOptions
import com.mediasfu.sdk.socket.ResponseLocalConnection
import com.mediasfu.sdk.socket.ResponseLocalConnectionData
import com.mediasfu.sdk.socket.ResponseJoinLocalRoom
import com.mediasfu.sdk.socket.ResponseJoinRoom
import com.mediasfu.sdk.socket.connectLocalSocket
import com.mediasfu.sdk.socket.createSocketManager
import com.mediasfu.sdk.socket.defaultMeetingRoomParams
import com.mediasfu.sdk.socket.defaultRecordingParams
import com.mediasfu.sdk.methods.MediasfuParameters
import com.mediasfu.sdk.consumers.ScreenState
import com.mediasfu.sdk.consumers.compareScreenStates
import com.mediasfu.sdk.consumers.ConnectIpsOptions
import com.mediasfu.sdk.consumers.connectIps
import com.mediasfu.sdk.consumers.ConsumerTransportInfo
import com.mediasfu.sdk.consumers.ResumePauseStreamsOptions
import com.mediasfu.sdk.consumers.ResumePauseStreamsParameters
import com.mediasfu.sdk.consumers.resumePauseStreams
import com.mediasfu.sdk.consumers.ReorderStreamsOptions
import com.mediasfu.sdk.consumers.ReorderStreamsParameters
import com.mediasfu.sdk.consumers.reorderStreams
import com.mediasfu.sdk.consumers.AddVideosGridOptions
import com.mediasfu.sdk.consumers.ComponentSizes
import com.mediasfu.sdk.consumers.CustomAudioCardBuilder
import com.mediasfu.sdk.consumers.CustomMiniCardBuilder
import com.mediasfu.sdk.consumers.CustomVideoCardBuilder
import com.mediasfu.sdk.consumers.GetEstimateOptions
import com.mediasfu.sdk.consumers.GetEstimateParameters
import com.mediasfu.sdk.consumers.GridSizes
import com.mediasfu.sdk.consumers.UpdateMiniCardsGridOptions
import com.mediasfu.sdk.consumers.addVideosGrid
import com.mediasfu.sdk.consumers.AutoAdjustOptions
import com.mediasfu.sdk.consumers.autoAdjust
import com.mediasfu.sdk.consumers.CalculateRowsAndColumnsOptions
import com.mediasfu.sdk.consumers.calculateRowsAndColumns
import com.mediasfu.sdk.consumers.CheckPermissionException
import com.mediasfu.sdk.consumers.CheckPermissionOptions
import com.mediasfu.sdk.consumers.checkPermission
import com.mediasfu.sdk.consumers.CheckGridOptions
import com.mediasfu.sdk.consumers.checkGrid
import com.mediasfu.sdk.consumers.GetVideosOptions
import com.mediasfu.sdk.consumers.getEstimate
import com.mediasfu.sdk.consumers.getVideos
import com.mediasfu.sdk.consumers.MixStreamsOptions
import com.mediasfu.sdk.consumers.mixStreams
import com.mediasfu.sdk.consumers.TriggerOptions
import com.mediasfu.sdk.consumers.TriggerParameters
import com.mediasfu.sdk.consumers.trigger
import com.mediasfu.sdk.consumers.RePortOptions
import com.mediasfu.sdk.consumers.RePortParameters
import com.mediasfu.sdk.consumers.rePort
import com.mediasfu.sdk.consumers.ProcessConsumerTransportsAudioOptions
import com.mediasfu.sdk.consumers.ProcessConsumerTransportsAudioParameters
import com.mediasfu.sdk.consumers.ProcessConsumerTransportsOptions
import com.mediasfu.sdk.consumers.ProcessConsumerTransportsParameters
import com.mediasfu.sdk.model.SleepOptions
import com.mediasfu.sdk.consumers.ResumePauseAudioStreamsOptions
import com.mediasfu.sdk.consumers.ResumePauseAudioStreamsParameters
import com.mediasfu.sdk.consumers.ProducerClosedOptions as ConnectIpsProducerClosedOptions
import com.mediasfu.sdk.consumers.ReadjustOptions
import com.mediasfu.sdk.consumers.ReadjustParameters
import com.mediasfu.sdk.consumers.socket_receive_methods.ProducerClosedOptions as SocketProducerClosedOptions
import com.mediasfu.sdk.consumers.socket_receive_methods.ProducerClosedParameters as SocketProducerClosedParameters
import com.mediasfu.sdk.consumers.socket_receive_methods.producerClosed as socketProducerClosed
import com.mediasfu.sdk.consumers.processConsumerTransports
import com.mediasfu.sdk.consumers.processConsumerTransportsAudio
import com.mediasfu.sdk.consumers.updateMiniCardsGridImpl
import com.mediasfu.sdk.consumers.readjust
import com.mediasfu.sdk.consumers.resumePauseAudioStreams
import com.mediasfu.sdk.methods.utils.producer.ProducerOptionsType
import com.mediasfu.sdk.model.MeetingRoomParams
import com.mediasfu.sdk.model.Participant
import com.mediasfu.sdk.model.RecordingParams
import com.mediasfu.sdk.model.ShowAlert
import com.mediasfu.sdk.model.SocketConfig
import com.mediasfu.sdk.model.Stream
import com.mediasfu.sdk.model.AudioDecibels
import com.mediasfu.sdk.ui.MediaSfuUIComponent
import com.mediasfu.sdk.webrtc.WebRtcDevice
import com.mediasfu.sdk.webrtc.WebRtcFactory
import com.mediasfu.sdk.webrtc.MediaStream
import com.mediasfu.sdk.webrtc.WebRtcProducer
import com.mediasfu.sdk.webrtc.WebRtcTransport
import kotlinx.coroutines.delay

class MediaSfuEngine(
	private var socketConfig: SocketConfig = SocketConfig(),
	private val deviceProvider: (() -> WebRtcDevice?)? = null
) {
	private val socket = createSocketManager()
	private val parameters = MediasfuParameters()
	private var localConnectionData: ResponseLocalConnectionData? = null

	private fun connectParameters(): EngineConnectSendTransportParameters =
		EngineConnectSendTransportParameters(parameters)
	
	init {
		// Initialize WebRTC device lazily so tests without Android context don't fail
		parameters.device = deviceProvider?.invoke()?.also {
		} ?: runCatching {
			WebRtcFactory.createDevice()
		}.onFailure { error ->
			Logger.e("MediaSfuEngine", "MediaSFU - initializeDevice: deferred Android device init -> ${error.message}")
		}.getOrNull()
	}

	fun initializeDevice(context: Any?) {
		if (context == null) {
			return
		}
		runCatching {
			WebRtcFactory.createDevice(context)
		}.onSuccess { device ->
			parameters.device = device
		}.onFailure { error ->
			Logger.e("MediaSfuEngine", "MediaSFU - initializeDevice: failed -> ${error.message}")
		}
	}

	fun greet(): String = "MediaSFU Kotlin SDK running on ${getPlatform().name}"

	// Connection helpers
	suspend fun connect(url: String, config: SocketConfig = socketConfig) = socket.connect(url, config)
	suspend fun disconnect() = socket.disconnect()
	fun isConnected(): Boolean = socket.isConnected()
	fun connectionState(): ConnectionState = socket.getConnectionState()

	suspend fun connectLocal(
		link: String,
		timeoutMillis: Long = 10000,
		config: SocketConfig = socketConfig.copy(
			transports = listOf("websocket"),
			autoConnect = true
		)
	): Result<ResponseLocalConnection> {
		val options = ConnectLocalSocketOptions(
			socket = socket,
			link = link,
			config = config,
			timeoutMillis = timeoutMillis
		)
		val result = connectLocalSocket(options)
		result.onSuccess { connection ->
			val resolvedData = if (connection.data == ResponseLocalConnectionData.Empty) {
				localConnectionData ?: ResponseLocalConnectionData.Empty
			} else {
				localConnectionData = connection.data
				connection.data
			}
			parameters.localSocket = connection.socket
			parameters.socket = connection.socket
			val resolvedLink = resolvedData.mediasfuURL?.takeIf { value -> value.isNotBlank() } ?: link
			parameters.link = resolvedLink
			resolvedData.apiUserName?.takeIf { value -> value.isNotBlank() }?.let { value ->
				parameters.apiUserName = value
			}
			resolvedData.apiKey?.takeIf { value -> value.isNotBlank() }?.let { value ->
				parameters.apiKey = value
			}
			updateParametersFromLocalConnection(resolvedData)
		}
		return result
	}

	fun latestLocalConnectionData(): ResponseLocalConnectionData? = localConnectionData

	private fun updateParametersFromLocalConnection(data: ResponseLocalConnectionData) {
		if (data == ResponseLocalConnectionData.Empty) return
		parameters.updateCanRecord(data.allowRecord)
		parameters.updateConfirmedToRecord(data.allowRecord)
		applyMeetingRoomParams(data.eventRoomParams ?: defaultMeetingRoomParams())
		applyRecordingParams(data.recordingParams ?: defaultRecordingParams())
	}

	private fun applyMeetingRoomParams(meetingRoomParams: MeetingRoomParams) {
		parameters.meetingRoomParams = meetingRoomParams
		parameters.itemPageLimit = meetingRoomParams.itemPageLimit
		parameters.audioSetting = meetingRoomParams.audioSetting
		parameters.videoSetting = meetingRoomParams.videoSetting
		parameters.screenshareSetting = meetingRoomParams.screenshareSetting
		parameters.chatSetting = meetingRoomParams.chatSetting
		parameters.addForBasic = meetingRoomParams.addCoHost
		parameters.targetOrientation = meetingRoomParams.targetOrientation
		parameters.targetResolution = meetingRoomParams.targetResolution
		parameters.targetResolutionHost = meetingRoomParams.targetResolutionHost
		parameters.audioOnlyRoom = meetingRoomParams.mediaType.equals("audio", ignoreCase = true)
		parameters.meetingVideoOptimized = meetingRoomParams.mediaType.equals("video", ignoreCase = true)
	}

	private fun applyRecordingParams(recordingParams: RecordingParams) {
		parameters.updateRecordingAudioPausesLimit(recordingParams.recordingAudioPausesLimit)
		parameters.updateRecordingAudioSupport(recordingParams.recordingAudioSupport)
		parameters.updateRecordingAudioPeopleLimit(recordingParams.recordingAudioPeopleLimit)
		parameters.updateRecordingAudioParticipantsTimeLimit(recordingParams.recordingAudioParticipantsTimeLimit)
		parameters.updateRecordingVideoPausesLimit(recordingParams.recordingVideoPausesLimit)
		parameters.updateRecordingVideoSupport(recordingParams.recordingVideoSupport)
		parameters.updateRecordingVideoPeopleLimit(recordingParams.recordingVideoPeopleLimit)
		parameters.updateRecordingVideoParticipantsTimeLimit(recordingParams.recordingVideoParticipantsTimeLimit)
		parameters.updateRecordingAllParticipantsSupport(recordingParams.recordingAllParticipantsSupport)
		parameters.updateRecordingVideoParticipantsSupport(recordingParams.recordingVideoParticipantsSupport)
		parameters.updateRecordingAllParticipantsFullRoomSupport(recordingParams.recordingAllParticipantsFullRoomSupport)
		parameters.updateRecordingVideoParticipantsFullRoomSupport(recordingParams.recordingVideoParticipantsFullRoomSupport)
		parameters.updateRecordingPreferredOrientation(recordingParams.recordingPreferredOrientation)
		parameters.updateRecordingSupportForOtherOrientation(recordingParams.recordingSupportForOtherOrientation)
		parameters.updateRecordingMultiFormatsSupport(recordingParams.recordingMultiFormatsSupport)
		parameters.updateRecordingAddHls(recordingParams.recordingHlsSupport)
		recordingParams.recordingAudioPausesCount?.let { parameters.updateRecordingAudioPausesCount(it) }
		recordingParams.recordingVideoPausesCount?.let { parameters.updateRecordingVideoPausesCount(it) }
		parameters.updatePauseLimit(recordingParams.recordingAudioPausesLimit)
		parameters.updatePauseRecordCount(recordingParams.recordingAudioPausesCount ?: parameters.pauseRecordCount)
		parameters.updateCanPauseResume(recordingParams.recordingAudioPausesLimit > 0 || recordingParams.recordingVideoPausesLimit > 0)
		parameters.updateCanLaunchRecord(recordingParams.recordingAudioSupport || recordingParams.recordingVideoSupport)
		parameters.updateStopLaunchRecord(false)
	}

	// Expose socket instance if needed by advanced callers
	fun socketManager() = socket
	
	// Expose parameters for advanced usage
	fun getParameters() = parameters

	// Join helpers wrapping emit methods
	suspend fun joinRoom(
		roomName: String,
		islevel: String,
		member: String,
		sec: String,
		apiUserName: String
	): Result<ResponseJoinRoom> {
		val options = JoinRoomOptions(
			socket = socket,
			roomName = roomName,
			islevel = islevel,
			member = member,
			sec = sec,
			apiUserName = apiUserName
		)
		return com.mediasfu.sdk.socket.joinRoom(options)
	}

	suspend fun joinConRoom(
		roomName: String,
		islevel: String,
		member: String,
		sec: String,
		apiUserName: String
	): Result<ResponseJoinRoom> {
		val options = JoinConRoomOptions(
			socket = socket,
			roomName = roomName,
			islevel = islevel,
			member = member,
			sec = sec,
			apiUserName = apiUserName
		)
		return com.mediasfu.sdk.socket.joinConRoom(options)
	}

	suspend fun joinLocalRoom(
		roomName: String,
		islevel: String,
		member: String,
		sec: String,
		apiUserName: String
	): Result<ResponseJoinLocalRoom> {
		val options = JoinLocalRoomOptions(
			socket = socket,
			roomName = roomName,
			islevel = islevel,
			member = member,
			sec = sec,
			apiUserName = apiUserName
		)
		return com.mediasfu.sdk.socket.joinLocalRoom(options)
	}

	suspend fun joinEventRoom(
		parameters: JoinEventRoomParameters
	): Result<CreateJoinLocalRoomResponse> {
		val options = JoinEventRoomOptions(
			socket = socket,
			parameters = parameters
		)
		return com.mediasfu.sdk.socket.joinEventRoom(options)
	}

	suspend fun createLocalRoom(
		parameters: CreateLocalRoomParameters
	): Result<CreateJoinLocalRoomResponse> {
		val options = CreateLocalRoomOptions(
			socket = socket,
			parameters = parameters
		)
		return com.mediasfu.sdk.socket.createLocalRoom(options)
	}
	
	// Consumer method helpers
	suspend fun connectToRemoteIPs(
		remoteIPs: List<String>,
		apiUserName: String,
		apiToken: String,
		apiKey: String? = null
	): Result<com.mediasfu.sdk.consumers.ConnectIpsResult> {
		val options = ConnectIpsOptions(
			consumeSockets = parameters.consumeSockets,
			remIP = remoteIPs,
			apiUserName = apiUserName,
			apiKey = apiKey,
			apiToken = apiToken,
			parameters = parameters,
			closedProducerMethod = ::handleProducerClosed
		)
		
		return connectIps(options)
	}

	private suspend fun handleProducerClosed(options: ConnectIpsProducerClosedOptions) {
		val resolved = SocketProducerClosedOptions(
			remoteProducerId = options.remoteProducerId,
			parameters = EngineProducerClosedParameters(parameters)
		)
		socketProducerClosed(resolved)
	}
	
	// WebRTC transport helpers
	suspend fun createSendTransport(
		option: String, // 'audio', 'video', 'screen', or 'all'
		audioConstraints: Map<String, Any>? = null,
		videoConstraints: Map<String, Any>? = null
	): Result<Unit> {
		val transportParams = connectParameters()
		val options = CreateSendTransportOptions(
			option = option,
			parameters = transportParams,
			audioConstraints = audioConstraints,
			videoConstraints = videoConstraints
		)

		return com.mediasfu.sdk.consumers.createSendTransport(options)
	}
	
	// Media transport connection helpers (simplified for now)
	suspend fun connectAudioTransport(
		stream: Any? = null,
		audioConstraints: Map<String, Any>? = null,
		targetOption: String = "all"
	): Result<Unit> {
		val mediaStream = when (stream) {
			is MediaStream -> stream
			null -> parameters.localStreamAudio
			else -> null
		}

		val options = ConnectSendTransportAudioOptions(
			stream = mediaStream,
			parameters = connectParameters(),
			audioConstraints = audioConstraints,
			targetOption = targetOption
		)

		return connectSendTransportAudio(options)
	}
	
	suspend fun connectVideoTransport(
		videoParams: Any? = null,
		videoConstraints: Map<String, Any>? = null,
		targetOption: String = "all"
	): Result<Unit> {
		val producerOptions = when (videoParams) {
			is ProducerOptionsType -> {
				parameters.vParams = videoParams
				videoParams
			}
			null -> parameters.vParams
			else -> parameters.vParams
		}

		return if (producerOptions != null) {
			val options = ConnectSendTransportVideoOptions(
				videoParams = producerOptions,
				parameters = connectParameters(),
				videoConstraints = videoConstraints,
				targetOption = targetOption
			)
			connectSendTransportVideo(options)
		} else {
			Result.failure(IllegalStateException("Video parameters are not available"))
		}
	}
	
	suspend fun connectScreenTransport(
		stream: Any? = null,
		targetOption: String = "all"
	): Result<Unit> {
		val mediaStream = when (stream) {
			is MediaStream -> stream
			null -> parameters.localStreamScreen
			else -> null
		}

		val options = ConnectSendTransportScreenOptions(
			stream = mediaStream,
			parameters = connectParameters(),
			targetOption = targetOption
		)

		return connectSendTransportScreen(options)
	}
	
	// Additional consumer methods (simplified for now)
	suspend fun connectReceiveTransport(
		consumer: com.mediasfu.sdk.webrtc.WebRtcConsumer,
		consumerTransport: com.mediasfu.sdk.webrtc.WebRtcTransport,
		remoteProducerId: String,
		serverConsumerTransportId: String
	): Result<Unit> {
		val socket = parameters.socket ?: return Result.failure(
			Exception("Socket not available")
		)
		
		val connectOptions = com.mediasfu.sdk.consumers.ConnectRecvTransportOptions(
			consumer = consumer,
			consumerTransport = consumerTransport,
			remoteProducerId = remoteProducerId,
			serverConsumerTransportId = serverConsumerTransportId,
			nsock = socket,
			parameters = EngineConnectRecvTransportParameters(parameters)
		)
		
		com.mediasfu.sdk.consumers.connectRecvTransport(connectOptions)
		return Result.success(Unit)
	}
	
	suspend fun resumeConsumer(
		stream: com.mediasfu.sdk.webrtc.MediaStream?,
		consumer: com.mediasfu.sdk.webrtc.WebRtcConsumer?,
		kind: String, // 'audio' | 'video'
		remoteProducerId: String
	): Result<Unit> {
		val socket = parameters.socket ?: return Result.failure(
			Exception("Socket not available")
		)
		
		val consumerResumeParams = createConsumerResumeParameters(parameters)
		
		val resumeOptions = com.mediasfu.sdk.consumers.ConsumerResumeOptions(
			stream = stream,
			consumer = consumer,
			kind = kind,
			remoteProducerId = remoteProducerId,
			nsock = socket,
			parameters = consumerResumeParams
		)
		
		return try {
			com.mediasfu.sdk.consumers.consumerResume(resumeOptions)
			Result.success(Unit)
		} catch (e: Exception) {
			Result.failure(e)
		}
	}
	
	suspend fun controlParticipantMedia(
		participantId: String,
		participantName: String,
		type: String, // 'audio' | 'video' | 'screenshare' | 'all'
		coHost: String = "",
		roomName: String = ""
	): Result<Unit> {
		val controlOptions = com.mediasfu.sdk.consumers.ControlMediaOptions(
			participantId = participantId,
			participantName = participantName,
			type = type,
			socket = parameters.socket,
			coHostResponsibility = parameters.coHostResponsibility,
			participants = parameters.participants,
			member = parameters.member,
			islevel = parameters.islevel,
			showAlert = parameters.showAlertHandler,
			coHost = coHost.ifEmpty { parameters.coHost },
			roomName = roomName.ifEmpty { parameters.roomName }
		)
		
		com.mediasfu.sdk.consumers.controlMedia(controlOptions)
		return Result.success(Unit)
	}
	
	suspend fun prepopulateMedia(
		mediaType: String, // 'audio' | 'video' | 'screen' | 'all'
		constraints: Map<String, Any>? = null,
		targetOption: String = "all"
	): Result<Unit> {
		// Map to PrepopulateUserMediaOptions
		val name = parameters.member
		val options = PrepopulateUserMediaOptions(
			name = name,
			parameters = EnginePrepopulateUserMediaParameters(parameters)
		)

		return runCatching {
			prepopulateUserMedia(options)
		}.fold({ Result.success(Unit) }, { Result.failure(it) })
	}
	
	// Stream management methods
	suspend fun resumePauseStreams(
		participants: List<Participant> = parameters.participants,
		dispActiveNames: List<String> = parameters.dispActiveNames,
		consumerTransports: List<WebRtcTransport> = parameters.consumerTransportsWebRtc,
		screenId: String? = parameters.screenId.ifEmpty { null },
		islevel: String = parameters.islevel
	): Result<Unit> {
		parameters.updateParticipants(participants)
		parameters.dispActiveNames = dispActiveNames
		parameters.consumerTransportsWebRtc = consumerTransports
		parameters.screenId = screenId ?: ""
		parameters.updateIslevel(islevel)

		val resumeOptions = ResumePauseStreamsOptions(
			parameters = EngineResumePauseStreamsParameters(parameters),
			socket = socket
		)

		return com.mediasfu.sdk.consumers.resumePauseStreams(resumeOptions)
	}
	
	suspend fun reorderStreams(
		add: Boolean = true,
		screenChanged: Boolean = false,
		streams: List<Stream> = parameters.newLimitedStreams
	): Result<Unit> {
		val reorderOptions = ReorderStreamsOptions(
			add = add,
			screenChanged = screenChanged,
			parameters = EngineReorderStreamsParameters(parameters),
			streams = streams
		)
		return com.mediasfu.sdk.consumers.reorderStreams(reorderOptions)
	}
	
	// Screen sharing methods
	suspend fun startScreenSharing(
		targetWidth: Int = 1920,
		targetHeight: Int = 1080
	): Result<Unit> {
		parameters.shared = false
		
		val startOptions = com.mediasfu.sdk.consumers.StartShareScreenOptions(
			parameters = EngineStartShareScreenParameters(parameters),
			targetWidth = targetWidth,
			targetHeight = targetHeight
		)
		
		return com.mediasfu.sdk.consumers.startShareScreen(startOptions)
	}
	
	suspend fun stopScreenSharing(): Result<Unit> {
		// Direct delegation approach to avoid interface inheritance conflicts
		// Manually update state instead of using complex adapter
		parameters.shared = false
		parameters.shareScreenStarted = false
		parameters.shareEnded = true
		parameters.updateMainWindow = true
		
		// Stop local stream if it exists
		parameters.localStreamScreen?.let {
			// Platform-specific stream cleanup would go here
			parameters.localStreamScreen = null
		}
		
		// Disconnect send transport screen using existing method
		try {
			val disconnectParams = object : com.mediasfu.sdk.consumers.DisconnectSendTransportScreenParameters {
				override val screenProducer: com.mediasfu.sdk.webrtc.WebRtcProducer? = parameters.screenProducer
				override val socket: com.mediasfu.sdk.socket.SocketManager? = parameters.socket
				override val localScreenProducer: com.mediasfu.sdk.webrtc.WebRtcProducer? = parameters.localScreenProducer
				override val localSocket: com.mediasfu.sdk.socket.SocketManager? = parameters.localSocket
				override val roomName: String = parameters.roomName
				override fun updateScreenProducer(producer: com.mediasfu.sdk.webrtc.WebRtcProducer?) { parameters.screenProducer = producer }
				override fun updateLocalScreenProducer(producer: com.mediasfu.sdk.webrtc.WebRtcProducer?) { parameters.localScreenProducer = producer }
				override fun getUpdatedAllParams() = this
			}
			com.mediasfu.sdk.consumers.disconnectSendTransportScreen(
				com.mediasfu.sdk.consumers.DisconnectSendTransportScreenOptions(disconnectParams)
			)
		} catch (e: Exception) {
			Logger.e("MediaSfuEngine", "Error disconnecting screen transport: ${e.message}")
		}
		
		// Handle screen annotation
		if (parameters.annotateScreenStream) {
			parameters.annotateScreenStream = false
			parameters.isScreenboardModalVisible = true
			kotlinx.coroutines.delay(500)
			parameters.isScreenboardModalVisible = false
		}
		
		// Update mainHeightWidth for conference events
		if (parameters.eventType?.toString()?.contains("conference", ignoreCase = true) == true) {
			parameters.mainHeightWidth = 0.0
		}
		
		// Prepopulate user media to refresh display
		try {
			prepopulateMedia(mediaType = "all")
		} catch (e: Exception) {
			Logger.e("MediaSfuEngine", "Error in prepopulateUserMedia: ${e.message}")
		}
		
		// Reorder streams with screen change flag
		try {
			reorderStreams(add = false, screenChanged = true)
		} catch (e: Exception) {
			Logger.e("MediaSfuEngine", "Error in reorderStreams: ${e.message}")
		}
		
		// Reset UI states
		parameters.lockScreen = false
		parameters.forceFullDisplay = parameters.prevForceFullDisplay
		parameters.firstAll = false
		parameters.firstRound = false
		
		return Result.success(Unit)
	}
	
	// Grid and layout methods
	@Suppress("UNUSED_PARAMETER")
	suspend fun addVideosToGrid(
		participants: List<Participant> = parameters.participants,
		streams: List<Stream> = parameters.lStreams,
		forceUpdate: Boolean = false
	): Result<Unit> {
		if (participants.isNotEmpty()) {
			parameters.refParticipants = participants
		} else if (parameters.refParticipants.isEmpty()) {
			parameters.refParticipants = parameters.participants
		}

		val resolvedStreams = if (streams.isNotEmpty()) {
			parameters.updateLStreams(streams)
			streams
		} else {
			parameters.lStreams
		}
		parameters.chatRefStreams = resolvedStreams

		val refLength = resolvedStreams.size
		val estimate = getEstimate(
			GetEstimateOptions(
				n = refLength,
				parameters = EngineGetEstimateParameters(parameters)
			)
		)

		val estimatedRows = estimate.getOrNull(1) ?: 0
		val estimatedCols = estimate.getOrNull(2) ?: 0

		val gridCheck = checkGrid(
			CheckGridOptions(
				rows = estimatedRows,
				cols = estimatedCols,
				actives = refLength
			)
		)

		parameters.removeAltGrid = gridCheck.removeAltGrid

		val maxMainItems = when {
			refLength == 0 -> 0
			gridCheck.numToAdd > 0 -> gridCheck.numToAdd.coerceAtMost(refLength)
			estimatedRows > 0 && estimatedCols > 0 -> (estimatedRows * estimatedCols).coerceAtMost(refLength)
			else -> refLength
		}

		val mainGridStreams = resolvedStreams.take(maxMainItems)
		val altGridStreams = if (gridCheck.removeAltGrid) {
			emptyList()
		} else {
			resolvedStreams.drop(maxMainItems)
		}

		val options = AddVideosGridOptions(
			mainGridStreams = mainGridStreams,
			altGridStreams = altGridStreams,
			numRows = (if (gridCheck.numRows > 0) gridCheck.numRows else estimatedRows).coerceAtLeast(0),
			numCols = (if (gridCheck.numCols > 0) gridCheck.numCols else estimatedCols).coerceAtLeast(0),
			actualRows = (if (gridCheck.actualRows > 0) gridCheck.actualRows else estimatedRows).coerceAtLeast(0),
			lastRowCols = (if (gridCheck.lastRowCols > 0) gridCheck.lastRowCols else estimatedCols).coerceAtLeast(0),
			removeAltGrid = gridCheck.removeAltGrid,
			parameters = EngineAddVideosGridParameters(parameters)
		)

		return runCatching { addVideosGrid(options) }
	}
	
	suspend fun updateGridLayout(
		rows: Int = 2,
		columns: Int = 2,
		forceFullDisplay: Boolean = false
	): Result<Unit> {
		parameters.gridRows = rows
		parameters.gridCols = columns
		parameters.forceFullDisplay = forceFullDisplay

		// Use calculateRowsAndColumns if dynamic calculation is needed
		return Result.success(Unit)
	}
	
	// Additional consumer methods
	suspend fun autoAdjustLayout(
		n: Int,
		eventType: Any? = null,
		shareScreenStarted: Boolean = false,
		shared: Boolean = false
	): Result<List<Int>> {
		val options = AutoAdjustOptions(
			n = n,
			eventType = eventType,
			shareScreenStarted = shareScreenStarted,
			shared = shared
		)
		return autoAdjust(options)
	}
	
	fun calculateGridDimensions(n: Int): Result<List<Int>> {
		val options = CalculateRowsAndColumnsOptions(n = n)
		return calculateRowsAndColumns(options)
	}
	
	suspend fun checkMediaPermission(
		permissionType: String,
		audioSetting: String = "allow",
		videoSetting: String = "allow",
		screenshareSetting: String = "approval",
		chatSetting: String = "allow"
	): Result<Int> {
		val options = CheckPermissionOptions(
			audioSetting = audioSetting,
			videoSetting = videoSetting,
			screenshareSetting = screenshareSetting,
			chatSetting = chatSetting,
			permissionType = permissionType
		)
		return try {
			Result.success(checkPermission(options))
		} catch (error: CheckPermissionException) {
			Result.failure(error)
		}
	}
	
	suspend fun processVideoStreams(
		participants: List<Participant> = emptyList(),
		allVideoStreams: List<Stream> = emptyList(),
		oldAllStreams: List<Stream> = emptyList(),
		adminVidID: String? = null
	): Result<Unit> {
		val options = GetVideosOptions(
			participants = participants,
			allVideoStreams = allVideoStreams,
			oldAllStreams = oldAllStreams,
			adminVidID = adminVidID,
			updateAllVideoStreams = { /* no-op */ },
			updateOldAllStreams = { /* no-op */ }
		)
		return getVideos(options)
	}
	
	suspend fun mixVideoStreams(
		alVideoStreams: List<Stream> = emptyList(),
		nonAlVideoStreams: List<Stream> = emptyList(),
		refParticipants: List<Participant> = emptyList()
	): Result<List<Any>> {
		val options = MixStreamsOptions(
			alVideoStreams = alVideoStreams,
			nonAlVideoStreams = nonAlVideoStreams,
			refParticipants = refParticipants
		)
		return mixStreams(options)
	}
	
	suspend fun triggerScreenUpdate(
		refActiveNames: List<String> = parameters.activeNames,
		roomName: String = parameters.roomName,
		eventType: com.mediasfu.sdk.model.EventType = parameters.eventType,
		shared: Boolean = parameters.shared,
		shareScreenStarted: Boolean = parameters.shareScreenStarted,
		whiteboardStarted: Boolean = parameters.whiteboardStarted,
		whiteboardEnded: Boolean = parameters.whiteboardEnded
	): Result<Unit> {
		parameters.activeNames = refActiveNames
		parameters.roomName = roomName
		parameters.eventType = eventType
		parameters.shared = shared
		parameters.shareScreenStarted = shareScreenStarted
		parameters.whiteboardStarted = whiteboardStarted
		parameters.whiteboardEnded = whiteboardEnded

		val triggerOptions = TriggerOptions(
			refActiveNames = refActiveNames,
			parameters = EngineTriggerParameters(parameters)
		)

		return trigger(triggerOptions)
	}

	suspend fun compareScreenStates(
		restart: Boolean = false,
		screenStates: List<ScreenState> = parameters.screenStates,
		prevScreenStates: List<ScreenState> = parameters.prevScreenStates,
		activeNames: List<String> = parameters.activeNames
	): Result<Unit> {
		parameters.screenStates = screenStates
		parameters.prevScreenStates = prevScreenStates
		parameters.activeNames = activeNames

		val options = CompareScreenStatesOptions(
			restart = restart,
			parameters = EngineCompareScreenStatesParameters(parameters)
		)

		return runCatching { compareScreenStates(options) }
	}
}

interface Platform {
	val name: String
}

expect fun getPlatform(): Platform

// Parameter adapter implementations moved to EngineParameterAdapters.kt.

