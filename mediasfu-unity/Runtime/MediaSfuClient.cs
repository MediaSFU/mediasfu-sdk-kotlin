using System;
using System.Collections.Generic;
using System.Globalization;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Net.Security;
using System.Net.WebSockets;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Threading.Tasks;
using MediaSFU.Unity.Json;

namespace MediaSFU.Unity
{
    /// <summary>
    /// Contract-first Unity facade for MediaSFU room, socket, and media operations.
    ///
    /// The Unity facade now has live room, socket, and transport-backed media
    /// orchestration paths. Operations still return deferred results when the
    /// caller has not attached the backend-specific local media or WebRTC device
    /// required for that slice.
    /// </summary>
    public sealed class MediaSfuClient : IDisposable
    {
        private const string DefaultCloudBaseUrl = "https://mediasfu.com";
        private const string DefaultCloudRoomsEndpoint = DefaultCloudBaseUrl + "/v1/rooms";
        private const int DefaultRequestCooldownSeconds = 240;
        private const string DeferredContractMessage =
            "Unity room and Socket.IO runtime behavior are implemented. This call stays deferred until the required local media backend or WebRTC device is attached for the requested media slice.";
        private static readonly Regex AlphanumericPattern = new Regex("^[a-zA-Z0-9_]+$", RegexOptions.Compiled);
        private static readonly Regex ParticipantKeyPattern = new Regex("[^a-zA-Z0-9]+", RegexOptions.Compiled);
        private static readonly string[] CoHostResponsibilityNames =
        {
            "participants",
            "media",
            "waiting",
            "chat"
        };
        private static readonly HttpClient SharedHttpClient = new HttpClient();
        private static readonly SemaphoreSlim ServerCertificateValidationCallbackGate = new SemaphoreSlim(1, 1);
        private readonly HttpClient _httpClient;
        private readonly bool _ownsHttpClient;
        private readonly SemaphoreSlim _socketSendLock = new SemaphoreSlim(1, 1);
            private readonly SemaphoreSlim _consumeSessionLock = new SemaphoreSlim(1, 1);
            private Task _consumeSessionPrimeTask;
        private readonly object _pendingSocketAcksGate = new object();
        private readonly object _knownRemoteProducerIdsGate = new object();
        private readonly Dictionary<int, TaskCompletionSource<string>> _pendingSocketAcks =
            new Dictionary<int, TaskCompletionSource<string>>();
        private readonly HashSet<string> _knownRemoteProducerIds =
            new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        private readonly HashSet<string> _screenProducerHintIds =
            new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        private readonly HashSet<string> _screenProducerHintParticipantNames =
            new HashSet<string>(StringComparer.OrdinalIgnoreCase);

        private bool _disposed;
        private bool _consumeRoomJoined;
        private int _nextSocketAckId;
        private string _lastLocalUserName = string.Empty;
        private string _lastLocalIsLevel = string.Empty;
        private string _lastConsumeSocketInboundPacket = string.Empty;
        private string _lastConsumeSocketOutboundPacket = string.Empty;
        private bool _suppressConfirmHere;
        private SocketTransportSession _consumeSocketTransportSession;
        private SocketTransportSession _socketTransportSession;

        public MediaSfuClient(MediaSfuClientOptions options)
        {
            Options = options ?? throw new ArgumentNullException(nameof(options));
            _httpClient = CreateHttpClient(Options, out var ownsHttpClient);
            _ownsHttpClient = ownsHttpClient;
        }

        public MediaSfuClientOptions Options { get; }

        public IMediaSfuLocalMediaBackend LocalMediaBackend { get; private set; }

        public IMediaSfuRemoteMediaBridge RemoteMediaBridge { get; private set; }

        public IMediaSfuWebRtcDevice WebRtcDevice { get; private set; }

        public MediaSfuConnectionState ConnectionState { get; private set; } = MediaSfuConnectionState.Idle;

        public MediaSfuRoom CurrentRoom { get; private set; }

        public MediaSfuSocketConnectionPlan LastSocketConnectionPlan { get; private set; }

        public MediaSfuSocketHandshake LastSocketHandshake { get; private set; }

        public MediaSfuRoomValidation LastRoomValidation { get; private set; }

        public event Action<MediaSfuConnectionStateChangedEvent> ConnectionStateChanged;

        public event Action<MediaSfuRoomChangedEvent> RoomChanged;

        public event Action<MediaSfuParticipantEvent> ParticipantJoined;

        public event Action<MediaSfuParticipantEvent> ParticipantLeft;

        public event Action<MediaSfuTrackEvent> TrackAdded;

        public event Action<MediaSfuTrackEvent> TrackRemoved;

        public event Action<MediaSfuSocketEvent> SocketEventReceived;

        public event Action<MediaSfuChatMessage> MessageReceived;

        public event Action<MediaSfuRemoteProducerEvent> RemoteProducerAvailable;

        public event Action<MediaSfuRemoteProducerClosedEvent> RemoteProducerClosed;

        public event Action<MediaSfuErrorEvent> ErrorOccurred;

        public void AttachLocalMediaBackend(IMediaSfuLocalMediaBackend localMediaBackend)
        {
            EnsureNotDisposed();
            if (!ReferenceEquals(localMediaBackend, WebRtcDevice))
            {
                WebRtcDevice = null;
            }

            LocalMediaBackend = localMediaBackend;
        }

        public void AttachRemoteMediaBridge(IMediaSfuRemoteMediaBridge remoteMediaBridge)
        {
            EnsureNotDisposed();
            if (!ReferenceEquals(remoteMediaBridge, WebRtcDevice))
            {
                WebRtcDevice = null;
            }

            RemoteMediaBridge = remoteMediaBridge;
        }

        public void AttachWebRtcDevice(IMediaSfuWebRtcDevice webRtcDevice)
        {
            EnsureNotDisposed();
            WebRtcDevice = webRtcDevice;
            LocalMediaBackend = webRtcDevice;
            RemoteMediaBridge = webRtcDevice;
        }

        public async Task<MediaSfuOperationResult<MediaSfuRoom>> CreateRoomAsync(
            MediaSfuCreateRoomRequest request,
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();

            if (request == null)
            {
                throw new ArgumentNullException(nameof(request));
            }

            ValidateClientConfiguration();

            if (string.IsNullOrWhiteSpace(request.UserName))
            {
                return MediaSfuOperationResult<MediaSfuRoom>.FromFailure("CreateRoom requires a non-empty UserName.");
            }

            var result = await ExecuteRoomOperationAsync(
                MediaSfuConnectionState.CreatingRoom,
                "CreateRoom",
                ResolveCreateEndpoint(),
                BuildCreatePayload(request),
                request.UserName,
                string.IsNullOrWhiteSpace(request.IsLevel) ? "2" : request.IsLevel,
                cancellationToken).ConfigureAwait(false);

            if (result.Success &&
                result.Value != null &&
                request.RecordingParameters != null &&
                !HasRecordingSupport(result.Value.RecordingParameters) &&
                HasRecordingSupport(request.RecordingParameters))
            {
                ApplyRecordingParameters(result.Value, request.RecordingParameters);
            }

            return result;
        }

        public async Task<MediaSfuOperationResult<MediaSfuRoom>> JoinRoomAsync(
            MediaSfuJoinRoomRequest request,
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();

            if (request == null)
            {
                throw new ArgumentNullException(nameof(request));
            }

            ValidateClientConfiguration();

            if (string.IsNullOrWhiteSpace(request.MeetingId) || string.IsNullOrWhiteSpace(request.UserName))
            {
                return MediaSfuOperationResult<MediaSfuRoom>.FromFailure(
                    "JoinRoom requires both MeetingId and UserName.");
            }

            return await ExecuteRoomOperationAsync(
                MediaSfuConnectionState.JoiningRoom,
                "JoinRoom",
                ResolveJoinEndpoint(),
                BuildJoinPayload(request),
                request.UserName,
                request.IsLevel,
                cancellationToken).ConfigureAwait(false);
        }

        public async Task<MediaSfuOperationResult<bool>> ConnectMediaAsync(CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();

            if (CurrentRoom == null || string.IsNullOrWhiteSpace(CurrentRoom.RoomName))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "ConnectMediaAsync requires a room from CreateRoomAsync or JoinRoomAsync before socket validation can begin.");
            }

            try
            {
                SetConnectionState(MediaSfuConnectionState.ValidatingRoom);
                var connectionTarget = ResolveSocketConnectionTarget();
                var connectionPlan = BuildSocketConnectionPlan(connectionTarget);
                LastSocketConnectionPlan = connectionPlan;

                SetConnectionState(MediaSfuConnectionState.ConnectingSocket);
                var handshake = await ProbeSocketHandshakeAsync(connectionTarget, cancellationToken).ConfigureAwait(false);
                LastSocketHandshake = handshake;
                await CloseSocketTransportAsync(markDisconnected: false).ConfigureAwait(false);

                var transportSession = await ConnectSocketTransportAsync(connectionTarget, handshake, cancellationToken)
                    .ConfigureAwait(false);
                _socketTransportSession = transportSession;
                transportSession.ReceiveLoopTask = Task.Run(
                    () => ReceiveSocketPacketsAsync(transportSession),
                    transportSession.ReceiveLoopCancellation.Token);
                var roomValidation = await ValidateJoinedRoomAsync(transportSession, cancellationToken).ConfigureAwait(false);
                LastRoomValidation = roomValidation;
                SetConnectionState(roomValidation == null ? MediaSfuConnectionState.Connected : MediaSfuConnectionState.InRoom);

                var detail =
                    roomValidation == null
                        ? $"Socket transport connected: sid={handshake.SessionId} polling={handshake.TransportUrl} websocket={transportSession.TransportUrl} namespace={connectionPlan.Namespace} room={connectionPlan.RoomName}."
                        : $"Room validation acknowledged: sid={handshake.SessionId} websocket={transportSession.TransportUrl} room={connectionPlan.RoomName} hasRtpCapabilities={roomValidation.HasRtpCapabilities}.";
                return MediaSfuOperationResult<bool>.FromSuccess(true, detail);
            }
            catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
            {
                await CloseSocketTransportAsync(markDisconnected: false).ConfigureAwait(false);
                SetConnectionState(MediaSfuConnectionState.Idle);
                throw;
            }
            catch (Exception error)
            {
                await CloseSocketTransportAsync(markDisconnected: false).ConfigureAwait(false);
                LastSocketHandshake = null;
                LastRoomValidation = null;
                SetConnectionState(MediaSfuConnectionState.Failed);
                PublishError("ConnectMedia", error.Message, error.ToString());
                return MediaSfuOperationResult<bool>.FromFailure(
                    $"ConnectMedia failed: {error.Message}",
                    error.ToString());
            }
        }

        public async Task<MediaSfuOperationResult<bool>> LeaveRoomAsync(CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();

            var currentRoom = CurrentRoom;
            if (currentRoom != null &&
                !string.IsNullOrWhiteSpace(currentRoom.RoomName) &&
                !string.IsNullOrWhiteSpace(_lastLocalUserName))
            {
                var session = _socketTransportSession;
                if (session?.Socket != null && session.Socket.State == WebSocketState.Open)
                {
                    try
                    {
                        await SendSocketEventAsync(
                                session,
                                "disconnectUser",
                                new Dictionary<string, object>
                                {
                                    ["member"] = _lastLocalUserName,
                                    ["roomName"] = currentRoom.RoomName,
                                    ["ban"] = false
                                },
                                cancellationToken)
                            .ConfigureAwait(false);
                    }
                    catch (Exception error)
                    {
                        PublishError("LeaveRoom", error.Message, error.ToString());
                    }
                }
            }

            await CloseSocketTransportAsync().ConfigureAwait(false);
            ClearCurrentRoom();
            SetConnectionState(MediaSfuConnectionState.Idle);
            return MediaSfuOperationResult<bool>.FromSuccess(
                true,
                "Socket transport closed locally and disconnectUser was emitted when a live session was available.");
        }

        public async Task<MediaSfuOperationResult<bool>> EndMeetingAsync(CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();

            var currentRoom = CurrentRoom;
            if (currentRoom == null || string.IsNullOrWhiteSpace(currentRoom.RoomName))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "EndMeetingAsync requires a room from CreateRoomAsync or JoinRoomAsync.");
            }

            if (!IsLocalParticipantHost())
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "EndMeetingAsync requires the local participant to be the host.");
            }

            if (string.IsNullOrWhiteSpace(_lastLocalUserName))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "EndMeetingAsync requires a resolved local user name.");
            }

            var session = _socketTransportSession;
            if (session?.Socket == null || session.Socket.State != WebSocketState.Open)
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "EndMeetingAsync requires an active socket connection from ConnectMediaAsync.");
            }

            try
            {
                await SendSocketEventAsync(
                        session,
                        "disconnectUser",
                        new Dictionary<string, object>
                        {
                            ["member"] = _lastLocalUserName,
                            ["roomName"] = currentRoom.RoomName,
                            ["ban"] = false
                        },
                        cancellationToken)
                    .ConfigureAwait(false);

                await CloseSocketTransportAsync().ConfigureAwait(false);
                ClearCurrentRoom();
                SetConnectionState(MediaSfuConnectionState.Idle);
                return MediaSfuOperationResult<bool>.FromSuccess(
                    true,
                    "disconnectUser emitted for the host end-meeting flow and the local session was closed.");
            }
            catch (Exception error)
            {
                PublishError("EndMeeting", error.Message, error.ToString());
                return MediaSfuOperationResult<bool>.FromFailure(
                    "EndMeetingAsync failed: " + error.Message,
                    error.ToString());
            }
        }

        public async Task<MediaSfuOperationResult<bool>> SetMicrophoneEnabledAsync(
            bool enabled,
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();

            if (enabled && TryCreateMediaPermissionFailure(MediaSfuTrackKind.Audio, out var permissionFailure))
            {
                return permissionFailure;
            }

            if (enabled && CurrentRoom?.HostRestrictedAudio == true && !HasGrantedLocalRequest(MediaSfuTrackKind.Audio))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "SetMicrophoneEnabled cannot enable audio because the host has restricted local audio. RequestMediaPermissionAsync(MediaSfuTrackKind.Audio) must be approved first.");
            }

                        return await ExecuteLocalMediaOperationAsync(
                                        "SetMicrophoneEnabled",
                                        MediaSfuTrackKind.Audio,
                                        "audio",
                                        enabled,
                                        enabled
                                                ? "Microphone enable requires a Unity local media backend to create or resume an audio producer. " +
                                                    DeferredContractMessage
                                                : null,
                                        enabled ? "resumeProducerAudio" : "pauseProducerMedia",
                                        (backend, request, ct) => backend.SetMicrophoneEnabledAsync(request, ct),
                                        cancellationToken)
                .ConfigureAwait(false);
        }

        public async Task<MediaSfuOperationResult<bool>> SetCameraEnabledAsync(
            bool enabled,
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();

            if (enabled && TryCreateMediaPermissionFailure(MediaSfuTrackKind.Video, out var permissionFailure))
            {
                return permissionFailure;
            }

            if (enabled && CurrentRoom?.HostRestrictedVideo == true && !HasGrantedLocalRequest(MediaSfuTrackKind.Video))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "SetCameraEnabled cannot enable video because the host has restricted local video. RequestMediaPermissionAsync(MediaSfuTrackKind.Video) must be approved first.");
            }

            return await ExecuteLocalMediaOperationAsync(
                    "SetCameraEnabled",
                    MediaSfuTrackKind.Video,
                    "video",
                    enabled,
                    "Camera enable requires a Unity local media backend to create or resume a video producer. " +
                    DeferredContractMessage,
                    "pauseProducerMedia",
                    (backend, request, ct) => backend.SetCameraEnabledAsync(request, ct),
                    cancellationToken)
                .ConfigureAwait(false);
        }

        public async Task<MediaSfuOperationResult<bool>> SetScreenShareEnabledAsync(
            bool enabled,
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();

            if (enabled && TryCreateMediaPermissionFailure(MediaSfuTrackKind.Screen, out var permissionFailure))
            {
                return permissionFailure;
            }

            if (enabled && CurrentRoom?.HostRestrictedScreenshare == true && !HasGrantedLocalRequest(MediaSfuTrackKind.Screen))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "SetScreenShareEnabled cannot enable screen share because the host has restricted local screen sharing. RequestMediaPermissionAsync(MediaSfuTrackKind.Screen) must be approved first.");
            }

            return await ExecuteLocalMediaOperationAsync(
                    "SetScreenShareEnabled",
                    MediaSfuTrackKind.Screen,
                    "screen",
                    enabled,
                    "Screen-share enable requires a Unity local media backend to capture the screen and create a screen producer. " +
                    DeferredContractMessage,
                    "pauseProducerMedia",
                    (backend, request, ct) => backend.SetScreenShareEnabledAsync(request, ct),
                    cancellationToken,
                    "closeScreenProducer")
                .ConfigureAwait(false);
        }

        public async Task<MediaSfuOperationResult<bool>> RequestMediaPermissionAsync(
            MediaSfuTrackKind trackKind,
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();

            if (!TryResolveParticipantRequestMetadata(trackKind, out var requestIcon, out var requestLabel))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "RequestMediaPermissionAsync supports only Audio, Video, and Screen tracks.");
            }

            var session = _socketTransportSession;
            if (session == null || session.Socket == null || session.Socket.State != WebSocketState.Open)
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "RequestMediaPermissionAsync requires an active socket connection from ConnectMediaAsync.");
            }

            if (CurrentRoom == null || string.IsNullOrWhiteSpace(CurrentRoom.RoomName))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "RequestMediaPermissionAsync requires a room from CreateRoomAsync or JoinRoomAsync.");
            }

            var permissionRequirement = GetMediaPermissionRequirement(trackKind);
            if (IsLocalParticipantPermissionExempt())
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "RequestMediaPermissionAsync is not required for the current local role.");
            }

            if (!IsHostRestrictionActive(trackKind) && permissionRequirement != MediaPermissionRequirement.Approval)
            {
                var requestLabelText = requestLabel;
                return MediaSfuOperationResult<bool>.FromFailure(
                    permissionRequirement == MediaPermissionRequirement.Disallow
                        ? "RequestMediaPermissionAsync cannot request " + requestLabelText + " because the room currently disallows it."
                        : "RequestMediaPermissionAsync is only needed when the room requires approval or the host has restricted local " + requestLabelText + ".");
            }

            var localRequestState = CurrentRoom.LocalRequests ?? new MediaSfuLocalRequestState();
            var currentRequestState = GetLocalRequestState(localRequestState, trackKind);
            if (string.Equals(currentRequestState, "pending", StringComparison.OrdinalIgnoreCase))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "RequestMediaPermissionAsync cannot send another " + requestLabel + " request while one is already pending.");
            }

            var retryAtEpochMs = GetLocalRequestRetryAtEpochMs(localRequestState, trackKind);
            var nowEpochMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            if (string.Equals(currentRequestState, "rejected", StringComparison.OrdinalIgnoreCase) &&
                retryAtEpochMs.HasValue &&
                retryAtEpochMs.Value > nowEpochMs)
            {
                var retryDelaySeconds = Math.Max(1L, (retryAtEpochMs.Value - nowEpochMs + 999L) / 1000L);
                return MediaSfuOperationResult<bool>.FromFailure(
                    "RequestMediaPermissionAsync cannot send another " + requestLabel +
                    " request yet. Retry in approximately " + retryDelaySeconds + " seconds.");
            }

            var requestId = ResolveParticipantRequestId(session);
            if (string.IsNullOrWhiteSpace(requestId))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "RequestMediaPermissionAsync could not resolve a stable local socket id for the request.");
            }

            var localParticipantName = FindLocalParticipant()?.DisplayName ?? _lastLocalUserName;
            if (string.IsNullOrWhiteSpace(localParticipantName))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "RequestMediaPermissionAsync requires a resolved local participant name.");
            }

            try
            {
                await SendSocketEventAsync(
                        session,
                        "participantRequest",
                        new Dictionary<string, object>
                        {
                            ["userRequest"] = new Dictionary<string, object>
                            {
                                ["id"] = requestId,
                                ["name"] = localParticipantName,
                                ["icon"] = requestIcon
                            },
                            ["roomName"] = CurrentRoom.RoomName
                        },
                        cancellationToken)
                    .ConfigureAwait(false);

                UpdateCurrentRoomState(nextRoom => MarkLocalRequestPending(nextRoom, trackKind));
                return MediaSfuOperationResult<bool>.FromSuccess(
                    true,
                    "participantRequest emitted for " + requestLabel + ".");
            }
            catch (Exception error)
            {
                PublishError("RequestMediaPermission", error.Message, error.ToString());
                return MediaSfuOperationResult<bool>.FromFailure(
                    "RequestMediaPermissionAsync failed: " + error.Message,
                    error.ToString());
            }
        }

        public async Task<MediaSfuOperationResult<bool>> ControlParticipantMediaAsync(
            MediaSfuParticipant participant,
            MediaSfuHostControlType controlType,
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();

            if (participant == null || string.IsNullOrWhiteSpace(participant.ParticipantId))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "ControlParticipantMediaAsync requires a participant with a non-empty ParticipantId.");
            }

            var session = _socketTransportSession;
            if (session == null || session.Socket == null || session.Socket.State != WebSocketState.Open)
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "ControlParticipantMediaAsync requires an active socket connection from ConnectMediaAsync.");
            }

            if (CurrentRoom == null || string.IsNullOrWhiteSpace(CurrentRoom.RoomName))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "ControlParticipantMediaAsync requires a room from CreateRoomAsync or JoinRoomAsync.");
            }

            var normalizedControlType = ResolveHostControlType(controlType);
            try
            {
                await SendSocketEventAsync(
                        session,
                        "controlMedia",
                        new Dictionary<string, object>
                        {
                            ["participantId"] = participant.ParticipantId,
                            ["participantName"] = participant.DisplayName ?? string.Empty,
                            ["type"] = normalizedControlType,
                            ["roomName"] = CurrentRoom.RoomName
                        },
                        cancellationToken)
                    .ConfigureAwait(false);

                return MediaSfuOperationResult<bool>.FromSuccess(
                    true,
                    "controlMedia emitted for " + (participant.DisplayName ?? participant.ParticipantId) +
                    " with type=" + normalizedControlType + ".");
            }
            catch (Exception error)
            {
                PublishError("ControlParticipantMedia", error.Message, error.ToString());
                return MediaSfuOperationResult<bool>.FromFailure(
                    "ControlParticipantMediaAsync failed: " + error.Message,
                    error.ToString());
            }
        }

        public Task<MediaSfuOperationResult<MediaSfuWebRtcTransport>> CreateMediaWebRtcTransportAsync(
            MediaSfuWebRtcTransportRequest request,
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();
            return CreateLocalMediaBackendWebRtcTransportAsync(request, cancellationToken);
        }

        public Task<MediaSfuOperationResult<bool>> ConnectMediaWebRtcTransportAsync(
            MediaSfuTransportConnectRequest request,
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();
            return ConnectLocalMediaBackendWebRtcTransportAsync(request, cancellationToken);
        }

        public Task<MediaSfuOperationResult<MediaSfuProduceResponse>> ProduceMediaTrackAsync(
            MediaSfuProduceRequest request,
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();
            return ProduceLocalMediaBackendTrackAsync(request, cancellationToken);
        }

        public Task<MediaSfuOperationResult<MediaSfuConsumeResponse>> ConsumeMediaTrackAsync(
            MediaSfuConsumeRequest request,
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();
            return ConsumeMediaTrackInternalAsync(request, cancellationToken);
        }

        public Task<MediaSfuOperationResult<bool>> ResumeConsumerAsync(
            string serverConsumerId,
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();
            return SendConsumerControlAsync("consumer-resume", serverConsumerId, cancellationToken);
        }

        public Task<MediaSfuOperationResult<bool>> PauseConsumerAsync(
            string serverConsumerId,
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();
            return SendConsumerControlAsync("consumer-pause", serverConsumerId, cancellationToken);
        }

        public async Task<MediaSfuOperationResult<bool>> StartRecordingAsync(
            MediaSfuUserRecordingParams userRecordingParams = null,
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();

            var recording = CurrentRoom?.Recording ?? new MediaSfuRecordingState();
            if (recording.RecordStarted && !recording.RecordPaused && !recording.RecordStopped)
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "StartRecording cannot start because recording is already active.");
            }

            var resolvedRecordingParams = ResolveRecordingUserParams(userRecordingParams);
            var mediaOptions = ResolveRecordingMediaOptions(resolvedRecordingParams);
            var recordingParameters = CurrentRoom?.RecordingParameters;
            var localParticipant = FindLocalParticipant();

            if (string.Equals(mediaOptions, "video", StringComparison.OrdinalIgnoreCase) &&
                (recordingParameters == null || !recordingParameters.RecordingVideoSupport))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "StartRecording cannot start video recording because this room does not allow video recording.");
            }

            if (string.Equals(mediaOptions, "audio", StringComparison.OrdinalIgnoreCase) &&
                (recordingParameters == null || !recordingParameters.RecordingAudioSupport))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "StartRecording cannot start audio recording because this room does not allow audio recording.");
            }

            if (string.Equals(mediaOptions, "video", StringComparison.OrdinalIgnoreCase) &&
                localParticipant != null &&
                !localParticipant.VideoOn)
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "StartRecording cannot start video recording because local video is off.");
            }

            if (string.Equals(mediaOptions, "audio", StringComparison.OrdinalIgnoreCase) &&
                localParticipant != null &&
                !localParticipant.AudioOn)
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "StartRecording cannot start audio recording because local audio is off.");
            }

            var action = recording.RecordStarted && recording.RecordPaused && !recording.RecordStopped
                ? "resumeRecord"
                : "startRecord";

            return await SendRecordingControlAsync(
                    "StartRecording",
                    action,
                    new Dictionary<string, object>
                    {
                        ["roomName"] = CurrentRoom?.RoomName ?? string.Empty,
                        ["userRecordingParams"] = SerializeUserRecordingParams(resolvedRecordingParams) ?? new Dictionary<string, object>()
                    },
                    (nextRoom, ack) => ApplyRecordingStartState(nextRoom, action, resolvedRecordingParams),
                    cancellationToken)
                .ConfigureAwait(false);
        }

        public async Task<MediaSfuOperationResult<bool>> PauseRecordingAsync(
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();

            var recording = CurrentRoom?.Recording ?? new MediaSfuRecordingState();
            if (!recording.RecordStarted || recording.RecordStopped)
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "PauseRecording requires an active recording.");
            }

            if (recording.RecordPaused)
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "PauseRecording cannot pause because recording is already paused.");
            }

            var pauseLimit = ResolveRecordingPauseLimit(CurrentRoom, ResolveRecordingMediaOptions(recording.UserRecordingParams));
            if (pauseLimit > 0 && recording.PauseCount >= pauseLimit)
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "You have reached the limit of pauses - you can choose to stop recording.");
            }

            return await SendRecordingControlAsync(
                    "PauseRecording",
                    "pauseRecord",
                    new Dictionary<string, object>
                    {
                        ["roomName"] = CurrentRoom?.RoomName ?? string.Empty
                    },
                    ApplyRecordingPauseState,
                    cancellationToken)
                .ConfigureAwait(false);
        }

        public async Task<MediaSfuOperationResult<bool>> ResumeRecordingAsync(
            MediaSfuUserRecordingParams userRecordingParams = null,
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();

            var recording = CurrentRoom?.Recording ?? new MediaSfuRecordingState();
            if (!recording.RecordStarted || !recording.RecordPaused || recording.RecordStopped)
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "ResumeRecording cannot resume because recording is not paused.");
            }

            var resolvedRecordingParams = ResolveRecordingUserParams(userRecordingParams);
            var mediaOptions = ResolveRecordingMediaOptions(resolvedRecordingParams);
            var recordingParameters = CurrentRoom?.RecordingParameters;
            var localParticipant = FindLocalParticipant();

            if (string.Equals(mediaOptions, "video", StringComparison.OrdinalIgnoreCase) &&
                (recordingParameters == null || !recordingParameters.RecordingVideoSupport))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "ResumeRecording cannot resume video recording because this room does not allow video recording.");
            }

            if (string.Equals(mediaOptions, "audio", StringComparison.OrdinalIgnoreCase) &&
                (recordingParameters == null || !recordingParameters.RecordingAudioSupport))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "ResumeRecording cannot resume audio recording because this room does not allow audio recording.");
            }

            if (string.Equals(mediaOptions, "video", StringComparison.OrdinalIgnoreCase) &&
                localParticipant != null &&
                !localParticipant.VideoOn)
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "ResumeRecording cannot resume video recording because local video is off.");
            }

            if (string.Equals(mediaOptions, "audio", StringComparison.OrdinalIgnoreCase) &&
                localParticipant != null &&
                !localParticipant.AudioOn)
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "ResumeRecording cannot resume audio recording because local audio is off.");
            }

            var pauseLimit = ResolveRecordingPauseLimit(CurrentRoom, mediaOptions);
            if (pauseLimit > 0 && recording.PauseCount > pauseLimit)
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "ResumeRecording cannot resume because the room's pause limit has already been exceeded.");
            }

            return await SendRecordingControlAsync(
                    "ResumeRecording",
                    "resumeRecord",
                    new Dictionary<string, object>
                    {
                        ["roomName"] = CurrentRoom?.RoomName ?? string.Empty,
                        ["userRecordingParams"] = SerializeUserRecordingParams(resolvedRecordingParams) ?? new Dictionary<string, object>()
                    },
                    (nextRoom, ack) => ApplyRecordingStartState(nextRoom, "resumeRecord", resolvedRecordingParams),
                    cancellationToken)
                .ConfigureAwait(false);
        }

        public async Task<MediaSfuOperationResult<bool>> StopRecordingAsync(
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();

            var recording = CurrentRoom?.Recording ?? new MediaSfuRecordingState();
            if (!recording.RecordStarted || recording.RecordStopped)
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "StopRecording requires an active recording that has not already stopped.");
            }

            return await SendRecordingControlAsync(
                    "StopRecording",
                    "stopRecord",
                    new Dictionary<string, object>
                    {
                        ["roomName"] = CurrentRoom?.RoomName ?? string.Empty
                    },
                    ApplyRecordingStopState,
                    cancellationToken)
                .ConfigureAwait(false);
        }

        public async Task<MediaSfuOperationResult<bool>> SendChatMessageAsync(
            string message,
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();

            if (string.IsNullOrWhiteSpace(message))
            {
                return MediaSfuOperationResult<bool>.FromFailure("SendChatMessage requires a non-empty message.");
            }

            var session = _socketTransportSession;
            if (session == null || session.Socket == null || session.Socket.State != WebSocketState.Open)
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "SendChatMessage requires an active socket connection from ConnectMediaAsync.");
            }

            if (CurrentRoom == null || string.IsNullOrWhiteSpace(CurrentRoom.RoomName))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "SendChatMessage requires a room from CreateRoomAsync or JoinRoomAsync.");
            }

            if (CurrentRoom.HostRestrictedChat)
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "SendChatMessage cannot send because the host has restricted local chat.");
            }

            var sender = string.IsNullOrWhiteSpace(_lastLocalUserName) ? "tester" : _lastLocalUserName;
            var messagePayload = new Dictionary<string, object>
            {
                ["sender"] = sender,
                ["receivers"] = Array.Empty<string>(),
                ["message"] = message,
                ["timestamp"] = DateTimeOffset.Now.ToString("HH:mm:ss"),
                ["group"] = true
            };

            var payload = new Dictionary<string, object>
            {
                ["messageObject"] = messagePayload,
                ["roomName"] = CurrentRoom.RoomName
            };

            try
            {
                await SendSocketEventAsync(session, "sendMessage", payload, cancellationToken).ConfigureAwait(false);
                return MediaSfuOperationResult<bool>.FromSuccess(true);
            }
            catch (Exception error)
            {
                PublishError("SendChatMessage", error.Message, error.ToString());
                return MediaSfuOperationResult<bool>.FromFailure(
                    $"SendChatMessage failed: {error.Message}",
                    error.ToString());
            }
        }

        public async Task<MediaSfuOperationResult<bool>> RespondToRoomRequestAsync(
            MediaSfuRoomRequest request,
            bool accept,
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();

            if (request == null || string.IsNullOrWhiteSpace(request.RequestId))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "RespondToRoomRequestAsync requires a pending request with a non-empty RequestId.");
            }

            var session = _socketTransportSession;
            if (session == null || session.Socket == null || session.Socket.State != WebSocketState.Open)
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "RespondToRoomRequestAsync requires an active socket connection from ConnectMediaAsync.");
            }

            if (CurrentRoom == null || string.IsNullOrWhiteSpace(CurrentRoom.RoomName))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "RespondToRoomRequestAsync requires a room from CreateRoomAsync or JoinRoomAsync.");
            }

            if (FindPendingRequestIndex(CurrentRoom.PendingRequests, request.RequestId) < 0)
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "RespondToRoomRequestAsync requires the request to exist in CurrentRoom.PendingRequests.");
            }

            try
            {
                var requestResponse = new Dictionary<string, object>
                {
                    ["id"] = request.RequestId,
                    ["type"] = request.Icon,
                    ["action"] = accept ? "accepted" : "rejected"
                };
                AddIfNotBlank(requestResponse, "name", request.DisplayName);

                await SendSocketEventAsync(
                        session,
                        "updateUserofRequestStatus",
                        new Dictionary<string, object>
                        {
                            ["requestResponse"] = requestResponse,
                            ["roomName"] = CurrentRoom.RoomName
                        },
                        cancellationToken)
                    .ConfigureAwait(false);

                if (accept && IsScreenRequestIcon(request.Icon))
                {
                    TryMarkScreenProducerParticipantHint(request.DisplayName ?? request.UserName);
                }

                UpdateCurrentRoomState(
                    nextRoom =>
                    {
                        RemovePendingRequest(nextRoom.PendingRequests, request.RequestId);
                        nextRoom.PendingModerationCount = ComputePendingModerationCount(nextRoom);
                    });
                return MediaSfuOperationResult<bool>.FromSuccess(true);
            }
            catch (Exception error)
            {
                PublishError("RespondToRoomRequest", error.Message, error.ToString());
                return MediaSfuOperationResult<bool>.FromFailure(
                    $"RespondToRoomRequestAsync failed: {error.Message}",
                    error.ToString());
            }
        }

        public async Task<MediaSfuOperationResult<bool>> RespondToWaitingParticipantAsync(
            MediaSfuWaitingRoomParticipant participant,
            bool allow,
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();

            if (participant == null || string.IsNullOrWhiteSpace(participant.ParticipantId))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "RespondToWaitingParticipantAsync requires a waiting-room participant with a non-empty ParticipantId.");
            }

            var session = _socketTransportSession;
            if (session == null || session.Socket == null || session.Socket.State != WebSocketState.Open)
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "RespondToWaitingParticipantAsync requires an active socket connection from ConnectMediaAsync.");
            }

            if (CurrentRoom == null || string.IsNullOrWhiteSpace(CurrentRoom.RoomName))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "RespondToWaitingParticipantAsync requires a room from CreateRoomAsync or JoinRoomAsync.");
            }

            if (FindWaitingParticipantIndex(CurrentRoom.WaitingRoomParticipants, participant.ParticipantId) < 0)
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "RespondToWaitingParticipantAsync requires the participant to exist in CurrentRoom.WaitingRoomParticipants.");
            }

            try
            {
                await SendSocketEventAsync(
                        session,
                        "allowUserIn",
                        new Dictionary<string, object>
                        {
                            ["participantId"] = participant.ParticipantId,
                            ["participantName"] = participant.DisplayName,
                            ["type"] = allow ? "true" : "false",
                            ["roomName"] = CurrentRoom.RoomName
                        },
                        cancellationToken)
                    .ConfigureAwait(false);

                UpdateCurrentRoomState(
                    nextRoom =>
                    {
                        RemoveWaitingParticipant(nextRoom.WaitingRoomParticipants, participant.ParticipantId);
                        nextRoom.PendingModerationCount = ComputePendingModerationCount(nextRoom);
                    });
                return MediaSfuOperationResult<bool>.FromSuccess(true);
            }
            catch (Exception error)
            {
                PublishError("RespondToWaitingParticipant", error.Message, error.ToString());
                return MediaSfuOperationResult<bool>.FromFailure(
                    $"RespondToWaitingParticipantAsync failed: {error.Message}",
                    error.ToString());
            }
        }

        public async Task<MediaSfuOperationResult<bool>> RemoveParticipantAsync(
            MediaSfuParticipant participant,
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();

            if (participant == null || string.IsNullOrWhiteSpace(participant.ParticipantId))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "RemoveParticipantAsync requires a participant with a non-empty ParticipantId.");
            }

            var session = _socketTransportSession;
            if (session == null || session.Socket == null || session.Socket.State != WebSocketState.Open)
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "RemoveParticipantAsync requires an active socket connection from ConnectMediaAsync.");
            }

            if (CurrentRoom == null || string.IsNullOrWhiteSpace(CurrentRoom.RoomName))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "RemoveParticipantAsync requires a room from CreateRoomAsync or JoinRoomAsync.");
            }

            MediaSfuParticipant targetParticipant = null;
            foreach (var roomParticipant in CurrentRoom.Participants)
            {
                if (roomParticipant == null)
                {
                    continue;
                }

                if (NormalizeParticipantKey(roomParticipant.ParticipantId) == NormalizeParticipantKey(participant.ParticipantId) ||
                    (!string.IsNullOrWhiteSpace(participant.DisplayName) &&
                     roomParticipant.DisplayName.Equals(participant.DisplayName, StringComparison.OrdinalIgnoreCase)))
                {
                    targetParticipant = roomParticipant;
                    break;
                }
            }

            if (targetParticipant == null)
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "RemoveParticipantAsync requires the participant to exist in CurrentRoom.Participants.");
            }

            if (targetParticipant.IsLocal)
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "RemoveParticipantAsync cannot remove the local participant. Use LeaveRoomAsync instead.");
            }

            if (targetParticipant.Role == MediaSfuParticipantRole.Host)
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "RemoveParticipantAsync cannot remove the host participant.");
            }

            if (string.IsNullOrWhiteSpace(targetParticipant.DisplayName))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "RemoveParticipantAsync requires the participant to have a resolved DisplayName.");
            }

            if (!CanLocalParticipantManageParticipants())
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "RemoveParticipantAsync requires the local participant to be the host or the assigned co-host with participants responsibility.");
            }

            try
            {
                await SendSocketEventAsync(
                        session,
                        "disconnectUserInitiate",
                        new Dictionary<string, object>
                        {
                            ["member"] = targetParticipant.DisplayName,
                            ["roomName"] = CurrentRoom.RoomName,
                            ["id"] = targetParticipant.ParticipantId
                        },
                        cancellationToken)
                    .ConfigureAwait(false);

                UpdateCurrentRoomState(
                    nextRoom => RemoveParticipant(nextRoom.Participants, targetParticipant.ParticipantId, targetParticipant.DisplayName));
                return MediaSfuOperationResult<bool>.FromSuccess(
                    true,
                    "disconnectUserInitiate emitted for " + targetParticipant.DisplayName + ".");
            }
            catch (Exception error)
            {
                PublishError("RemoveParticipant", error.Message, error.ToString());
                return MediaSfuOperationResult<bool>.FromFailure(
                    "RemoveParticipantAsync failed: " + error.Message,
                    error.ToString());
            }
        }

        public async Task<MediaSfuOperationResult<bool>> UpdateCoHostAsync(
            string coHostName,
            IReadOnlyList<MediaSfuCoHostResponsibility> coHostResponsibilities = null,
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();

            if (!HasOpenRoomSocketSession())
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "UpdateCoHostAsync requires an active socket connection from ConnectMediaAsync.");
            }

            if (CurrentRoom == null || string.IsNullOrWhiteSpace(CurrentRoom.RoomName))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "UpdateCoHostAsync requires a room from CreateRoomAsync or JoinRoomAsync.");
            }

            if (CurrentRoom.RoomName.StartsWith("d", StringComparison.OrdinalIgnoreCase))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "UpdateCoHostAsync cannot modify co-host settings for demo rooms.");
            }

            if (!IsLocalParticipantHost())
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "UpdateCoHostAsync requires the local participant to be the host.");
            }

            var resolvedCoHostName = NormalizeCoHostNameForUpdate(coHostName);
            var resolvedResponsibilities = ResolveCoHostResponsibilitiesForUpdate(
                resolvedCoHostName,
                coHostResponsibilities);

            if (!string.Equals(resolvedCoHostName, "No coHost", StringComparison.OrdinalIgnoreCase))
            {
                var targetParticipant = FindParticipantByName(resolvedCoHostName);
                if (targetParticipant == null)
                {
                    return MediaSfuOperationResult<bool>.FromFailure(
                        "UpdateCoHostAsync requires the selected participant to exist in CurrentRoom.Participants.");
                }

                if (targetParticipant.Role == MediaSfuParticipantRole.Host)
                {
                    return MediaSfuOperationResult<bool>.FromFailure(
                        "UpdateCoHostAsync cannot assign the host as co-host.");
                }
            }

            try
            {
                var acknowledgment = await EmitBooleanRoomControlSocketEventWithAckAsync(
                    "UpdateCoHost",
                        "updateCoHost",
                        new Dictionary<string, object>
                        {
                            ["roomName"] = CurrentRoom.RoomName,
                            ["coHost"] = resolvedCoHostName,
                            ["coHostResponsibility"] = SerializeCoHostResponsibilities(resolvedResponsibilities)
                        },
                        preferConsumeSession: true,
                        cancellationToken)
                    .ConfigureAwait(false);
                if (!acknowledgment.Success)
                {
                    var reason = string.IsNullOrWhiteSpace(acknowledgment.Reason)
                        ? "unknown reason"
                        : acknowledgment.Reason;
                    return MediaSfuOperationResult<bool>.FromFailure(
                        "UpdateCoHostAsync failed: " + reason,
                        acknowledgment.RawPayload);
                }

                UpdateCurrentRoomState(
                    nextRoom => ApplyCoHostUpdateState(
                        nextRoom,
                        resolvedCoHostName,
                        resolvedResponsibilities));
                return MediaSfuOperationResult<bool>.FromSuccess(
                    true,
                    "updateCoHost acknowledged for " + resolvedCoHostName + ".");
            }
            catch (Exception error)
            {
                PublishError("UpdateCoHost", error.Message, error.ToString());
                return MediaSfuOperationResult<bool>.FromFailure(
                    "UpdateCoHostAsync failed: " + error.Message,
                    error.ToString());
            }
        }

        public Task<MediaSfuOperationResult<bool>> StartOrUpdateBreakoutRoomsAsync(
            MediaSfuBreakoutRoomsRequest request,
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();

            if (request == null)
            {
                return Task.FromResult(
                    MediaSfuOperationResult<bool>.FromFailure(
                        "StartOrUpdateBreakoutRoomsAsync requires a non-null request."));
            }

            if (!IsLocalParticipantHost())
            {
                return Task.FromResult(
                    MediaSfuOperationResult<bool>.FromFailure(
                        "StartOrUpdateBreakoutRoomsAsync requires the local participant to be the host."));
            }

            if (CurrentRoom?.ShareScreenStarted == true)
            {
                return Task.FromResult(
                    MediaSfuOperationResult<bool>.FromFailure(
                        "StartOrUpdateBreakoutRoomsAsync cannot start breakout rooms while screen sharing is active."));
            }

            var normalizedRooms = NormalizeBreakoutRooms(request.Rooms);
            if (normalizedRooms.Count == 0)
            {
                return Task.FromResult(
                    MediaSfuOperationResult<bool>.FromFailure(
                        "StartOrUpdateBreakoutRoomsAsync requires at least one breakout room with participants."));
            }

            var eventName = CurrentRoom?.Breakout?.Started == true && CurrentRoom.Breakout.Ended == false
                ? "updateBreakout"
                : "startBreakout";
            var newParticipantAction = string.IsNullOrWhiteSpace(request.NewParticipantAction)
                ? "autoAssignNewRoom"
                : request.NewParticipantAction.Trim();

            return SendBooleanAckSocketEventAsync(
                "StartOrUpdateBreakoutRooms",
                eventName,
                new Dictionary<string, object>
                {
                    ["breakoutRooms"] = BuildBreakoutRoomsPayload(normalizedRooms),
                    ["newParticipantAction"] = newParticipantAction,
                    ["roomName"] = CurrentRoom?.RoomName ?? string.Empty
                },
                nextRoom => ApplyBreakoutRoomsStartedState(nextRoom, normalizedRooms, eventName),
                cancellationToken);
        }

        public Task<MediaSfuOperationResult<bool>> StopBreakoutRoomsAsync(
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();

            if (!IsLocalParticipantHost())
            {
                return Task.FromResult(
                    MediaSfuOperationResult<bool>.FromFailure(
                        "StopBreakoutRoomsAsync requires the local participant to be the host."));
            }

            return SendBooleanAckSocketEventAsync(
                "StopBreakoutRooms",
                "stopBreakout",
                new Dictionary<string, object>
                {
                    ["roomName"] = CurrentRoom?.RoomName ?? string.Empty
                },
                ApplyBreakoutRoomsStoppedState,
                cancellationToken);
        }

        public Task<MediaSfuOperationResult<bool>> StartOrUpdateWhiteboardAsync(
            MediaSfuWhiteboardSessionRequest request,
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();

            if (request == null)
            {
                return Task.FromResult(
                    MediaSfuOperationResult<bool>.FromFailure(
                        "StartOrUpdateWhiteboardAsync requires a non-null request."));
            }

            if (!IsLocalParticipantHost())
            {
                return Task.FromResult(
                    MediaSfuOperationResult<bool>.FromFailure(
                        "StartOrUpdateWhiteboardAsync requires the local participant to be the host."));
            }

            if (CurrentRoom?.ShareScreenStarted == true)
            {
                return Task.FromResult(
                    MediaSfuOperationResult<bool>.FromFailure(
                        "StartOrUpdateWhiteboardAsync cannot start whiteboard while screen sharing is active."));
            }

            if (CurrentRoom?.Breakout?.Started == true && CurrentRoom.Breakout.Ended == false)
            {
                return Task.FromResult(
                    MediaSfuOperationResult<bool>.FromFailure(
                        "StartOrUpdateWhiteboardAsync cannot start whiteboard while breakout rooms are active."));
            }

            var normalizedUsers = NormalizeWhiteboardUsers(request.Users);
            var eventName = CurrentRoom?.Whiteboard?.Started == true && CurrentRoom.Whiteboard.Ended == false
                ? "updateWhiteboard"
                : "startWhiteboard";

            return SendBooleanAckSocketEventAsync(
                "StartOrUpdateWhiteboard",
                eventName,
                new Dictionary<string, object>
                {
                    ["whiteboardUsers"] = BuildWhiteboardUsersPayload(normalizedUsers),
                    ["roomName"] = CurrentRoom?.RoomName ?? string.Empty
                },
                nextRoom => ApplyWhiteboardStartedState(nextRoom, normalizedUsers, eventName),
                cancellationToken);
        }

        public Task<MediaSfuOperationResult<bool>> StopWhiteboardAsync(
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();

            if (!IsLocalParticipantHost())
            {
                return Task.FromResult(
                    MediaSfuOperationResult<bool>.FromFailure(
                        "StopWhiteboardAsync requires the local participant to be the host."));
            }

            return SendBooleanAckSocketEventAsync(
                "StopWhiteboard",
                "stopWhiteboard",
                new Dictionary<string, object>
                {
                    ["roomName"] = CurrentRoom?.RoomName ?? string.Empty
                },
                ApplyWhiteboardStoppedState,
                cancellationToken);
        }

        public Task<MediaSfuOperationResult<bool>> SendWhiteboardActionAsync(
            string action,
            MediaSfuWhiteboardShape shape = null,
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();

            var normalizedAction = NormalizeWhiteboardActionName(action);
            if (string.IsNullOrWhiteSpace(normalizedAction))
            {
                return Task.FromResult(
                    MediaSfuOperationResult<bool>.FromFailure(
                        "SendWhiteboardActionAsync requires a non-empty action."));
            }

            if (!CanLocalParticipantUseWhiteboard())
            {
                return Task.FromResult(
                    MediaSfuOperationResult<bool>.FromFailure(
                        "SendWhiteboardActionAsync requires host access or an assigned whiteboard user."));
            }

            if (RequiresWhiteboardShapePayload(normalizedAction) && shape == null)
            {
                return Task.FromResult(
                    MediaSfuOperationResult<bool>.FromFailure(
                        "SendWhiteboardActionAsync requires a shape payload for this action."));
            }

            var actionPayload = shape == null
                ? new Dictionary<string, object>()
                : BuildWhiteboardShapePayload(shape);
            var eventPayload = BuildWhiteboardActionPayload(normalizedAction, actionPayload);

            return SendBooleanAckSocketEventAsync(
                "SendWhiteboardAction",
                "updateBoardAction",
                eventPayload,
                nextRoom => ApplyWhiteboardActionState(nextRoom, eventPayload),
                cancellationToken);
        }

        public Task<MediaSfuOperationResult<bool>> UpdateWhiteboardShapesAsync(
            IReadOnlyList<MediaSfuWhiteboardShape> shapes,
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();

            if (!CanLocalParticipantUseWhiteboard())
            {
                return Task.FromResult(
                    MediaSfuOperationResult<bool>.FromFailure(
                        "UpdateWhiteboardShapesAsync requires host access or an assigned whiteboard user."));
            }

            var payload = new Dictionary<string, object>
            {
                ["shapes"] = BuildWhiteboardShapesPayload(shapes)
            };
            var eventPayload = BuildWhiteboardActionPayload("shapes", payload);

            return SendBooleanAckSocketEventAsync(
                "UpdateWhiteboardShapes",
                "updateBoardAction",
                eventPayload,
                nextRoom => ApplyWhiteboardActionState(nextRoom, eventPayload),
                cancellationToken);
        }

        public Task<MediaSfuOperationResult<bool>> ClearWhiteboardAsync(
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();

            if (!CanLocalParticipantUseWhiteboard())
            {
                return Task.FromResult(
                    MediaSfuOperationResult<bool>.FromFailure(
                        "ClearWhiteboardAsync requires host access or an assigned whiteboard user."));
            }

            var eventPayload = BuildWhiteboardActionPayload("clear", new Dictionary<string, object>());

            return SendBooleanAckSocketEventAsync(
                "ClearWhiteboard",
                "updateBoardAction",
                eventPayload,
                nextRoom => ApplyWhiteboardActionState(nextRoom, eventPayload),
                cancellationToken);
        }

        public Task<MediaSfuOperationResult<bool>> CreatePollAsync(
            MediaSfuPollCreateRequest request,
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();

            if (request == null)
            {
                return Task.FromResult(
                    MediaSfuOperationResult<bool>.FromFailure(
                        "CreatePollAsync requires a non-null request."));
            }

            var question = request.Question?.Trim() ?? string.Empty;
            if (string.IsNullOrWhiteSpace(question))
            {
                return Task.FromResult(
                    MediaSfuOperationResult<bool>.FromFailure(
                        "CreatePollAsync requires a non-empty Question."));
            }

            var options = new List<string>();
            if (request.Options != null)
            {
                foreach (var option in request.Options)
                {
                    var trimmedOption = option?.Trim() ?? string.Empty;
                    if (!string.IsNullOrWhiteSpace(trimmedOption))
                    {
                        options.Add(trimmedOption);
                    }
                }
            }

            if (options.Count < 2)
            {
                return Task.FromResult(
                    MediaSfuOperationResult<bool>.FromFailure(
                        "CreatePollAsync requires at least two non-empty options."));
            }

            var pollType = string.IsNullOrWhiteSpace(request.Type)
                ? "singleChoice"
                : request.Type.Trim();

            return SendBooleanAckSocketEventAsync(
                "CreatePoll",
                "createPoll",
                new Dictionary<string, object>
                {
                    ["roomName"] = CurrentRoom?.RoomName ?? string.Empty,
                    ["poll"] = new Dictionary<string, object>
                    {
                        ["question"] = question,
                        ["type"] = pollType,
                        ["options"] = options
                    }
                },
                nextRoom => nextRoom.PollModalVisible = false,
                cancellationToken);
        }

        public Task<MediaSfuOperationResult<bool>> VotePollAsync(
            MediaSfuPollVoteRequest request,
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();

            if (request == null)
            {
                return Task.FromResult(
                    MediaSfuOperationResult<bool>.FromFailure(
                        "VotePollAsync requires a non-null request."));
            }

            if (string.IsNullOrWhiteSpace(request.PollId))
            {
                return Task.FromResult(
                    MediaSfuOperationResult<bool>.FromFailure(
                        "VotePollAsync requires a non-empty PollId."));
            }

            if (request.OptionIndex < 0)
            {
                return Task.FromResult(
                    MediaSfuOperationResult<bool>.FromFailure(
                        "VotePollAsync requires a non-negative OptionIndex."));
            }

            var memberName = FindLocalParticipant()?.DisplayName ?? _lastLocalUserName;
            if (string.IsNullOrWhiteSpace(memberName))
            {
                return Task.FromResult(
                    MediaSfuOperationResult<bool>.FromFailure(
                        "VotePollAsync requires a resolved local participant name."));
            }

            return SendBooleanAckSocketEventAsync(
                "VotePoll",
                "votePoll",
                new Dictionary<string, object>
                {
                    ["roomName"] = CurrentRoom?.RoomName ?? string.Empty,
                    ["poll_id"] = request.PollId,
                    ["member"] = memberName,
                    ["choice"] = request.OptionIndex
                },
                nextRoom => ApplyPollVoteState(nextRoom, request.PollId, memberName, request.OptionIndex),
                cancellationToken);
        }

        public Task<MediaSfuOperationResult<bool>> EndPollAsync(
            string pollId,
            CancellationToken cancellationToken = default)
        {
            EnsureNotDisposed();

            if (string.IsNullOrWhiteSpace(pollId))
            {
                return Task.FromResult(
                    MediaSfuOperationResult<bool>.FromFailure(
                        "EndPollAsync requires a non-empty PollId."));
            }

            return SendBooleanAckSocketEventAsync(
                "EndPoll",
                "endPoll",
                new Dictionary<string, object>
                {
                    ["roomName"] = CurrentRoom?.RoomName ?? string.Empty,
                    ["poll_id"] = pollId
                },
                nextRoom => ApplyPollEndedState(nextRoom, pollId),
                cancellationToken);
        }

        public void ConfirmPresence(bool suppressFuturePrompts = false)
        {
            EnsureNotDisposed();

            if (suppressFuturePrompts)
            {
                _suppressConfirmHere = true;
            }

            if (CurrentRoom == null)
            {
                return;
            }

            UpdateCurrentRoomState(nextRoom => nextRoom.ConfirmHereRequested = false);
        }

        private async Task<MediaSfuOperationResult<bool>> SendRecordingControlAsync(
            string operation,
            string eventName,
            Dictionary<string, object> payload,
            Action<MediaSfuRoom, RecordingControlAck> applySuccess,
            CancellationToken cancellationToken)
        {
            if (!HasOpenRoomSocketSession())
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    $"{operation} requires an active socket connection from ConnectMediaAsync.");
            }

            if (CurrentRoom == null || string.IsNullOrWhiteSpace(CurrentRoom.RoomName))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    $"{operation} requires a room from CreateRoomAsync or JoinRoomAsync.");
            }

            try
            {
                var acknowledgmentPayload = await EmitRoomControlSocketEventWithAckAsync(
                        eventName,
                        payload,
                    preferConsumeSession: false,
                        cancellationToken)
                    .ConfigureAwait(false);

                var acknowledgment = ParseRecordingControlAck(acknowledgmentPayload, operation);

                if (!acknowledgment.Success)
                {
                    return MediaSfuOperationResult<bool>.FromFailure(
                        BuildRecordingControlFailureMessage(operation, acknowledgment),
                        acknowledgment.RawPayload);
                }

                UpdateCurrentRoomState(nextRoom => applySuccess?.Invoke(nextRoom, acknowledgment));
                return MediaSfuOperationResult<bool>.FromSuccess(true);
            }
            catch (Exception error)
            {
                PublishError(operation, error.Message, error.ToString());
                return MediaSfuOperationResult<bool>.FromFailure(
                    $"{operation} failed: {error.Message}",
                    error.ToString());
            }
        }

        private async Task<MediaSfuOperationResult<bool>> SendBooleanAckSocketEventAsync(
            string operation,
            string eventName,
            Dictionary<string, object> payload,
            Action<MediaSfuRoom> applySuccess,
            CancellationToken cancellationToken)
        {
            if (!HasOpenRoomSocketSession())
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    $"{operation} requires an active socket connection from ConnectMediaAsync.");
            }

            if (CurrentRoom == null || string.IsNullOrWhiteSpace(CurrentRoom.RoomName))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    $"{operation} requires a room from CreateRoomAsync or JoinRoomAsync.");
            }

            try
            {
                var acknowledgment = await EmitBooleanRoomControlSocketEventWithAckAsync(
                    operation,
                        eventName,
                        payload,
                    preferConsumeSession: false,
                        cancellationToken)
                    .ConfigureAwait(false);
                if (!acknowledgment.Success)
                {
                    var reason = string.IsNullOrWhiteSpace(acknowledgment.Reason)
                        ? "unknown reason"
                        : acknowledgment.Reason;
                    return MediaSfuOperationResult<bool>.FromFailure(
                        $"{operation} failed: {reason}",
                        acknowledgment.RawPayload);
                }

                UpdateCurrentRoomState(nextRoom => applySuccess?.Invoke(nextRoom));
                return MediaSfuOperationResult<bool>.FromSuccess(true);
            }
            catch (Exception error)
            {
                PublishError(operation, error.Message, error.ToString());
                return MediaSfuOperationResult<bool>.FromFailure(
                    $"{operation} failed: {error.Message}",
                    error.ToString());
            }
        }

        private static string BuildRecordingControlFailureMessage(string operation, RecordingControlAck acknowledgment)
        {
            var reason = string.IsNullOrWhiteSpace(acknowledgment?.Reason)
                ? "unknown reason"
                : acknowledgment.Reason;
            var stateSuffix = string.IsNullOrWhiteSpace(acknowledgment?.RecordState)
                ? string.Empty
                : $"; the current state is: {acknowledgment.RecordState}";

            return operation switch
            {
                "PauseRecording" => $"Recording Pause Failed: {reason}{stateSuffix}",
                "ResumeRecording" => $"Recording could not resume - {reason}",
                "StopRecording" => $"Recording Stop Failed: {reason}{stateSuffix}",
                _ => $"Recording could not start - {reason}"
            };
        }

        private MediaSfuUserRecordingParams ResolveRecordingUserParams(MediaSfuUserRecordingParams requestedUserRecordingParams)
        {
            return CloneUserRecordingParams(requestedUserRecordingParams) ??
                   CloneUserRecordingParams(CurrentRoom?.Recording?.UserRecordingParams) ??
                   new MediaSfuUserRecordingParams();
        }

        private static string ResolveRecordingMediaOptions(MediaSfuUserRecordingParams userRecordingParams)
        {
            var mediaOptions = userRecordingParams?.MainSpecs?.MediaOptions;
            return string.IsNullOrWhiteSpace(mediaOptions)
                ? "video"
                : mediaOptions.Trim().ToLowerInvariant();
        }

        private static int ResolveRecordingPauseLimit(MediaSfuRoom room, string recordingMediaOptions)
        {
            if (room?.RecordingParameters == null)
            {
                return 0;
            }

            return string.Equals(recordingMediaOptions, "video", StringComparison.OrdinalIgnoreCase)
                ? room.RecordingParameters.RecordingVideoPausesLimit
                : room.RecordingParameters.RecordingAudioPausesLimit;
        }

        private static bool HasRecordingSupport(MediaSfuRecordingParameters recordingParameters)
        {
            return recordingParameters != null &&
                   (recordingParameters.RecordingAudioSupport || recordingParameters.RecordingVideoSupport);
        }

        private static void ApplyRecordingStartState(
            MediaSfuRoom room,
            string action,
            MediaSfuUserRecordingParams userRecordingParams)
        {
            if (room == null)
            {
                return;
            }

            var recording = room.Recording ?? (room.Recording = new MediaSfuRecordingState());
            if (userRecordingParams != null)
            {
                recording.UserRecordingParams = CloneUserRecordingParams(userRecordingParams);
            }

            if (string.Equals(action, "startRecord", StringComparison.OrdinalIgnoreCase))
            {
                recording.PauseCount = 0;
                recording.RecordElapsedTimeSeconds = 0;
                recording.ProgressTime = "00:00:00";
                recording.RecordStartTimeEpochMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            }

            recording.LastNoticeState = string.Equals(action, "resumeRecord", StringComparison.OrdinalIgnoreCase)
                ? "resume"
                : "start";
            recording.LastStopReason = string.Empty;
            recording.TimeLeftSeconds = null;
            recording.State = "red";
            recording.RecordStarted = true;
            recording.RecordPaused = false;
            recording.RecordStopped = false;
            recording.CanLaunchRecord = false;
            recording.ShowRecordButtons = true;
            recording.IsTimerRunning = true;
        }

        private static void ApplyRecordingPauseState(MediaSfuRoom room, RecordingControlAck acknowledgment)
        {
            if (room == null)
            {
                return;
            }

            var recording = room.Recording ?? (room.Recording = new MediaSfuRecordingState());
            recording.LastNoticeState = string.IsNullOrWhiteSpace(acknowledgment?.RecordState)
                ? "pause"
                : acknowledgment.RecordState;
            recording.PauseCount = acknowledgment?.PauseCount ?? Math.Max(recording.PauseCount + 1, 1);
            recording.State = "yellow";
            recording.RecordStarted = true;
            recording.RecordPaused = true;
            recording.RecordStopped = false;
            recording.CanLaunchRecord = false;
            recording.ShowRecordButtons = true;
            recording.IsTimerRunning = false;
            recording.CanPauseResume = true;
        }

        private static void ApplyRecordingStopState(MediaSfuRoom room, RecordingControlAck acknowledgment)
        {
            if (room == null)
            {
                return;
            }

            var recording = room.Recording ?? (room.Recording = new MediaSfuRecordingState());
            recording.LastNoticeState = string.IsNullOrWhiteSpace(acknowledgment?.RecordState)
                ? "stop"
                : acknowledgment.RecordState;
            recording.LastStopReason = acknowledgment?.Reason ?? string.Empty;
            recording.TimeLeftSeconds = null;
            recording.State = "green";
            recording.RecordStarted = true;
            recording.RecordPaused = false;
            recording.RecordStopped = true;
            recording.CanLaunchRecord = false;
            recording.ShowRecordButtons = false;
            recording.IsTimerRunning = false;
            recording.CanPauseResume = false;
        }

        private async Task<MediaSfuOperationResult<bool>> ExecuteLocalMediaOperationAsync(
            string operation,
            MediaSfuTrackKind trackKind,
            string mediaTag,
            bool enabled,
            string missingBackendDetail,
            string fallbackEventName,
            Func<IMediaSfuLocalMediaBackend, MediaSfuLocalMediaRequest, CancellationToken, Task<MediaSfuOperationResult<bool>>> backendInvoker,
            CancellationToken cancellationToken,
            string fallbackPreEventName = null)
        {
            if (LocalMediaBackend == null)
            {
                if (enabled && !string.IsNullOrWhiteSpace(missingBackendDetail))
                {
                    return MediaSfuOperationResult<bool>.FromDeferredContract(missingBackendDetail);
                }

                return await SendLocalMediaControlAsync(
                        operation,
                        mediaTag,
                        enabled,
                        fallbackEventName,
                        cancellationToken,
                        fallbackPreEventName)
                    .ConfigureAwait(false);
            }

            var request = BuildLocalMediaRequest(trackKind, enabled);

            try
            {
                var result = await backendInvoker(LocalMediaBackend, request, cancellationToken).ConfigureAwait(false);
                if (result == null)
                {
                    return MediaSfuOperationResult<bool>.FromFailure(
                        $"{operation} failed: local media backend returned no result.");
                }

                if (result.Success)
                {
                    if (enabled)
                    {
                        ConsumeGrantedLocalRequest(trackKind);
                    }

                    ApplyLocalMediaBackendState(mediaTag, enabled);
                }

                return result;
            }
            catch (Exception error)
            {
                PublishError(operation, error.Message, error.ToString());
                return MediaSfuOperationResult<bool>.FromFailure(
                    $"{operation} failed: {error.Message}",
                    error.ToString());
            }
        }

        private MediaSfuLocalMediaRequest BuildLocalMediaRequest(MediaSfuTrackKind trackKind, bool enabled)
        {
            return new MediaSfuLocalMediaRequest
            {
                Kind = trackKind,
                Enabled = enabled,
                Room = CloneRoom(CurrentRoom),
                RoomValidation = CloneRoomValidation(LastRoomValidation),
                LocalParticipant = CloneParticipant(FindLocalParticipant()),
                LocalUserName = _lastLocalUserName,
                LocalIsLevel = _lastLocalIsLevel,
                CreateWebRtcTransportAsync = CreateLocalMediaBackendWebRtcTransportAsync,
                ConnectWebRtcTransportAsync = ConnectLocalMediaBackendWebRtcTransportAsync,
                ProduceTrackAsync = ProduceLocalMediaBackendTrackAsync,
                EmitSocketEventAsync = EmitLocalMediaBackendSocketEventAsync,
                EmitSocketEventWithAckAsync = EmitLocalMediaBackendSocketEventWithAckAsync
            };
        }

        private MediaSfuRemoteMediaRequest BuildRemoteMediaRequest(MediaSfuRemoteProducerEvent remoteProducer)
        {
            return new MediaSfuRemoteMediaRequest
            {
                RemoteProducer = remoteProducer,
                Room = CloneRoom(CurrentRoom),
                RoomValidation = CloneRoomValidation(LastRoomValidation),
                CreateWebRtcTransportAsync = CreateLocalMediaBackendWebRtcTransportAsync,
                ConnectWebRtcTransportAsync = ConnectLocalMediaBackendWebRtcTransportAsync,
                ConsumeTrackAsync = ConsumeMediaTrackInternalAsync,
                ResumeConsumerAsync = ResumeConsumerAsync,
                PauseConsumerAsync = PauseConsumerAsync
            };
        }

        private void ApplyLocalMediaBackendState(string mediaTag, bool enabled)
        {
            var localParticipant = FindLocalParticipant();
            var localParticipantName = localParticipant?.DisplayName ?? _lastLocalUserName;
            if (string.IsNullOrWhiteSpace(localParticipantName))
            {
                return;
            }

            UpdateParticipantTrackState(localParticipantName, mediaTag, enabled);

            if (IsScreenMediaTag(mediaTag))
            {
                ApplyLocalScreenShareRoomState(localParticipant, enabled);
            }
        }

        private static bool IsScreenMediaTag(string mediaTag)
        {
            if (string.IsNullOrWhiteSpace(mediaTag))
            {
                return false;
            }

            switch (mediaTag.Trim().ToLowerInvariant())
            {
                case "screen":
                case "screenshare":
                case "screen-share":
                    return true;
                default:
                    return false;
            }
        }

        private void ApplyLocalScreenShareRoomState(MediaSfuParticipant previousLocalParticipant, bool enabled)
        {
            if (CurrentRoom == null || enabled)
            {
                return;
            }

            var localScreenTrackId = previousLocalParticipant?.ScreenTrackId ?? string.Empty;
            var localParticipantWasSharing = previousLocalParticipant?.ScreenOn == true;
            var activeScreenProducerId = CurrentRoom.ScreenProducerId ?? string.Empty;

            if (!localParticipantWasSharing &&
                !string.IsNullOrWhiteSpace(activeScreenProducerId) &&
                !string.Equals(activeScreenProducerId, localScreenTrackId, StringComparison.OrdinalIgnoreCase))
            {
                return;
            }

            UpdateCurrentRoomState(
                nextRoom =>
                {
                    var nextScreenProducerId = nextRoom.ScreenProducerId ?? string.Empty;
                    if (localParticipantWasSharing ||
                        string.IsNullOrWhiteSpace(nextScreenProducerId) ||
                        string.Equals(nextScreenProducerId, localScreenTrackId, StringComparison.OrdinalIgnoreCase))
                    {
                        nextRoom.ScreenProducerId = string.Empty;
                        nextRoom.ShareScreenStarted = false;
                        nextRoom.DeferScreenReceived = false;
                    }
                });
        }

        private async Task EmitLocalMediaBackendSocketEventAsync(
            string eventName,
            Dictionary<string, object> payload,
            CancellationToken cancellationToken)
        {
            var session = GetLocalMediaBackendSocketSessionOrThrow();

            await SendSocketEventAsync(
                    session,
                    eventName,
                    payload ?? new Dictionary<string, object>(),
                    cancellationToken)
                .ConfigureAwait(false);
        }

        private async Task<string> EmitLocalMediaBackendSocketEventWithAckAsync(
            string eventName,
            Dictionary<string, object> payload,
            CancellationToken cancellationToken)
        {
            var session = GetLocalMediaBackendSocketSessionOrThrow();

            return await EmitSocketEventWithAckAsync(
                    session,
                    eventName,
                    payload ?? new Dictionary<string, object>(),
                    cancellationToken)
                .ConfigureAwait(false);
        }

        private async Task<MediaSfuOperationResult<MediaSfuWebRtcTransport>> CreateLocalMediaBackendWebRtcTransportAsync(
            MediaSfuWebRtcTransportRequest request,
            CancellationToken cancellationToken)
        {
            if (CurrentRoom == null || string.IsNullOrWhiteSpace(CurrentRoom.RoomName))
            {
                return MediaSfuOperationResult<MediaSfuWebRtcTransport>.FromFailure(
                    "CreateWebRtcTransport requires a room from CreateRoomAsync or JoinRoomAsync.");
            }

            try
            {
                var normalizedRequest = request ?? new MediaSfuWebRtcTransportRequest();
                var session = normalizedRequest.Direction == MediaSfuTransportDirection.Receive
                    ? await EnsureRemoteMediaBackendSocketSessionAsync(cancellationToken).ConfigureAwait(false)
                    : GetLocalMediaBackendSocketSessionOrThrow();
                var acknowledgmentPayload = await EmitSocketEventWithAckAsync(
                        session,
                        "createWebRtcTransport",
                        new Dictionary<string, object>
                        {
                            ["consumer"] = normalizedRequest.Direction == MediaSfuTransportDirection.Receive,
                            ["islevel"] = string.IsNullOrWhiteSpace(normalizedRequest.IsLevel)
                                ? _lastLocalIsLevel
                                : normalizedRequest.IsLevel
                        },
                        cancellationToken)
                    .ConfigureAwait(false);
                var transport = ParseWebRtcTransportAck(acknowledgmentPayload, "CreateWebRtcTransport");
                return MediaSfuOperationResult<MediaSfuWebRtcTransport>.FromSuccess(transport);
            }
            catch (Exception error)
            {
                PublishError("CreateWebRtcTransport", error.Message, error.ToString());
                return MediaSfuOperationResult<MediaSfuWebRtcTransport>.FromFailure(
                    $"CreateWebRtcTransport failed: {error.Message}",
                    error.ToString());
            }
        }

        private async Task<MediaSfuOperationResult<bool>> ConnectLocalMediaBackendWebRtcTransportAsync(
            MediaSfuTransportConnectRequest request,
            CancellationToken cancellationToken)
        {
            if (request == null)
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "ConnectWebRtcTransport requires a non-null request.");
            }

            if (string.IsNullOrWhiteSpace(request.DtlsParametersJson))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    "ConnectWebRtcTransport requires non-empty DTLS parameters JSON.");
            }

            try
            {
                var session = request.Direction == MediaSfuTransportDirection.Receive
                    ? await EnsureRemoteMediaBackendSocketSessionAsync(cancellationToken).ConfigureAwait(false)
                    : GetLocalMediaBackendSocketSessionOrThrow();
                var eventName = request.Direction == MediaSfuTransportDirection.Receive
                    ? "transport-recv-connect"
                    : "transport-connect";
                var payload = new Dictionary<string, object>
                {
                    ["dtlsParameters"] = DeserializeJsonPayload(request.DtlsParametersJson, "dtlsParameters")
                };

                if (request.Direction == MediaSfuTransportDirection.Receive)
                {
                    if (string.IsNullOrWhiteSpace(request.TransportId))
                    {
                        return MediaSfuOperationResult<bool>.FromFailure(
                            "ConnectWebRtcTransport receive transport requires a non-empty TransportId.");
                    }

                    payload["serverConsumerTransportId"] = request.TransportId;
                }

                await SendSocketEventAsync(session, eventName, payload, cancellationToken).ConfigureAwait(false);
                return MediaSfuOperationResult<bool>.FromSuccess(true);
            }
            catch (Exception error)
            {
                PublishError("ConnectWebRtcTransport", error.Message, error.ToString());
                return MediaSfuOperationResult<bool>.FromFailure(
                    $"ConnectWebRtcTransport failed: {error.Message}",
                    error.ToString());
            }
        }

        private async Task<MediaSfuOperationResult<MediaSfuProduceResponse>> ProduceLocalMediaBackendTrackAsync(
            MediaSfuProduceRequest request,
            CancellationToken cancellationToken)
        {
            if (request == null)
            {
                return MediaSfuOperationResult<MediaSfuProduceResponse>.FromFailure(
                    "ProduceTrack requires a non-null request.");
            }

            if (string.IsNullOrWhiteSpace(request.Kind))
            {
                return MediaSfuOperationResult<MediaSfuProduceResponse>.FromFailure(
                    "ProduceTrack requires a non-empty media kind.");
            }

            if (string.IsNullOrWhiteSpace(request.RtpParametersJson))
            {
                return MediaSfuOperationResult<MediaSfuProduceResponse>.FromFailure(
                    "ProduceTrack requires non-empty RTP parameters JSON.");
            }

            var session = GetLocalMediaBackendSocketSessionOrThrow();

            try
            {
                var payload = new Dictionary<string, object>
                {
                    ["kind"] = request.Kind,
                    ["rtpParameters"] = DeserializeJsonPayload(request.RtpParametersJson, "rtpParameters"),
                    ["islevel"] = string.IsNullOrWhiteSpace(request.IsLevel) ? _lastLocalIsLevel : request.IsLevel,
                    ["name"] = string.IsNullOrWhiteSpace(request.Name) ? _lastLocalUserName : request.Name
                };

                if (!string.IsNullOrWhiteSpace(request.AppDataJson))
                {
                    payload["appData"] = DeserializeJsonPayload(request.AppDataJson, "appData");
                }

                var acknowledgmentPayload = await EmitSocketEventWithAckAsync(
                        session,
                        "transport-produce",
                        payload,
                        cancellationToken)
                    .ConfigureAwait(false);
                var response = ParseProduceTrackAck(acknowledgmentPayload, "ProduceTrack");
                return MediaSfuOperationResult<MediaSfuProduceResponse>.FromSuccess(response);
            }
            catch (Exception error)
            {
                PublishError("ProduceTrack", error.Message, error.ToString());
                return MediaSfuOperationResult<MediaSfuProduceResponse>.FromFailure(
                    $"ProduceTrack failed: {error.Message}",
                    error.ToString());
            }
        }

        private async Task<MediaSfuOperationResult<MediaSfuConsumeResponse>> ConsumeMediaTrackInternalAsync(
            MediaSfuConsumeRequest request,
            CancellationToken cancellationToken)
        {
            if (request == null)
            {
                return MediaSfuOperationResult<MediaSfuConsumeResponse>.FromFailure(
                    "ConsumeTrack requires a non-null request.");
            }

            if (string.IsNullOrWhiteSpace(request.RemoteProducerId))
            {
                return MediaSfuOperationResult<MediaSfuConsumeResponse>.FromFailure(
                    "ConsumeTrack requires a non-empty RemoteProducerId.");
            }

            if (string.IsNullOrWhiteSpace(request.ServerConsumerTransportId))
            {
                return MediaSfuOperationResult<MediaSfuConsumeResponse>.FromFailure(
                    "ConsumeTrack requires a non-empty ServerConsumerTransportId.");
            }

            if (string.IsNullOrWhiteSpace(request.RtpCapabilitiesJson))
            {
                return MediaSfuOperationResult<MediaSfuConsumeResponse>.FromFailure(
                    "ConsumeTrack requires non-empty RTP capabilities JSON.");
            }

            try
            {
                var session = await EnsureRemoteMediaBackendSocketSessionAsync(cancellationToken).ConfigureAwait(false);
                var acknowledgmentPayload = await EmitSocketEventWithAckAsync(
                        session,
                        "consume",
                        new Dictionary<string, object>
                        {
                            ["rtpCapabilities"] = DeserializeJsonPayload(request.RtpCapabilitiesJson, "rtpCapabilities"),
                            ["remoteProducerId"] = request.RemoteProducerId,
                            ["serverConsumerTransportId"] = request.ServerConsumerTransportId
                        },
                        cancellationToken)
                    .ConfigureAwait(false);

                var response = ParseConsumeTrackAck(acknowledgmentPayload, "ConsumeTrack");
                return MediaSfuOperationResult<MediaSfuConsumeResponse>.FromSuccess(response);
            }
            catch (Exception error)
            {
                PublishError("ConsumeTrack", error.Message, error.ToString());
                return MediaSfuOperationResult<MediaSfuConsumeResponse>.FromFailure(
                    $"ConsumeTrack failed: {error.Message}",
                    error.ToString());
            }
        }

        private async Task<MediaSfuOperationResult<bool>> SendConsumerControlAsync(
            string eventName,
            string serverConsumerId,
            CancellationToken cancellationToken)
        {
            if (string.IsNullOrWhiteSpace(serverConsumerId))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    $"{eventName} requires a non-empty server consumer id.");
            }

            var session = GetRemoteMediaBackendSocketSessionOrThrow();

            try
            {
                await EmitSocketEventWithAckAsync(
                        session,
                        eventName,
                        new Dictionary<string, object>
                        {
                            ["serverConsumerId"] = serverConsumerId
                        },
                        cancellationToken)
                    .ConfigureAwait(false);
                return MediaSfuOperationResult<bool>.FromSuccess(true);
            }
            catch (Exception error)
            {
                PublishError(eventName, error.Message, error.ToString());
                return MediaSfuOperationResult<bool>.FromFailure(
                    $"{eventName} failed: {error.Message}",
                    error.ToString());
            }
        }

        private SocketTransportSession GetLocalMediaBackendSocketSessionOrThrow()
        {
            var session = _socketTransportSession;
            if (session == null || session.Socket == null || session.Socket.State != WebSocketState.Open)
            {
                throw new InvalidOperationException(
                    "Local media backend requires an active socket connection from ConnectMediaAsync.");
            }

            return session;
        }

        private SocketTransportSession GetRemoteMediaBackendSocketSessionOrThrow()
        {
            var session = _consumeSocketTransportSession;
            if (session != null && session.Socket != null && session.Socket.State == WebSocketState.Open)
            {
                return session;
            }

            return GetLocalMediaBackendSocketSessionOrThrow();
        }

        private async Task<SocketTransportSession> EnsureRemoteMediaBackendSocketSessionAsync(
            CancellationToken cancellationToken)
        {
            await _consumeSessionLock.WaitAsync(cancellationToken).ConfigureAwait(false);
            try
            {
                var consumeSession = await EnsureConsumeSocketTransportAsync(LastRoomValidation, cancellationToken).ConfigureAwait(false);
                if (consumeSession != null)
                {
                    await JoinConsumeRoomAsync(consumeSession, cancellationToken).ConfigureAwait(false);
                    return consumeSession;
                }

                return GetRemoteMediaBackendSocketSessionOrThrow();
            }
            finally
            {
                _consumeSessionLock.Release();
            }
        }

        private static MediaSfuRoomValidation CloneRoomValidation(MediaSfuRoomValidation value)
        {
            if (value == null)
            {
                return null;
            }

            return new MediaSfuRoomValidation
            {
                HasRtpCapabilities = value.HasRtpCapabilities,
                RtpCapabilitiesJson = value.RtpCapabilitiesJson,
                RoomRecvIps = value.RoomRecvIps == null ? new List<string>() : new List<string>(value.RoomRecvIps),
                SecureCode = value.SecureCode,
                RecordOnly = value.RecordOnly,
                IsHost = value.IsHost,
                SafeRoom = value.SafeRoom,
                AutoStartSafeRoom = value.AutoStartSafeRoom,
                SafeRoomStarted = value.SafeRoomStarted,
                SafeRoomEnded = value.SafeRoomEnded,
                Reason = value.Reason,
                Banned = value.Banned,
                Suspended = value.Suspended,
                NoAdmin = value.NoAdmin,
                RawPayload = value.RawPayload
            };
        }

        private async Task<MediaSfuOperationResult<bool>> SendLocalMediaControlAsync(
            string operation,
            string mediaTag,
            bool enabled,
            string eventName,
            CancellationToken cancellationToken,
            string preEventName = null)
        {
            var session = _socketTransportSession;
            if (session == null || session.Socket == null || session.Socket.State != WebSocketState.Open)
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    $"{operation} requires an active socket connection from ConnectMediaAsync.");
            }

            if (CurrentRoom == null || string.IsNullOrWhiteSpace(CurrentRoom.RoomName))
            {
                return MediaSfuOperationResult<bool>.FromFailure(
                    $"{operation} requires a room from CreateRoomAsync or JoinRoomAsync.");
            }

            try
            {
                if (!string.IsNullOrWhiteSpace(preEventName))
                {
                    await SendSocketEventAsync(
                            session,
                            preEventName,
                            new Dictionary<string, object>(),
                            cancellationToken)
                        .ConfigureAwait(false);
                }

                var payload = new Dictionary<string, object>
                {
                    ["mediaTag"] = mediaTag,
                    ["roomName"] = CurrentRoom.RoomName
                };

                await SendSocketEventAsync(session, eventName, payload, cancellationToken).ConfigureAwait(false);
                UpdateParticipantTrackState(_lastLocalUserName, mediaTag, enabled);

                return MediaSfuOperationResult<bool>.FromDeferredContract(
                    $"{operation} emitted {eventName} for {mediaTag}. No local media backend or WebRTC engine is currently attached, so Unity only applied the socket-side state change. " +
                    DeferredContractMessage);
            }
            catch (Exception error)
            {
                PublishError(operation, error.Message, error.ToString());
                return MediaSfuOperationResult<bool>.FromFailure(
                    $"{operation} failed: {error.Message}",
                    error.ToString());
            }
        }

        private bool IsHostRestrictionActive(MediaSfuTrackKind trackKind)
        {
            var room = CurrentRoom;
            if (room == null)
            {
                return false;
            }

            return trackKind switch
            {
                MediaSfuTrackKind.Audio => room.HostRestrictedAudio,
                MediaSfuTrackKind.Video => room.HostRestrictedVideo,
                MediaSfuTrackKind.Screen => room.HostRestrictedScreenshare,
                _ => false
            };
        }

        private bool TryCreateMediaPermissionFailure(
            MediaSfuTrackKind trackKind,
            out MediaSfuOperationResult<bool> failure)
        {
            failure = null;

            if (IsLocalParticipantPermissionExempt() || HasGrantedLocalRequest(trackKind))
            {
                return false;
            }

            var permissionRequirement = GetMediaPermissionRequirement(trackKind);
            switch (permissionRequirement)
            {
                case MediaPermissionRequirement.Allow:
                    return false;
                case MediaPermissionRequirement.Approval:
                    failure = MediaSfuOperationResult<bool>.FromFailure(
                        "Set" + GetTrackActionName(trackKind) + " cannot enable " + GetTrackPermissionLabel(trackKind) +
                        " because the room requires host approval first. RequestMediaPermissionAsync(MediaSfuTrackKind." +
                        GetTrackKindCodeName(trackKind) + ") must be approved first.");
                    return true;
                case MediaPermissionRequirement.Disallow:
                    failure = MediaSfuOperationResult<bool>.FromFailure(
                        "Set" + GetTrackActionName(trackKind) + " cannot enable " + GetTrackPermissionLabel(trackKind) +
                        " because the room currently disallows it.");
                    return true;
                default:
                    return false;
            }
        }

        private bool IsLocalParticipantPermissionExempt()
        {
            if (string.Equals(_lastLocalIsLevel, "2", StringComparison.OrdinalIgnoreCase))
            {
                return true;
            }

            var localParticipant = FindLocalParticipant();
            if (localParticipant?.Role == MediaSfuParticipantRole.CoHost)
            {
                return true;
            }

            var localParticipantName = localParticipant?.DisplayName ?? _lastLocalUserName;
            return !string.IsNullOrWhiteSpace(localParticipantName) &&
                   !string.IsNullOrWhiteSpace(CurrentRoom?.CoHost) &&
                   string.Equals(CurrentRoom.CoHost, localParticipantName, StringComparison.OrdinalIgnoreCase);
        }

        private bool IsLocalParticipantHost()
        {
            if (string.Equals(_lastLocalIsLevel, "2", StringComparison.OrdinalIgnoreCase))
            {
                return true;
            }

            return FindLocalParticipant()?.Role == MediaSfuParticipantRole.Host;
        }

        private bool CanLocalParticipantManageParticipants()
        {
            if (IsLocalParticipantHost())
            {
                return true;
            }

            var localParticipantName = FindLocalParticipant()?.DisplayName ?? _lastLocalUserName;
            if (string.IsNullOrWhiteSpace(localParticipantName) ||
                string.IsNullOrWhiteSpace(CurrentRoom?.CoHost) ||
                !string.Equals(CurrentRoom.CoHost, localParticipantName, StringComparison.OrdinalIgnoreCase))
            {
                return false;
            }

            if (CurrentRoom?.CoHostResponsibilities == null)
            {
                return false;
            }

            foreach (var responsibility in CurrentRoom.CoHostResponsibilities)
            {
                if (responsibility != null &&
                    responsibility.Value &&
                    string.Equals(responsibility.Name, "participants", StringComparison.OrdinalIgnoreCase))
                {
                    return true;
                }
            }

            return false;
        }

        private bool CanLocalParticipantUseWhiteboard()
        {
            var whiteboard = CurrentRoom?.Whiteboard;
            if (whiteboard == null || !whiteboard.Started || whiteboard.Ended)
            {
                return false;
            }

            if (IsLocalParticipantHost())
            {
                return true;
            }

            var localParticipantName = FindLocalParticipant()?.DisplayName ?? _lastLocalUserName;
            if (string.IsNullOrWhiteSpace(localParticipantName) || whiteboard.Users == null)
            {
                return false;
            }

            foreach (var user in whiteboard.Users)
            {
                if (user != null &&
                    user.UseBoard &&
                    string.Equals(user.Name, localParticipantName, StringComparison.OrdinalIgnoreCase))
                {
                    return true;
                }
            }

            return false;
        }

        private static List<List<MediaSfuBreakoutParticipant>> NormalizeBreakoutRooms(
            IEnumerable<IEnumerable<MediaSfuBreakoutParticipant>> rooms)
        {
            var normalizedRooms = new List<List<MediaSfuBreakoutParticipant>>();
            if (rooms == null)
            {
                return normalizedRooms;
            }

            var roomIndex = 0;
            foreach (var room in rooms)
            {
                var normalizedRoom = new List<MediaSfuBreakoutParticipant>();
                if (room != null)
                {
                    foreach (var participant in room)
                    {
                        if (participant == null || string.IsNullOrWhiteSpace(participant.DisplayName))
                        {
                            continue;
                        }

                        normalizedRoom.Add(
                            new MediaSfuBreakoutParticipant
                            {
                                DisplayName = participant.DisplayName.Trim(),
                                BreakRoom = participant.BreakRoom ?? roomIndex
                            });
                    }
                }

                if (normalizedRoom.Count > 0)
                {
                    normalizedRooms.Add(normalizedRoom);
                }

                roomIndex += 1;
            }

            return normalizedRooms;
        }

        private static List<MediaSfuWhiteboardUser> NormalizeWhiteboardUsers(
            IEnumerable<MediaSfuWhiteboardUser> users)
        {
            var normalizedUsers = new List<MediaSfuWhiteboardUser>();
            if (users == null)
            {
                return normalizedUsers;
            }

            var seenNames = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
            foreach (var user in users)
            {
                var name = user?.Name?.Trim() ?? string.Empty;
                if (string.IsNullOrWhiteSpace(name) || !seenNames.Add(name))
                {
                    continue;
                }

                normalizedUsers.Add(
                    new MediaSfuWhiteboardUser
                    {
                        Name = name,
                        UseBoard = true
                    });
            }

            return normalizedUsers;
        }

        private static List<List<Dictionary<string, object>>> BuildBreakoutRoomsPayload(
            IReadOnlyList<List<MediaSfuBreakoutParticipant>> rooms)
        {
            var payloadRooms = new List<List<Dictionary<string, object>>>();
            if (rooms == null)
            {
                return payloadRooms;
            }

            foreach (var room in rooms)
            {
                var payloadRoom = new List<Dictionary<string, object>>();
                if (room != null)
                {
                    foreach (var participant in room)
                    {
                        if (participant == null || string.IsNullOrWhiteSpace(participant.DisplayName))
                        {
                            continue;
                        }

                        payloadRoom.Add(
                            new Dictionary<string, object>
                            {
                                ["name"] = participant.DisplayName,
                                ["breakRoom"] = participant.BreakRoom ?? -1
                            });
                    }
                }

                if (payloadRoom.Count > 0)
                {
                    payloadRooms.Add(payloadRoom);
                }
            }

            return payloadRooms;
        }

        private static List<Dictionary<string, object>> BuildWhiteboardUsersPayload(
            IReadOnlyList<MediaSfuWhiteboardUser> users)
        {
            var payloadUsers = new List<Dictionary<string, object>>();
            if (users == null)
            {
                return payloadUsers;
            }

            foreach (var user in users)
            {
                if (user == null || string.IsNullOrWhiteSpace(user.Name))
                {
                    continue;
                }

                payloadUsers.Add(
                    new Dictionary<string, object>
                    {
                        ["name"] = user.Name,
                        ["useBoard"] = true
                    });
            }

            return payloadUsers;
        }

        private static Dictionary<string, object> BuildWhiteboardActionPayload(string action, object payload)
        {
            return new Dictionary<string, object>
            {
                ["action"] = action,
                ["payload"] = payload ?? new Dictionary<string, object>(),
                ["roomName"] = string.Empty
            };
        }

        private Dictionary<string, object> BuildWhiteboardActionPayload(string action, Dictionary<string, object> payload)
        {
            var eventPayload = BuildWhiteboardActionPayload(action, (object)payload);
            eventPayload["roomName"] = CurrentRoom?.RoomName ?? string.Empty;
            return eventPayload;
        }

        private static Dictionary<string, object> BuildWhiteboardShapePayload(MediaSfuWhiteboardShape shape)
        {
            var payload = new Dictionary<string, object>();
            if (shape == null)
            {
                return payload;
            }

            AddIfNotBlank(payload, "type", NormalizeWhiteboardShapeType(shape.Type));
            AddIfHasValue(payload, "x", shape.X);
            AddIfHasValue(payload, "y", shape.Y);
            AddIfHasValue(payload, "x1", shape.X1);
            AddIfHasValue(payload, "y1", shape.Y1);
            AddIfHasValue(payload, "x2", shape.X2);
            AddIfHasValue(payload, "y2", shape.Y2);
            AddIfNotBlank(payload, "color", shape.Color);
            payload["thickness"] = shape.Thickness;
            AddIfNotBlank(payload, "lineType", NormalizeWhiteboardLineType(shape.LineType));
            AddIfNotBlank(payload, "text", shape.Text);
            AddIfNotBlank(payload, "fontFamily", shape.FontFamily);
            if (shape.FontSize > 0f)
            {
                payload["fontSize"] = shape.FontSize;
            }

            AddIfNotBlank(payload, "src", shape.ImageSrc);

            if (shape.Points != null && shape.Points.Count > 0)
            {
                var points = new List<Dictionary<string, object>>();
                foreach (var point in shape.Points)
                {
                    if (point == null)
                    {
                        continue;
                    }

                    points.Add(
                        new Dictionary<string, object>
                        {
                            ["x"] = point.X,
                            ["y"] = point.Y
                        });
                }

                payload["points"] = points;
            }

            return payload;
        }

        private static List<Dictionary<string, object>> BuildWhiteboardShapesPayload(
            IReadOnlyList<MediaSfuWhiteboardShape> shapes)
        {
            var payloadShapes = new List<Dictionary<string, object>>();
            if (shapes == null)
            {
                return payloadShapes;
            }

            foreach (var shape in shapes)
            {
                if (shape != null)
                {
                    payloadShapes.Add(BuildWhiteboardShapePayload(shape));
                }
            }

            return payloadShapes;
        }

        private static string NormalizeWhiteboardActionName(string action)
        {
            if (string.IsNullOrWhiteSpace(action))
            {
                return string.Empty;
            }

            switch (action.Trim().ToLowerInvariant())
            {
                case "uploadimage":
                case "upload-image":
                    return "uploadImage";
                case "deleteshape":
                case "delete-shape":
                    return "deleteShape";
                case "togglebackground":
                case "toggle-background":
                    return "toggleBackground";
                default:
                    return action.Trim();
            }
        }

        private static bool RequiresWhiteboardShapePayload(string action)
        {
            switch (NormalizeWhiteboardActionName(action).ToLowerInvariant())
            {
                case "draw":
                case "shape":
                case "erase":
                case "text":
                case "uploadimage":
                case "deleteshape":
                    return true;
                default:
                    return false;
            }
        }

        private static void ApplyBreakoutRoomsStartedState(
            MediaSfuRoom room,
            IReadOnlyList<List<MediaSfuBreakoutParticipant>> breakoutRooms,
            string eventName)
        {
            if (room == null)
            {
                return;
            }

            var breakout = room.Breakout ?? (room.Breakout = new MediaSfuBreakoutState());
            breakout.Rooms.Clear();
            if (breakoutRooms != null)
            {
                foreach (var breakoutRoom in breakoutRooms)
                {
                    var clonedRoom = new List<MediaSfuBreakoutParticipant>();
                    if (breakoutRoom != null)
                    {
                        foreach (var participant in breakoutRoom)
                        {
                            clonedRoom.Add(CloneBreakoutParticipant(participant));
                        }
                    }

                    breakout.Rooms.Add(clonedRoom);
                }
            }

            breakout.Started = true;
            breakout.Ended = false;
            breakout.Status = string.Equals(eventName, "updateBreakout", StringComparison.OrdinalIgnoreCase)
                ? "updated"
                : "started";
        }

        private static void ApplyBreakoutRoomsStoppedState(MediaSfuRoom room)
        {
            if (room == null)
            {
                return;
            }

            var breakout = room.Breakout ?? (room.Breakout = new MediaSfuBreakoutState());
            breakout.Started = false;
            breakout.Ended = true;
            breakout.Status = "ended";
        }

        private static void ApplyWhiteboardStartedState(
            MediaSfuRoom room,
            IReadOnlyList<MediaSfuWhiteboardUser> users,
            string eventName)
        {
            if (room == null)
            {
                return;
            }

            var whiteboard = room.Whiteboard ?? (room.Whiteboard = new MediaSfuWhiteboardState());
            whiteboard.Users.Clear();
            if (users != null)
            {
                foreach (var user in users)
                {
                    whiteboard.Users.Add(CloneWhiteboardUser(user));
                }
            }

            whiteboard.Started = true;
            whiteboard.Ended = false;
            whiteboard.CanStart = false;
            whiteboard.LastAction = string.Equals(eventName, "updateWhiteboard", StringComparison.OrdinalIgnoreCase)
                ? "updated"
                : "started";
        }

        private static void ApplyWhiteboardStoppedState(MediaSfuRoom room)
        {
            if (room == null)
            {
                return;
            }

            var whiteboard = room.Whiteboard ?? (room.Whiteboard = new MediaSfuWhiteboardState());
            whiteboard.Started = false;
            whiteboard.Ended = true;
            whiteboard.CanStart = true;
            whiteboard.LastAction = "ended";
        }

        private static void ApplyWhiteboardActionState(
            MediaSfuRoom room,
            Dictionary<string, object> eventPayload)
        {
            if (room == null || eventPayload == null)
            {
                return;
            }

            var payloadJson = JsonSerializer.Serialize(eventPayload);
            using var document = JsonDocument.Parse(payloadJson);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object)
            {
                return;
            }

            var action = TryGetString(root, "action");
            if (string.IsNullOrWhiteSpace(action))
            {
                return;
            }

            var actionPayload = TryGetNestedElement(root, "payload");
            var whiteboard = room.Whiteboard ?? (room.Whiteboard = new MediaSfuWhiteboardState());
            whiteboard.LastAction = action;

            switch (action.Trim().ToLowerInvariant())
            {
                case "draw":
                    AppendWhiteboardShape(whiteboard.Shapes, CreateWhiteboardDrawShape(actionPayload));
                    break;
                case "shape":
                    AppendWhiteboardShape(whiteboard.Shapes, CreateWhiteboardShapeFromElement(actionPayload));
                    break;
                case "erase":
                    ApplyWhiteboardErase(whiteboard.Shapes, actionPayload);
                    break;
                case "clear":
                    whiteboard.Shapes.Clear();
                    break;
                case "uploadimage":
                    AppendWhiteboardShape(whiteboard.Shapes, CreateWhiteboardImageShape(actionPayload));
                    break;
                case "togglebackground":
                    whiteboard.UseImageBackground = !whiteboard.UseImageBackground;
                    break;
                case "undo":
                    ApplyWhiteboardUndo(whiteboard);
                    break;
                case "redo":
                    ApplyWhiteboardRedo(whiteboard);
                    break;
                case "text":
                    AppendWhiteboardShape(whiteboard.Shapes, CreateWhiteboardTextShape(actionPayload));
                    break;
                case "deleteshape":
                    ApplyWhiteboardDeleteShape(whiteboard.Shapes, actionPayload);
                    break;
                case "shapes":
                    ReplaceWhiteboardShapes(whiteboard.Shapes, actionPayload);
                    break;
            }
        }

        private static string NormalizeCoHostNameForUpdate(string coHostName)
        {
            return string.IsNullOrWhiteSpace(coHostName)
                ? "No coHost"
                : coHostName.Trim();
        }

        private IReadOnlyList<MediaSfuCoHostResponsibility> ResolveCoHostResponsibilitiesForUpdate(
            string coHostName,
            IReadOnlyList<MediaSfuCoHostResponsibility> coHostResponsibilities)
        {
            if (string.Equals(coHostName, "No coHost", StringComparison.OrdinalIgnoreCase))
            {
                return NormalizeCoHostResponsibilities(coHostResponsibilities);
            }

            if (coHostResponsibilities != null)
            {
                return NormalizeCoHostResponsibilities(coHostResponsibilities);
            }

            if (CurrentRoom != null &&
                string.Equals(CurrentRoom.CoHost, coHostName, StringComparison.OrdinalIgnoreCase) &&
                CurrentRoom.CoHostResponsibilities != null)
            {
                return NormalizeCoHostResponsibilities(CurrentRoom.CoHostResponsibilities);
            }

            return CreateDefaultCoHostResponsibilities();
        }

        private static IReadOnlyList<MediaSfuCoHostResponsibility> NormalizeCoHostResponsibilities(
            IReadOnlyList<MediaSfuCoHostResponsibility> responsibilities)
        {
            var provided = BuildCoHostResponsibilityLookup(responsibilities);
            var normalized = new List<MediaSfuCoHostResponsibility>(CoHostResponsibilityNames.Length);
            foreach (var responsibilityName in CoHostResponsibilityNames)
            {
                if (provided.TryGetValue(responsibilityName, out var responsibility))
                {
                    normalized.Add(CloneCoHostResponsibility(responsibility));
                }
                else
                {
                    normalized.Add(new MediaSfuCoHostResponsibility
                    {
                        Name = responsibilityName,
                        Value = false,
                        Dedicated = false
                    });
                }
            }

            return normalized;
        }

        private static IReadOnlyList<MediaSfuCoHostResponsibility> CreateDefaultCoHostResponsibilities()
        {
            return NormalizeCoHostResponsibilities(Array.Empty<MediaSfuCoHostResponsibility>());
        }

        private static List<Dictionary<string, object>> SerializeCoHostResponsibilities(
            IReadOnlyList<MediaSfuCoHostResponsibility> responsibilities)
        {
            var serialized = new List<Dictionary<string, object>>();
            if (responsibilities == null)
            {
                return serialized;
            }

            foreach (var responsibility in responsibilities)
            {
                if (responsibility == null)
                {
                    continue;
                }

                serialized.Add(
                    new Dictionary<string, object>
                    {
                        ["name"] = responsibility.Name ?? string.Empty,
                        ["value"] = responsibility.Value,
                        ["dedicated"] = responsibility.Dedicated
                    });
            }

            return serialized;
        }

        private void ApplyCoHostUpdateState(
            MediaSfuRoom room,
            string coHostName,
            IReadOnlyList<MediaSfuCoHostResponsibility> responsibilities)
        {
            if (room == null)
            {
                return;
            }

            room.CoHost = coHostName;
            room.CoHostResponsibilities.Clear();
            if (responsibilities != null)
            {
                foreach (var responsibility in responsibilities)
                {
                    room.CoHostResponsibilities.Add(CloneCoHostResponsibility(responsibility));
                }
            }

            foreach (var participant in room.Participants)
            {
                if (participant == null || participant.Role == MediaSfuParticipantRole.Host)
                {
                    continue;
                }

                if (participant.DisplayName.Equals(coHostName, StringComparison.OrdinalIgnoreCase))
                {
                    participant.Role = MediaSfuParticipantRole.CoHost;
                }
                else if (participant.IsLocal || participant.DisplayName.Equals(_lastLocalUserName, StringComparison.OrdinalIgnoreCase))
                {
                    participant.Role = ResolveParticipantRole(_lastLocalIsLevel);
                }
                else if (participant.Role == MediaSfuParticipantRole.CoHost)
                {
                    participant.Role = MediaSfuParticipantRole.Participant;
                }
            }
        }

        private static void ApplyPollEndedState(MediaSfuRoom room, string pollId)
        {
            if (room == null || string.IsNullOrWhiteSpace(pollId))
            {
                return;
            }

            var existingIndex = FindPollIndex(room.Polls, pollId);
            if (existingIndex >= 0 && room.Polls[existingIndex] != null)
            {
                room.Polls[existingIndex].Status = "ended";
            }

            if (NormalizeParticipantKey(room.ActivePoll?.PollId) == NormalizeParticipantKey(pollId))
            {
                room.ActivePoll = ClonePoll(room.ActivePoll) ?? new MediaSfuPoll();
                room.ActivePoll.Status = "ended";
            }

            room.LastPollStatus = "ended";
            room.PollModalVisible = false;
        }

        private static void ApplyPollVoteState(
            MediaSfuRoom room,
            string pollId,
            string voterName,
            int optionIndex)
        {
            if (room == null ||
                string.IsNullOrWhiteSpace(pollId) ||
                string.IsNullOrWhiteSpace(voterName) ||
                optionIndex < 0)
            {
                return;
            }

            var existingIndex = FindPollIndex(room.Polls, pollId);
            var poll = existingIndex >= 0
                ? ClonePoll(room.Polls[existingIndex])
                : string.Equals(room.ActivePoll?.PollId, pollId, StringComparison.OrdinalIgnoreCase)
                    ? ClonePoll(room.ActivePoll)
                    : null;
            if (poll == null)
            {
                return;
            }

            poll.Voters ??= new Dictionary<string, int>();
            poll.Votes ??= new List<int>();

            var voteSlots = poll.Options?.Count ?? 0;
            while (poll.Votes.Count < voteSlots)
            {
                poll.Votes.Add(0);
            }

            if (poll.Voters.TryGetValue(voterName, out var previousChoice) &&
                previousChoice >= 0 &&
                previousChoice < poll.Votes.Count &&
                poll.Votes[previousChoice] > 0)
            {
                poll.Votes[previousChoice]--;
            }

            poll.Voters[voterName] = optionIndex;
            if (optionIndex < poll.Votes.Count)
            {
                poll.Votes[optionIndex]++;
            }

            if (existingIndex >= 0)
            {
                room.Polls[existingIndex] = ClonePoll(poll);
            }
            else
            {
                room.Polls.Add(ClonePoll(poll));
            }

            if (string.Equals(room.ActivePoll?.PollId, pollId, StringComparison.OrdinalIgnoreCase))
            {
                room.ActivePoll = ClonePoll(poll);
            }

            room.LastPollStatus = "voted";
            room.PollModalVisible = false;
        }

        private MediaPermissionRequirement GetMediaPermissionRequirement(MediaSfuTrackKind trackKind)
        {
            var room = CurrentRoom;
            if (room == null)
            {
                return MediaPermissionRequirement.Allow;
            }

            var setting = trackKind switch
            {
                MediaSfuTrackKind.Audio => room.AudioSetting,
                MediaSfuTrackKind.Video => room.VideoSetting,
                MediaSfuTrackKind.Screen => room.ScreenshareSetting,
                _ => string.Empty
            };

            return NormalizeMediaPermissionRequirement(setting);
        }

        private static MediaPermissionRequirement NormalizeMediaPermissionRequirement(string setting)
        {
            if (string.IsNullOrWhiteSpace(setting))
            {
                return MediaPermissionRequirement.Allow;
            }

            return setting.Trim().ToLowerInvariant() switch
            {
                "allow" => MediaPermissionRequirement.Allow,
                "approval" => MediaPermissionRequirement.Approval,
                "disallow" => MediaPermissionRequirement.Disallow,
                _ => MediaPermissionRequirement.Disallow
            };
        }

        private static string GetTrackPermissionLabel(MediaSfuTrackKind trackKind)
        {
            return trackKind switch
            {
                MediaSfuTrackKind.Audio => "audio",
                MediaSfuTrackKind.Video => "video",
                MediaSfuTrackKind.Screen => "screen share",
                _ => "media"
            };
        }

        private static string GetTrackKindCodeName(MediaSfuTrackKind trackKind)
        {
            return trackKind switch
            {
                MediaSfuTrackKind.Audio => nameof(MediaSfuTrackKind.Audio),
                MediaSfuTrackKind.Video => nameof(MediaSfuTrackKind.Video),
                MediaSfuTrackKind.Screen => nameof(MediaSfuTrackKind.Screen),
                _ => nameof(MediaSfuTrackKind.Video)
            };
        }

        private static string GetTrackActionName(MediaSfuTrackKind trackKind)
        {
            return trackKind switch
            {
                MediaSfuTrackKind.Audio => "MicrophoneEnabled",
                MediaSfuTrackKind.Video => "CameraEnabled",
                MediaSfuTrackKind.Screen => "ScreenShareEnabled",
                _ => "MediaEnabled"
            };
        }

        private enum MediaPermissionRequirement
        {
            Allow,
            Approval,
            Disallow
        }

        private bool HasGrantedLocalRequest(MediaSfuTrackKind trackKind)
        {
            var localRequests = CurrentRoom?.LocalRequests;
            if (localRequests == null)
            {
                return false;
            }

            return trackKind switch
            {
                MediaSfuTrackKind.Audio => localRequests.AudioActionGranted,
                MediaSfuTrackKind.Video => localRequests.VideoActionGranted,
                MediaSfuTrackKind.Screen => localRequests.ScreenshareActionGranted,
                _ => false
            };
        }

        private void ConsumeGrantedLocalRequest(MediaSfuTrackKind trackKind)
        {
            UpdateCurrentRoomState(
                nextRoom =>
                {
                    var localRequests = nextRoom.LocalRequests ?? (nextRoom.LocalRequests = new MediaSfuLocalRequestState());
                    switch (trackKind)
                    {
                        case MediaSfuTrackKind.Audio:
                            localRequests.AudioActionGranted = false;
                            break;
                        case MediaSfuTrackKind.Video:
                            localRequests.VideoActionGranted = false;
                            break;
                        case MediaSfuTrackKind.Screen:
                            localRequests.ScreenshareActionGranted = false;
                            break;
                    }
                });
        }

        private static void MarkLocalRequestPending(MediaSfuRoom room, MediaSfuTrackKind trackKind)
        {
            if (room == null)
            {
                return;
            }

            var localRequests = room.LocalRequests ?? (room.LocalRequests = new MediaSfuLocalRequestState());
            switch (trackKind)
            {
                case MediaSfuTrackKind.Audio:
                    localRequests.AudioRequestState = "pending";
                    localRequests.AudioActionGranted = false;
                    localRequests.AudioRequestRetryAtEpochMs = null;
                    break;
                case MediaSfuTrackKind.Video:
                    localRequests.VideoRequestState = "pending";
                    localRequests.VideoActionGranted = false;
                    localRequests.VideoRequestRetryAtEpochMs = null;
                    break;
                case MediaSfuTrackKind.Screen:
                    localRequests.ScreenshareRequestState = "pending";
                    localRequests.ScreenshareActionGranted = false;
                    localRequests.ScreenshareRequestRetryAtEpochMs = null;
                    break;
            }
        }

        private static string GetLocalRequestState(MediaSfuLocalRequestState localRequests, MediaSfuTrackKind trackKind)
        {
            if (localRequests == null)
            {
                return "none";
            }

            return trackKind switch
            {
                MediaSfuTrackKind.Audio => localRequests.AudioRequestState ?? "none",
                MediaSfuTrackKind.Video => localRequests.VideoRequestState ?? "none",
                MediaSfuTrackKind.Screen => localRequests.ScreenshareRequestState ?? "none",
                _ => "none"
            };
        }

        private static long? GetLocalRequestRetryAtEpochMs(MediaSfuLocalRequestState localRequests, MediaSfuTrackKind trackKind)
        {
            if (localRequests == null)
            {
                return null;
            }

            return trackKind switch
            {
                MediaSfuTrackKind.Audio => localRequests.AudioRequestRetryAtEpochMs,
                MediaSfuTrackKind.Video => localRequests.VideoRequestRetryAtEpochMs,
                MediaSfuTrackKind.Screen => localRequests.ScreenshareRequestRetryAtEpochMs,
                _ => null
            };
        }

        private string ResolveParticipantRequestId(SocketTransportSession session)
        {
            var namespaceSocketId = TryParseSocketNamespaceSid(session?.NamespaceConnectPacket);
            if (!string.IsNullOrWhiteSpace(namespaceSocketId))
            {
                return namespaceSocketId;
            }

            return LastSocketHandshake?.SessionId ?? string.Empty;
        }

        private static string TryParseSocketNamespaceSid(string connectPacket)
        {
            if (string.IsNullOrWhiteSpace(connectPacket))
            {
                return string.Empty;
            }

            var payloadStart = connectPacket.IndexOf('{');
            if (payloadStart < 0 || payloadStart >= connectPacket.Length)
            {
                return string.Empty;
            }

            try
            {
                using var document = JsonDocument.Parse(connectPacket.Substring(payloadStart));
                var root = document.RootElement;
                return root.ValueKind == JsonValueKind.Object
                    ? TryGetString(root, "sid") ?? string.Empty
                    : string.Empty;
            }
            catch (JsonException)
            {
                return string.Empty;
            }
        }

        private static bool TryResolveParticipantRequestMetadata(
            MediaSfuTrackKind trackKind,
            out string requestIcon,
            out string requestLabel)
        {
            switch (trackKind)
            {
                case MediaSfuTrackKind.Audio:
                    requestIcon = "fa-microphone";
                    requestLabel = "audio";
                    return true;
                case MediaSfuTrackKind.Video:
                    requestIcon = "fa-video";
                    requestLabel = "video";
                    return true;
                case MediaSfuTrackKind.Screen:
                    requestIcon = "fa-desktop";
                    requestLabel = "screen share";
                    return true;
                default:
                    requestIcon = string.Empty;
                    requestLabel = string.Empty;
                    return false;
            }
        }

        private static string ResolveHostControlType(MediaSfuHostControlType controlType)
        {
            return controlType switch
            {
                MediaSfuHostControlType.Audio => "audio",
                MediaSfuHostControlType.Video => "video",
                MediaSfuHostControlType.ScreenShare => "screenshare",
                MediaSfuHostControlType.Chat => "chat",
                MediaSfuHostControlType.All => "all",
                _ => "all"
            };
        }

        public void Dispose()
        {
            _disposed = true;
            CloseSocketTransportSynchronously(markDisconnected: false);
            if (_ownsHttpClient)
            {
                _httpClient.Dispose();
            }

            _socketSendLock.Dispose();
        }

        private static HttpClient CreateHttpClient(MediaSfuClientOptions options, out bool ownsHttpClient)
        {
            if (options.ServerCertificateValidationCallback == null)
            {
                ownsHttpClient = false;
                return SharedHttpClient;
            }

            var handler = new HttpClientHandler
            {
                ServerCertificateCustomValidationCallback = (request, certificate, chain, sslPolicyErrors) =>
                    options.ServerCertificateValidationCallback(
                        request?.RequestUri,
                        certificate,
                        chain,
                        sslPolicyErrors)
            };

            ownsHttpClient = true;
            return new HttpClient(handler);
        }

        private void EnsureNotDisposed()
        {
            if (_disposed)
            {
                throw new ObjectDisposedException(nameof(MediaSfuClient));
            }
        }

        private void ValidateClientConfiguration()
        {
            var requiresCloudCredentials = Options.ConnectMediaSfu &&
                                           Options.ConnectionMode != MediaSfuConnectionMode.CommunityEdition;

            if (requiresCloudCredentials)
            {
                if (Options.Credentials == null ||
                    string.IsNullOrWhiteSpace(Options.Credentials.ApiUserName) ||
                    string.IsNullOrWhiteSpace(Options.Credentials.ApiKey))
                {
                    throw new InvalidOperationException(
                        "MediaSFU cloud operations require ApiUserName and ApiKey in MediaSfuClientOptions.Credentials.");
                }
            }

            var requiresLocalLink = Options.ConnectionMode != MediaSfuConnectionMode.Cloud &&
                                    string.IsNullOrWhiteSpace(Options.LocalLink);

            if (requiresLocalLink)
            {
                throw new InvalidOperationException(
                    "CommunityEdition and Hybrid modes require MediaSfuClientOptions.LocalLink.");
            }
        }

        public MediaSfuSocketConnectionPlan BuildSocketConnectionPlan()
        {
            EnsureNotDisposed();

            if (CurrentRoom == null || string.IsNullOrWhiteSpace(CurrentRoom.RoomName))
            {
                throw new InvalidOperationException(
                    "BuildSocketConnectionPlan requires a successful CreateRoomAsync or JoinRoomAsync result first.");
            }

            return BuildSocketConnectionPlan(ResolveSocketConnectionTarget());
        }

        private async Task<MediaSfuOperationResult<MediaSfuRoom>> ExecuteRoomOperationAsync(
            MediaSfuConnectionState pendingState,
            string operation,
            string endpoint,
            IReadOnlyDictionary<string, object> payload,
            string localUserName,
            string localIsLevel,
            CancellationToken cancellationToken)
        {
            SetConnectionState(pendingState);

            try
            {
                var result = await SendRoomRequestAsync(
                    operation,
                    endpoint,
                    payload,
                    localUserName,
                    localIsLevel,
                    cancellationToken).ConfigureAwait(false);

                if (result.Success && result.Value != null)
                {
                    _lastLocalUserName = localUserName ?? string.Empty;
                    _lastLocalIsLevel = localIsLevel ?? string.Empty;
                    SetCurrentRoom(result.Value);
                }

                return result;
            }
            finally
            {
                if (ConnectionState == pendingState)
                {
                    SetConnectionState(MediaSfuConnectionState.Idle);
                }
            }
        }

        private async Task<MediaSfuOperationResult<MediaSfuRoom>> SendRoomRequestAsync(
            string operation,
            string endpoint,
            IReadOnlyDictionary<string, object> payload,
            string localUserName,
            string localIsLevel,
            CancellationToken cancellationToken)
        {
            using var requestMessage = new HttpRequestMessage(HttpMethod.Post, endpoint);
            requestMessage.Headers.Authorization = new AuthenticationHeaderValue(
                "Bearer",
                $"{Options.Credentials.ApiUserName}:{Options.Credentials.ApiKey}");
            requestMessage.Content = new StringContent(
                JsonSerializer.Serialize(payload),
                Encoding.UTF8,
                "application/json");

            try
            {
                using var response = await _httpClient.SendAsync(requestMessage, cancellationToken).ConfigureAwait(false);
                var bodyText = await response.Content.ReadAsStringAsync().ConfigureAwait(false);

                if (response.IsSuccessStatusCode)
                {
                    var room = TryParseRoom(bodyText, localUserName, localIsLevel);
                    if (room != null)
                    {
                        return MediaSfuOperationResult<MediaSfuRoom>.FromSuccess(room);
                    }

                    const string parseError = "Room operation succeeded, but the response payload could not be parsed.";
                    PublishError(operation, parseError, bodyText);
                    return MediaSfuOperationResult<MediaSfuRoom>.FromFailure(parseError, bodyText);
                }

                var (error, detail) = ParseErrorResponse(bodyText, response.ReasonPhrase);
                PublishError(operation, error, detail);
                return MediaSfuOperationResult<MediaSfuRoom>.FromFailure(error, detail);
            }
            catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
            {
                throw;
            }
            catch (Exception error)
            {
                PublishError(operation, error.Message, error.ToString());
                return MediaSfuOperationResult<MediaSfuRoom>.FromFailure(
                    $"{operation} failed: {error.Message}",
                    error.ToString());
            }
        }

        private string ResolveCreateEndpoint()
        {
            if (Options.ConnectionMode == MediaSfuConnectionMode.CommunityEdition ||
                Options.ConnectionMode == MediaSfuConnectionMode.Hybrid)
            {
                return $"{Options.LocalLink.TrimEnd('/')}/createRoom";
            }

            return ResolveCloudRoomsEndpoint();
        }

        private string ResolveJoinEndpoint()
        {
            if (Options.ConnectionMode == MediaSfuConnectionMode.CommunityEdition ||
                Options.ConnectionMode == MediaSfuConnectionMode.Hybrid)
            {
                return $"{Options.LocalLink.TrimEnd('/')}/joinRoom";
            }

            return ResolveCloudRoomsEndpoint();
        }

        private string ResolveCloudRoomsEndpoint()
        {
            if (string.IsNullOrWhiteSpace(Options.BaseUrl))
            {
                return DefaultCloudRoomsEndpoint;
            }

            var normalized = Options.BaseUrl.TrimEnd('/');
            return normalized.EndsWith("/v1/rooms", StringComparison.OrdinalIgnoreCase)
                ? normalized
                : $"{normalized}/v1/rooms";
        }

        private MediaSfuSocketConnectionPlan BuildSocketConnectionPlan(SocketConnectionTarget target)
        {
            return new MediaSfuSocketConnectionPlan
            {
                BaseUrl = target.BaseUrl,
                Namespace = target.Namespace,
                RoomName = CurrentRoom.RoomName,
                ConnectTimeoutMs = Options.SocketConnectTimeoutMs,
                AckTimeoutMs = Options.SocketAckTimeoutMs,
                UsesSecureTransport = target.UsesSecureTransport
            };
        }

        private async Task<MediaSfuSocketHandshake> ProbeSocketHandshakeAsync(
            SocketConnectionTarget target,
            CancellationToken cancellationToken)
        {
            var transportUrl = BuildPollingHandshakeUrl(target);
            using var requestMessage = new HttpRequestMessage(HttpMethod.Get, transportUrl);
            using var response = await _httpClient.SendAsync(requestMessage, cancellationToken).ConfigureAwait(false);
            var bodyText = await response.Content.ReadAsStringAsync().ConfigureAwait(false);

            if (!response.IsSuccessStatusCode)
            {
                var (_, detail) = ParseErrorResponse(bodyText, response.ReasonPhrase);
                throw new InvalidOperationException(
                    $"Socket polling handshake failed with status {(int)response.StatusCode}: {detail}");
            }

            return ParseSocketHandshake(bodyText, transportUrl);
        }

        private async Task<SocketTransportSession> ConnectSocketTransportAsync(
            SocketConnectionTarget target,
            MediaSfuSocketHandshake handshake,
            CancellationToken cancellationToken)
        {
            var transportUrl = BuildWebSocketTransportUri(target);
            var socket = new ClientWebSocket();
            socket.Options.KeepAliveInterval = TimeSpan.FromMilliseconds(
                handshake.PingIntervalMs > 0 ? handshake.PingIntervalMs : 25000);

            try
            {
                using var timeoutCancellation = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
                if (Options.SocketConnectTimeoutMs > 0)
                {
                    timeoutCancellation.CancelAfter(Options.SocketConnectTimeoutMs);
                }

                await ConnectWebSocketAsync(socket, transportUrl, timeoutCancellation.Token).ConfigureAwait(false);

                var openPacket = await ReceiveNextTextPacketAsync(socket, timeoutCancellation.Token).ConfigureAwait(false);
                if (!openPacket.StartsWith("0", StringComparison.Ordinal))
                {
                    throw new InvalidOperationException(
                        $"WebSocket transport did not return an Engine.IO open packet: {openPacket}");
                }

                await SendSocketPacketAsync(socket, BuildSocketIoConnectPacket(target.Namespace), timeoutCancellation.Token)
                    .ConfigureAwait(false);

                var connectPacket = await WaitForSocketNamespaceConnectionAsync(
                        socket,
                        target.Namespace,
                        timeoutCancellation.Token)
                    .ConfigureAwait(false);

                return new SocketTransportSession
                {
                    Socket = socket,
                    TransportUrl = transportUrl.ToString(),
                    Namespace = target.Namespace,
                    ReceiveLoopCancellation = new CancellationTokenSource(),
                    NamespaceConnectPacket = connectPacket
                };
            }
            catch
            {
                socket.Dispose();
                throw;
            }
        }

        private async Task ConnectWebSocketAsync(
            ClientWebSocket socket,
            Uri transportUrl,
            CancellationToken cancellationToken)
        {
            var validationCallback = Options.ServerCertificateValidationCallback;
            if (validationCallback == null)
            {
                await socket.ConnectAsync(transportUrl, cancellationToken).ConfigureAwait(false);
                return;
            }

            await ServerCertificateValidationCallbackGate.WaitAsync(cancellationToken).ConfigureAwait(false);
            var previousCallback = ServicePointManager.ServerCertificateValidationCallback;
            try
            {
                ServicePointManager.ServerCertificateValidationCallback = (sender, certificate, chain, sslPolicyErrors) =>
                {
                    var requestUri = ResolveCertificateValidationUri(sender) ?? transportUrl;
                    return validationCallback(requestUri, certificate, chain, sslPolicyErrors);
                };

                await socket.ConnectAsync(transportUrl, cancellationToken).ConfigureAwait(false);
            }
            finally
            {
                ServicePointManager.ServerCertificateValidationCallback = previousCallback;
                ServerCertificateValidationCallbackGate.Release();
            }
        }

        private static Uri ResolveCertificateValidationUri(object sender)
        {
            return sender is HttpWebRequest request ? request.RequestUri : null;
        }

        private async Task<MediaSfuRoomValidation> ValidateJoinedRoomAsync(
            SocketTransportSession session,
            CancellationToken cancellationToken)
        {
            if (!ShouldValidateJoinedRoom())
            {
                return null;
            }

            var validationPayload = new Dictionary<string, object>
            {
                ["roomName"] = CurrentRoom.RoomName,
                ["islevel"] = _lastLocalIsLevel,
                ["member"] = _lastLocalUserName,
                ["sec"] = CurrentRoom.Secret,
                ["apiUserName"] = CurrentRoom.ApiUserName
            };

            var acknowledgmentPayload = await EmitSocketEventWithAckAsync(
                    session,
                    "joinRoom",
                    validationPayload,
                    cancellationToken)
                .ConfigureAwait(false);
            var acknowledgment = ParseRoomValidation(acknowledgmentPayload);

            if (!acknowledgment.HasRtpCapabilities)
            {
                if (acknowledgment.Banned)
                {
                    throw new InvalidOperationException("User is banned.");
                }

                if (acknowledgment.Suspended)
                {
                    throw new InvalidOperationException("User is suspended.");
                }

                if (acknowledgment.NoAdmin)
                {
                    throw new InvalidOperationException("Host has not joined the room yet.");
                }

                throw new InvalidOperationException("Failed to join room.");
            }

            return acknowledgment;
        }

        private async Task JoinConsumeRoomAsync(
            SocketTransportSession session,
            CancellationToken cancellationToken)
        {
            if (_consumeRoomJoined || !ShouldValidateJoinedRoom())
            {
                return;
            }

            var apiUserName = !string.IsNullOrWhiteSpace(CurrentRoom?.ApiUserName)
                ? CurrentRoom.ApiUserName
                : Options.Credentials?.ApiUserName ?? string.Empty;
            var consumeApiToken = ResolveConsumeApiToken();
            var joinAttempts = new List<KeyValuePair<string, Dictionary<string, object>>>();

            var preferredJoinConRoomSecret = consumeApiToken;
            joinAttempts.Add(
                new KeyValuePair<string, Dictionary<string, object>>(
                    "joinConRoom",
                    new Dictionary<string, object>
                    {
                        ["roomName"] = CurrentRoom.RoomName,
                        ["islevel"] = _lastLocalIsLevel,
                        ["member"] = _lastLocalUserName,
                        ["sec"] = preferredJoinConRoomSecret,
                        ["apiUserName"] = apiUserName
                    }));

            var apiKeyFallbackSecret = Options.Credentials?.ApiKey ?? string.Empty;
            if (!string.IsNullOrWhiteSpace(apiKeyFallbackSecret) &&
                !string.Equals(apiKeyFallbackSecret, preferredJoinConRoomSecret, StringComparison.Ordinal))
            {
                joinAttempts.Add(
                    new KeyValuePair<string, Dictionary<string, object>>(
                        "joinConRoom",
                        new Dictionary<string, object>
                        {
                            ["roomName"] = CurrentRoom.RoomName,
                            ["islevel"] = _lastLocalIsLevel,
                            ["member"] = _lastLocalUserName,
                            ["sec"] = apiKeyFallbackSecret,
                            ["apiUserName"] = apiUserName
                        }));
            }

            var failedAttempts = new List<string>();
            foreach (var joinAttempt in joinAttempts)
            {
                var attemptSummary = DescribeConsumeJoinAttempt(joinAttempt.Key, joinAttempt.Value);
                string acknowledgmentPayload;
                try
                {
                    Console.WriteLine(
                        "MediaSfu receive " + joinAttempt.Key + " start transport=" + (session?.TransportUrl ?? string.Empty) +
                        " namespace=" + (session?.Namespace ?? string.Empty) +
                        " roomName=" + (CurrentRoom?.RoomName ?? string.Empty) +
                        " attempt=" + attemptSummary);

                    acknowledgmentPayload = await EmitSocketEventWithAckAsync(
                            session,
                            joinAttempt.Key,
                            joinAttempt.Value,
                            cancellationToken)
                        .ConfigureAwait(false);

                    Console.WriteLine(
                        "MediaSfu receive " + joinAttempt.Key + " completed transport=" + (session?.TransportUrl ?? string.Empty) +
                        " namespace=" + (session?.Namespace ?? string.Empty) +
                        " attempt=" + attemptSummary);

                    ApplyConsumeJoinAcknowledgment(joinAttempt.Key, acknowledgmentPayload);
                    return;
                }
                catch (Exception error)
                {
                    Console.WriteLine(
                        "MediaSfu receive " + joinAttempt.Key + " failed transport=" + (session?.TransportUrl ?? string.Empty) +
                        " namespace=" + (session?.Namespace ?? string.Empty) +
                        " attempt=" + attemptSummary +
                        " reason=" + error.Message);
                    failedAttempts.Add(joinAttempt.Key + " (" + attemptSummary + "): " + error.Message);
                }
            }

            var roomRecvIps = LastRoomValidation?.RoomRecvIps == null
                ? string.Empty
                : string.Join(",", LastRoomValidation.RoomRecvIps);
            throw new InvalidOperationException(
                "consume room join failed on transport=" + (session?.TransportUrl ?? string.Empty) +
                " namespace=" + (session?.Namespace ?? string.Empty) +
                " roomRecvIps=" + roomRecvIps +
                " lastConsumeOut=" + _lastConsumeSocketOutboundPacket +
                " lastConsumeIn=" + _lastConsumeSocketInboundPacket +
                ". Attempts=" + string.Join(" | ", failedAttempts));
        }

        private string DescribeConsumeJoinAttempt(
            string operation,
            Dictionary<string, object> payload)
        {
            if (payload == null)
            {
                return operation + " payload=null";
            }

            payload.TryGetValue("roomName", out var roomNameValue);
            payload.TryGetValue("member", out var memberValue);
            payload.TryGetValue("islevel", out var isLevelValue);
            payload.TryGetValue("apiUserName", out var apiUserNameValue);

            var credentialKey = payload.ContainsKey("apiToken")
                ? "apiToken"
                : payload.ContainsKey("sec")
                    ? "sec"
                    : "none";
            var credentialValue = credentialKey == "none"
                ? string.Empty
                : Convert.ToString(payload[credentialKey]);

            return operation +
                " credentialKey=" + credentialKey +
                " credentialSource=" + DescribeConsumeCredentialSource(credentialValue) +
                " roomName=" + Convert.ToString(roomNameValue ?? string.Empty) +
                " member=" + Convert.ToString(memberValue ?? string.Empty) +
                " islevel=" + Convert.ToString(isLevelValue ?? string.Empty) +
                " apiUserName=" + (string.IsNullOrWhiteSpace(Convert.ToString(apiUserNameValue)) ? "empty" : "present");
        }

        private string DescribeConsumeCredentialSource(string credentialValue)
        {
            if (string.IsNullOrWhiteSpace(credentialValue))
            {
                return "empty";
            }

            if (!string.IsNullOrWhiteSpace(Options.Credentials?.ApiKey) &&
                string.Equals(credentialValue, Options.Credentials.ApiKey, StringComparison.Ordinal))
            {
                return "apiKey(len=" + credentialValue.Length + ")";
            }

            if (!string.IsNullOrWhiteSpace(CurrentRoom?.Secret) &&
                string.Equals(credentialValue, CurrentRoom.Secret, StringComparison.Ordinal))
            {
                return "roomSecret(len=" + credentialValue.Length + ")";
            }

            return "custom(len=" + credentialValue.Length + ")";
        }

        private void ApplyConsumeJoinAcknowledgment(string operation, string acknowledgmentPayload)
        {
            var root = ParseAckPayloadRoot(acknowledgmentPayload, operation);
            if (TryGetNestedElement(root, "error") is JsonElement errorElement)
            {
                throw new InvalidOperationException(
                    operation + " returned an error: " + DescribeAckError(errorElement));
            }

            if (TryGetNestedElement(root, "rtpCapabilities", "rtpCapabilities_") is JsonElement rtpCapabilities &&
                !LastRoomValidation.HasRtpCapabilities)
            {
                LastRoomValidation = CloneRoomValidation(LastRoomValidation) ?? new MediaSfuRoomValidation();
                LastRoomValidation.HasRtpCapabilities = true;
                LastRoomValidation.RtpCapabilitiesJson = rtpCapabilities.GetRawText();
            }

            var success = TryGetBool(root, "success");
            var hasRtpCapabilities = TryGetNestedElement(root, "rtpCapabilities", "rtpCapabilities_") != null;
            if (success == false || (!hasRtpCapabilities && success != true))
            {
                throw new InvalidOperationException(
                    operation + " acknowledgment did not include RTP capabilities. " +
                    DescribeAckPayloadShape(root));
            }

            _consumeRoomJoined = true;

            if (CurrentRoom?.Participants != null && CurrentRoom.Participants.Count > 0)
            {
                ReconcileRemoteProducerState(Array.Empty<MediaSfuParticipant>(), CurrentRoom.Participants);
            }
        }

        private string ResolveConsumeApiToken()
        {
            if (!string.IsNullOrWhiteSpace(CurrentRoom?.Secret))
            {
                return CurrentRoom.Secret;
            }

            return Options.Credentials?.ApiKey ?? string.Empty;
        }

        private async Task<SocketTransportSession> EnsureConsumeSocketTransportAsync(
            MediaSfuRoomValidation roomValidation,
            CancellationToken cancellationToken)
        {
            var consumeSocketIps = await ResolveConsumeSocketIpsAsync(roomValidation, cancellationToken)
                .ConfigureAwait(false);
            if (consumeSocketIps.Count == 0)
            {
                return null;
            }

            var session = _consumeSocketTransportSession;
            if (session != null && session.Socket?.State == WebSocketState.Open)
            {
                return session;
            }

            Console.WriteLine(
                "MediaSfu receive consume target candidates=" + string.Join(",", consumeSocketIps));

            var target = BuildConsumeSocketConnectionTarget(consumeSocketIps[0]);
            var handshake = await ProbeSocketHandshakeAsync(target, cancellationToken).ConfigureAwait(false);
            session = await ConnectSocketTransportAsync(target, handshake, cancellationToken).ConfigureAwait(false);
            _consumeSocketTransportSession = session;
            session.ReceiveLoopTask = Task.Run(
                () => ReceiveSocketPacketsAsync(session),
                session.ReceiveLoopCancellation.Token);
            return session;
        }

        private async Task<List<string>> ResolveConsumeSocketIpsAsync(
            MediaSfuRoomValidation roomValidation,
            CancellationToken cancellationToken)
        {
            var fallbackIps = NormalizeConsumeSocketIps(roomValidation?.RoomRecvIps);
            if (fallbackIps.Count == 0)
            {
                return ResolveConsumeSocketIpsFromDomains();
            }

            for (var attempt = 0; attempt < 30; attempt++)
            {
                cancellationToken.ThrowIfCancellationRequested();

                var consumingDomainIps = ResolveConsumeSocketIpsFromDomains();
                if (consumingDomainIps.Count > 0)
                {
                    return consumingDomainIps;
                }

                await Task.Delay(100, cancellationToken).ConfigureAwait(false);
            }

            return fallbackIps;
        }

        private List<string> ResolveConsumeSocketIpsFromDomains()
        {
            var consumingDomains = CurrentRoom?.ConsumingDomains;
            if (consumingDomains?.Domains == null || consumingDomains.Domains.Count == 0)
            {
                return new List<string>();
            }

            Dictionary<string, string> altDomains = null;
            if (consumingDomains.HasAltDomains && !string.IsNullOrWhiteSpace(consumingDomains.AltDomainsJson))
            {
                try
                {
                    using var document = JsonDocument.Parse(consumingDomains.AltDomainsJson);
                    var root = document.RootElement;
                    if (root.ValueKind == JsonValueKind.Object &&
                        root.TryGetProperty("data", out var dataElement) &&
                        dataElement.ValueKind == JsonValueKind.Object)
                    {
                        altDomains = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
                        foreach (var property in dataElement.EnumerateObject())
                        {
                            if (property.Value.ValueKind == JsonValueKind.String)
                            {
                                var value = property.Value.GetString();
                                if (!string.IsNullOrWhiteSpace(value))
                                {
                                    altDomains[property.Name] = value;
                                }
                            }
                        }
                    }
                }
                catch (JsonException)
                {
                }
            }

            var resolvedIps = new List<string>();
            foreach (var domain in consumingDomains.Domains)
            {
                if (string.IsNullOrWhiteSpace(domain))
                {
                    continue;
                }

                if (altDomains != null && altDomains.TryGetValue(domain, out var mappedIp) &&
                    !string.IsNullOrWhiteSpace(mappedIp))
                {
                    if (!resolvedIps.Exists(value => string.Equals(value, mappedIp, StringComparison.OrdinalIgnoreCase)))
                    {
                        resolvedIps.Add(mappedIp);
                    }

                    continue;
                }

                if (!resolvedIps.Exists(value => string.Equals(value, domain, StringComparison.OrdinalIgnoreCase)))
                {
                    resolvedIps.Add(domain);
                }
            }

            return resolvedIps;
        }

        private static List<string> NormalizeConsumeSocketIps(IReadOnlyList<string> roomRecvIps)
        {
            var normalizedIps = new List<string>();
            if (roomRecvIps == null)
            {
                return normalizedIps;
            }

            foreach (var roomRecvIp in roomRecvIps)
            {
                if (string.IsNullOrWhiteSpace(roomRecvIp) ||
                    string.Equals(roomRecvIp, "none", StringComparison.OrdinalIgnoreCase) ||
                    normalizedIps.Exists(value => string.Equals(value, roomRecvIp, StringComparison.OrdinalIgnoreCase)))
                {
                    continue;
                }

                normalizedIps.Add(roomRecvIp);
            }

            return normalizedIps;
        }

        private SocketConnectionTarget BuildConsumeSocketConnectionTarget(string roomRecvIp)
        {
            if (string.IsNullOrWhiteSpace(roomRecvIp))
            {
                throw new InvalidOperationException("roomRecvIP was empty.");
            }

            var baseUrl = roomRecvIp.StartsWith("http://", StringComparison.OrdinalIgnoreCase) ||
                          roomRecvIp.StartsWith("https://", StringComparison.OrdinalIgnoreCase)
                ? roomRecvIp
                : $"https://{roomRecvIp}.mediasfu.com";

            var credentialedUrl = AppendSocketCredentialQuery(EnsureMediaNamespace(baseUrl.TrimEnd('/')));
            if (!Uri.TryCreate(credentialedUrl, UriKind.Absolute, out var uri))
            {
                throw new InvalidOperationException($"Unable to resolve a valid consume socket URL from '{credentialedUrl}'.");
            }

            var namespacePath = string.IsNullOrWhiteSpace(uri.AbsolutePath) || uri.AbsolutePath == "/"
                ? "/"
                : uri.AbsolutePath.TrimEnd('/');

            return new SocketConnectionTarget
            {
                BaseUrl = uri.GetLeftPart(UriPartial.Authority),
                Namespace = namespacePath,
                Query = uri.Query,
                UsesSecureTransport =
                    uri.Scheme.Equals("https", StringComparison.OrdinalIgnoreCase) ||
                    uri.Scheme.Equals("wss", StringComparison.OrdinalIgnoreCase)
            };
        }

        private SocketConnectionTarget ResolveSocketConnectionTarget()
        {
            var rawUrl = ResolveSocketEndpointUrl();
            if (!Uri.TryCreate(rawUrl, UriKind.Absolute, out var uri))
            {
                throw new InvalidOperationException($"Unable to resolve a valid socket URL from '{rawUrl}'.");
            }

            var namespacePath = string.IsNullOrWhiteSpace(uri.AbsolutePath) || uri.AbsolutePath == "/"
                ? "/"
                : uri.AbsolutePath.TrimEnd('/');

            return new SocketConnectionTarget
            {
                BaseUrl = uri.GetLeftPart(UriPartial.Authority),
                Namespace = namespacePath,
                Query = uri.Query,
                UsesSecureTransport =
                    uri.Scheme.Equals("https", StringComparison.OrdinalIgnoreCase) ||
                    uri.Scheme.Equals("wss", StringComparison.OrdinalIgnoreCase)
            };
        }

        private string ResolveSocketEndpointUrl()
        {
            string rawUrl;
            if (!string.IsNullOrWhiteSpace(CurrentRoom?.Link))
            {
                rawUrl = CurrentRoom.Link;
            }
            else if (!string.IsNullOrWhiteSpace(Options.LocalLink))
            {
                rawUrl = Options.LocalLink;
            }
            else if (!string.IsNullOrWhiteSpace(Options.BaseUrl))
            {
                var normalized = Options.BaseUrl.TrimEnd('/');
                rawUrl = normalized.EndsWith("/v1/rooms", StringComparison.OrdinalIgnoreCase)
                    ? normalized[..^"/v1/rooms".Length]
                    : normalized;
            }
            else
            {
                rawUrl = DefaultCloudBaseUrl;
            }

            var baseUrl = ResolveSocketBaseUrl(rawUrl);
            var endpoint = EnsureMediaNamespace(baseUrl);
            return AppendSocketCredentialQuery(endpoint);
        }

        private static string ResolveSocketBaseUrl(string rawUrl)
        {
            var trimmed = rawUrl?.Trim();
            if (string.IsNullOrWhiteSpace(trimmed))
            {
                return DefaultCloudBaseUrl;
            }

            var normalized = trimmed.Contains("://", StringComparison.Ordinal)
                ? trimmed
                : "https://" + trimmed;

            if (!Uri.TryCreate(normalized, UriKind.Absolute, out var uri))
            {
                return DefaultCloudBaseUrl;
            }

            var scheme = uri.Scheme.Equals("ws", StringComparison.OrdinalIgnoreCase)
                ? "http"
                : uri.Scheme.Equals("wss", StringComparison.OrdinalIgnoreCase)
                    ? "https"
                    : uri.Scheme;
            var builder = new UriBuilder(uri)
            {
                Scheme = scheme,
                Path = "/",
                Query = string.Empty,
                Fragment = string.Empty
            };

            return builder.Uri.ToString().TrimEnd('/');
        }

        private static string EnsureMediaNamespace(string baseUrl)
        {
            var sanitized = (baseUrl ?? string.Empty).TrimEnd('/');
            return sanitized.EndsWith("/media", StringComparison.OrdinalIgnoreCase)
                ? sanitized
                : sanitized + "/media";
        }

        private string AppendSocketCredentialQuery(string endpoint)
        {
            var apiUserName = !string.IsNullOrWhiteSpace(CurrentRoom?.ApiUserName)
                ? CurrentRoom.ApiUserName
                : Options.Credentials?.ApiUserName ?? string.Empty;
            var apiKey = Options.Credentials?.ApiKey ?? string.Empty;

            if (string.IsNullOrWhiteSpace(apiUserName) && string.IsNullOrWhiteSpace(apiKey))
            {
                return endpoint;
            }

            if (!Uri.TryCreate(endpoint, UriKind.Absolute, out var uri))
            {
                return endpoint;
            }

            var queryParts = new List<string>();
            var existingQuery = uri.Query.TrimStart('?');
            if (!string.IsNullOrWhiteSpace(existingQuery))
            {
                queryParts.Add(existingQuery);
            }

            if (!string.IsNullOrWhiteSpace(apiUserName))
            {
                queryParts.Add("apiUserName=" + Uri.EscapeDataString(apiUserName));
            }

            if (!string.IsNullOrWhiteSpace(apiKey))
            {
                queryParts.Add("apiKey=" + Uri.EscapeDataString(apiKey));
            }

            var builder = new UriBuilder(uri)
            {
                Query = string.Join("&", queryParts)
            };

            return builder.Uri.ToString().TrimEnd('/');
        }

        private static string BuildPollingHandshakeUrl(SocketConnectionTarget target)
        {
            var builder = new UriBuilder(target.BaseUrl)
            {
                Path = "/socket.io/",
                Query = BuildPollingQuery(target.Query)
            };

            return builder.Uri.ToString();
        }

        private static Uri BuildWebSocketTransportUri(SocketConnectionTarget target)
        {
            var builder = new UriBuilder(target.BaseUrl)
            {
                Scheme = target.UsesSecureTransport ? "wss" : "ws",
                Path = "/socket.io/",
                Query = BuildWebSocketQuery(target.Query)
            };

            return builder.Uri;
        }

        private static string BuildSocketEventPacket(
            string socketNamespace,
            int? ackId,
            string eventName,
            object payload)
        {
            var serializedPayload = JsonSerializer.Serialize(new object[] { eventName, payload });
            var builder = new StringBuilder("42");
            if (!string.Equals(socketNamespace, "/", StringComparison.Ordinal))
            {
                builder.Append(socketNamespace);
                builder.Append(',');
            }

            if (ackId.HasValue)
            {
                builder.Append(ackId.Value);
            }

            builder.Append(serializedPayload);
            return builder.ToString();
        }

        private static string BuildPollingQuery(string existingQuery)
        {
            var query = new StringBuilder();
            query.Append("EIO=4&transport=polling&t=");
            query.Append(DateTimeOffset.UtcNow.ToUnixTimeMilliseconds());

            var trimmedExisting = existingQuery?.TrimStart('?');
            if (!string.IsNullOrWhiteSpace(trimmedExisting))
            {
                query.Append('&');
                query.Append(trimmedExisting);
            }

            return query.ToString();
        }

        private static string BuildWebSocketQuery(string existingQuery)
        {
            var query = new StringBuilder("EIO=4&transport=websocket");
            var trimmedExisting = existingQuery?.TrimStart('?');
            if (!string.IsNullOrWhiteSpace(trimmedExisting))
            {
                query.Append('&');
                query.Append(trimmedExisting);
            }

            return query.ToString();
        }

        private async Task SendSocketEventAsync(
            SocketTransportSession session,
            string eventName,
            object payload,
            CancellationToken cancellationToken)
        {
            var packet = BuildSocketEventPacket(session.Namespace, null, eventName, payload);
            await SendSocketPacketAsync(session.Socket, packet, cancellationToken).ConfigureAwait(false);
        }

        private SocketTransportSession ResolveRoomControlSocketSession()
        {
            return IsSocketSessionOpen(_consumeSocketTransportSession)
                ? _consumeSocketTransportSession
                : _socketTransportSession;
        }

        private bool HasOpenRoomSocketSession()
        {
            return IsSocketSessionOpen(_socketTransportSession) ||
                   IsSocketSessionOpen(_consumeSocketTransportSession);
        }

        private static bool IsSocketSessionOpen(SocketTransportSession session)
        {
            return session?.Socket != null && session.Socket.State == WebSocketState.Open;
        }

        private async Task<string> EmitRoomControlSocketEventWithAckAsync(
            string eventName,
            object payload,
            bool preferConsumeSession,
            CancellationToken cancellationToken)
        {
            Exception firstError = null;
            foreach (var session in ResolveRoomControlSocketSessions(preferConsumeSession))
            {
                try
                {
                    return await EmitSocketEventWithAckAsync(
                            session,
                            eventName,
                            payload,
                            cancellationToken)
                        .ConfigureAwait(false);
                }
                catch (Exception error)
                {
                    if (cancellationToken.IsCancellationRequested)
                    {
                        throw;
                    }

                    firstError = error;
                }
            }

            if (firstError != null)
            {
                throw firstError;
            }

            throw new InvalidOperationException(
                "Room control events require an active socket connection from ConnectMediaAsync.");
        }

        private async Task<BooleanSocketAck> EmitBooleanRoomControlSocketEventWithAckAsync(
            string operation,
            string eventName,
            object payload,
            bool preferConsumeSession,
            CancellationToken cancellationToken)
        {
            Exception firstError = null;
            BooleanSocketAck firstRetryableFailure = null;
            foreach (var session in ResolveRoomControlSocketSessions(preferConsumeSession))
            {
                try
                {
                    var acknowledgmentPayload = await EmitSocketEventWithAckAsync(
                            session,
                            eventName,
                            payload,
                            cancellationToken)
                        .ConfigureAwait(false);
                    var acknowledgment = ParseBooleanSocketAck(acknowledgmentPayload, operation);
                    if (acknowledgment.Success)
                    {
                        return acknowledgment;
                    }

                    if (!IsRetryableRoomControlAckFailure(acknowledgment))
                    {
                        return acknowledgment;
                    }

                    firstRetryableFailure = firstRetryableFailure ?? acknowledgment;
                }
                catch (Exception error)
                {
                    if (cancellationToken.IsCancellationRequested)
                    {
                        throw;
                    }

                    firstError = firstError ?? error;
                }
            }

            if (firstRetryableFailure != null)
            {
                return firstRetryableFailure;
            }

            if (firstError != null)
            {
                throw firstError;
            }

            throw new InvalidOperationException(
                "Room control events require an active socket connection from ConnectMediaAsync.");
        }

        private IReadOnlyList<SocketTransportSession> ResolveRoomControlSocketSessions(bool preferConsumeSession)
        {
            var sessions = new List<SocketTransportSession>(2);
            if (preferConsumeSession)
            {
                AddOpenSocketSession(sessions, _consumeSocketTransportSession);
                AddOpenSocketSession(sessions, _socketTransportSession);
            }
            else
            {
                AddOpenSocketSession(sessions, _socketTransportSession);
                AddOpenSocketSession(sessions, _consumeSocketTransportSession);
            }

            return sessions;
        }

        private static void AddOpenSocketSession(
            ICollection<SocketTransportSession> sessions,
            SocketTransportSession session)
        {
            if (!IsSocketSessionOpen(session) || sessions.Contains(session))
            {
                return;
            }

            sessions.Add(session);
        }

        private static bool IsRetryableRoomControlAckFailure(BooleanSocketAck acknowledgment)
        {
            return acknowledgment != null &&
                   string.Equals(acknowledgment.Reason, "Invalid parameters", StringComparison.OrdinalIgnoreCase);
        }

        private async Task<string> EmitSocketEventWithAckAsync(
            SocketTransportSession session,
            string eventName,
            object payload,
            CancellationToken cancellationToken)
        {
            var ackId = Interlocked.Increment(ref _nextSocketAckId);
            var pendingAck = RegisterPendingSocketAck(ackId);
            var packet = BuildSocketEventPacket(session.Namespace, ackId, eventName, payload);

            try
            {
                TraceConsumeSocketPacket(session, "out", packet, eventName);
                await SendSocketPacketAsync(session.Socket, packet, cancellationToken).ConfigureAwait(false);

                var timeoutTask = Options.SocketAckTimeoutMs > 0
                    ? Task.Delay(Options.SocketAckTimeoutMs, cancellationToken)
                    : Task.Delay(Timeout.Infinite, cancellationToken);
                var completedTask = await Task.WhenAny(pendingAck.Task, timeoutTask).ConfigureAwait(false);
                if (completedTask == pendingAck.Task)
                {
                    return await pendingAck.Task.ConfigureAwait(false);
                }

                cancellationToken.ThrowIfCancellationRequested();
                throw new TimeoutException($"Timed out waiting for Socket.IO ack for '{eventName}'.");
            }
            finally
            {
                RemovePendingSocketAck(ackId, pendingAck);
            }
        }

        private TaskCompletionSource<string> RegisterPendingSocketAck(int ackId)
        {
            var pendingAck = new TaskCompletionSource<string>(TaskCreationOptions.RunContinuationsAsynchronously);

            lock (_pendingSocketAcksGate)
            {
                _pendingSocketAcks[ackId] = pendingAck;
            }

            return pendingAck;
        }

        private bool TryResolvePendingSocketAck(int ackId, string payload)
        {
            TaskCompletionSource<string> pendingAck;

            lock (_pendingSocketAcksGate)
            {
                if (!_pendingSocketAcks.TryGetValue(ackId, out pendingAck))
                {
                    return false;
                }

                _pendingSocketAcks.Remove(ackId);
            }

            pendingAck.TrySetResult(payload);
            return true;
        }

        private void RemovePendingSocketAck(int ackId, TaskCompletionSource<string> pendingAck)
        {
            lock (_pendingSocketAcksGate)
            {
                if (_pendingSocketAcks.TryGetValue(ackId, out var existing) && ReferenceEquals(existing, pendingAck))
                {
                    _pendingSocketAcks.Remove(ackId);
                }
            }
        }

        private void FailPendingSocketAcks(Exception error)
        {
            List<TaskCompletionSource<string>> pendingAcks;

            lock (_pendingSocketAcksGate)
            {
                pendingAcks = new List<TaskCompletionSource<string>>(_pendingSocketAcks.Values);
                _pendingSocketAcks.Clear();
            }

            foreach (var pendingAck in pendingAcks)
            {
                pendingAck.TrySetException(error);
            }
        }

        private async Task ReceiveSocketPacketsAsync(SocketTransportSession session)
        {
            var cancellationToken = session.ReceiveLoopCancellation.Token;

            try
            {
                while (!cancellationToken.IsCancellationRequested && session.Socket.State == WebSocketState.Open)
                {
                    var packet = await ReceiveNextTextPacketAsync(session.Socket, cancellationToken).ConfigureAwait(false);
                    if (string.IsNullOrWhiteSpace(packet))
                    {
                        continue;
                    }

                    if (packet == "2")
                    {
                        TraceConsumeSocketPacket(session, "in", packet);
                        await SendSocketPacketAsync(session.Socket, "3", cancellationToken).ConfigureAwait(false);
                        continue;
                    }

                    TraceConsumeSocketPacket(session, "in", packet);

                    if (packet.StartsWith("43", StringComparison.Ordinal))
                    {
                        var metadata = ParseSocketPacketMetadata(packet.Substring(2));
                        if (metadata.AckId.HasValue)
                        {
                            TryResolvePendingSocketAck(metadata.AckId.Value, metadata.Payload);
                        }

                        continue;
                    }

                    if (packet.StartsWith("42", StringComparison.Ordinal))
                    {
                        try
                        {
                            DispatchSocketEvent(ParseSocketEventPacket(packet.Substring(2)));
                        }
                        catch (Exception error)
                        {
                            PublishError("SocketEvent", error.Message, packet);
                        }

                        continue;
                    }

                    if (packet.StartsWith("41", StringComparison.Ordinal))
                    {
                        break;
                    }

                    if (packet.StartsWith("44", StringComparison.Ordinal))
                    {
                        PublishError("SocketTransport", "Socket.IO transport returned an error packet.", packet);
                        FailPendingSocketAcks(new InvalidOperationException($"Socket.IO transport error: {packet}"));
                    }
                }
            }
            catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
            {
            }
            catch (Exception error)
            {
                if (ReferenceEquals(_socketTransportSession, session) && !_disposed)
                {
                    PublishError("SocketTransport", error.Message, error.ToString());
                    SetConnectionState(MediaSfuConnectionState.Failed);
                }
            }
            finally
            {
                FailPendingSocketAcks(new InvalidOperationException("Socket transport closed before pending Socket.IO acknowledgments completed."));

                if (ReferenceEquals(_socketTransportSession, session))
                {
                    _socketTransportSession = null;
                    DisposeSocketTransportSession(session);
                    if (!_disposed && ConnectionState != MediaSfuConnectionState.Failed)
                    {
                        SetConnectionState(MediaSfuConnectionState.Disconnected);
                    }
                }
            }
        }

        private void TraceConsumeSocketPacket(
            SocketTransportSession session,
            string direction,
            string packet,
            string eventName = null)
        {
            if (!ReferenceEquals(_consumeSocketTransportSession, session) || string.IsNullOrWhiteSpace(packet))
            {
                return;
            }

            var summary = string.IsNullOrWhiteSpace(eventName)
                ? $"consume {direction} packet"
                : $"consume {direction} packet event={eventName}";
            var packetPreview = RedactSocketPacketPreview(packet.Length > 512 ? packet.Substring(0, 512) : packet);

            if (string.Equals(direction, "in", StringComparison.Ordinal))
            {
                _lastConsumeSocketInboundPacket = packetPreview;
            }
            else
            {
                _lastConsumeSocketOutboundPacket = packetPreview;
            }

            Console.WriteLine("MediaSfu " + summary + " :: " + packetPreview);
        }

        private static string RedactSocketPacketPreview(string packetPreview)
        {
            if (string.IsNullOrWhiteSpace(packetPreview))
            {
                return packetPreview;
            }

            var redacted = Regex.Replace(
                packetPreview,
                "(\\\"(?:apiKey|apiToken|sec)\\\"\\s*:\\s*\\\")[^\\\"]+",
                "$1<redacted>");
            return Regex.Replace(
                redacted,
                "((?:apiKey|apiToken)=)[^&\\s]+",
                "$1<redacted>");
        }

        private async Task CloseSocketTransportAsync(bool markDisconnected = true)
        {
            _consumeRoomJoined = false;

            var consumeSession = _consumeSocketTransportSession;
            _consumeSocketTransportSession = null;

            var session = _socketTransportSession;
            if (session == null)
            {
                if (consumeSession == null)
                {
                    return;
                }
            }

            _socketTransportSession = null;
            FailPendingSocketAcks(new InvalidOperationException("Socket transport closed."));

            try
            {
                if (consumeSession != null)
                {
                    consumeSession.ReceiveLoopCancellation.Cancel();

                    if (consumeSession.Socket.State == WebSocketState.Open ||
                        consumeSession.Socket.State == WebSocketState.CloseReceived)
                    {
                        await consumeSession.Socket.CloseAsync(
                                WebSocketCloseStatus.NormalClosure,
                                "Client closing consume transport",
                                CancellationToken.None)
                            .ConfigureAwait(false);
                    }

                    if (consumeSession.ReceiveLoopTask != null)
                    {
                        await consumeSession.ReceiveLoopTask.ConfigureAwait(false);
                    }
                }

                if (session != null)
                {
                    session.ReceiveLoopCancellation.Cancel();

                    if (session.Socket.State == WebSocketState.Open ||
                        session.Socket.State == WebSocketState.CloseReceived)
                    {
                        await session.Socket.CloseAsync(
                                WebSocketCloseStatus.NormalClosure,
                                "Client closing transport",
                                CancellationToken.None)
                            .ConfigureAwait(false);
                    }

                    if (session.ReceiveLoopTask != null)
                    {
                        await session.ReceiveLoopTask.ConfigureAwait(false);
                    }
                }
            }
            catch
            {
            }
            finally
            {
                if (consumeSession != null)
                {
                    DisposeSocketTransportSession(consumeSession);
                }

                if (session != null)
                {
                    DisposeSocketTransportSession(session);
                }

                if (markDisconnected && !_disposed)
                {
                    SetConnectionState(MediaSfuConnectionState.Disconnected);
                }
            }
        }

        private void CloseSocketTransportSynchronously(bool markDisconnected)
        {
            try
            {
                CloseSocketTransportAsync(markDisconnected).GetAwaiter().GetResult();
            }
            catch
            {
            }
        }

        private static void DisposeSocketTransportSession(SocketTransportSession session)
        {
            if (session == null)
            {
                return;
            }

            try
            {
                session.ReceiveLoopCancellation.Dispose();
            }
            catch
            {
            }

            try
            {
                session.Socket.Dispose();
            }
            catch
            {
            }
        }

        private static async Task<string> WaitForSocketNamespaceConnectionAsync(
            ClientWebSocket socket,
            string socketNamespace,
            CancellationToken cancellationToken)
        {
            for (var attempt = 0; attempt < 6; attempt += 1)
            {
                var packet = await ReceiveNextTextPacketAsync(socket, cancellationToken).ConfigureAwait(false);
                if (packet == "2")
                {
                    continue;
                }

                if (IsSocketNamespaceConnected(packet, socketNamespace))
                {
                    return packet;
                }

                if (packet.StartsWith("44", StringComparison.Ordinal) ||
                    packet.StartsWith("41", StringComparison.Ordinal))
                {
                    throw new InvalidOperationException(
                        $"Socket.IO namespace connection failed for '{socketNamespace}': {packet}");
                }
            }

            throw new InvalidOperationException(
                $"Timed out waiting for Socket.IO namespace connection on '{socketNamespace}'.");
        }

        private static bool IsSocketNamespaceConnected(string packet, string socketNamespace)
        {
            if (string.IsNullOrWhiteSpace(packet) || !packet.StartsWith("40", StringComparison.Ordinal))
            {
                return false;
            }

            if (socketNamespace == "/")
            {
                return packet == "40" || packet.StartsWith("40{", StringComparison.Ordinal);
            }

            var prefix = $"40{socketNamespace}";
            return packet == prefix || packet.StartsWith(prefix + ",", StringComparison.Ordinal);
        }

        private bool ShouldValidateJoinedRoom()
        {
            return CurrentRoom != null &&
                   CurrentRoom.RoomName.StartsWith("s", StringComparison.OrdinalIgnoreCase) ||
                   CurrentRoom != null && CurrentRoom.RoomName.StartsWith("p", StringComparison.OrdinalIgnoreCase)
                ? !string.IsNullOrWhiteSpace(CurrentRoom.Secret) &&
                  CurrentRoom.Secret.Length == 64 &&
                  !string.IsNullOrWhiteSpace(CurrentRoom.ApiUserName) &&
                  CurrentRoom.ApiUserName.Length >= 6 &&
                  !string.IsNullOrWhiteSpace(_lastLocalUserName) &&
                  AlphanumericPattern.IsMatch(_lastLocalUserName) &&
                  !string.IsNullOrWhiteSpace(_lastLocalIsLevel) &&
                  (_lastLocalIsLevel == "0" || _lastLocalIsLevel == "1" || _lastLocalIsLevel == "2")
                : false;
        }

        private static SocketPacketMetadata ParseSocketPacketMetadata(string rawMeta)
        {
            var index = 0;
            var parsedNamespace = "/";

            if (!string.IsNullOrWhiteSpace(rawMeta) && rawMeta.StartsWith("/", StringComparison.Ordinal))
            {
                var commaIndex = rawMeta.IndexOf(',');
                if (commaIndex >= 0)
                {
                    parsedNamespace = rawMeta.Substring(0, commaIndex);
                    index = commaIndex + 1;
                }
                else
                {
                    parsedNamespace = rawMeta;
                    index = rawMeta.Length;
                }
            }

            var idStart = index;
            while (index < rawMeta.Length && char.IsDigit(rawMeta[index]))
            {
                index += 1;
            }

            var ackId = index > idStart
                ? int.TryParse(rawMeta.Substring(idStart, index - idStart), out var parsedAckId) ? parsedAckId : (int?)null
                : null;
            var payload = index < rawMeta.Length ? rawMeta.Substring(index) : string.Empty;

            return new SocketPacketMetadata
            {
                Namespace = parsedNamespace,
                AckId = ackId,
                Payload = payload
            };
        }

        private static SocketEventPacket ParseSocketEventPacket(string rawMeta)
        {
            var metadata = ParseSocketPacketMetadata(rawMeta);
            if (string.IsNullOrWhiteSpace(metadata.Payload))
            {
                throw new InvalidOperationException("Socket.IO event payload was empty.");
            }

            using var document = JsonDocument.Parse(metadata.Payload);
            if (document.RootElement.ValueKind != JsonValueKind.Array || document.RootElement.GetArrayLength() == 0)
            {
                throw new InvalidOperationException($"Socket.IO event payload had an unexpected shape: {metadata.Payload}");
            }

            var eventNameElement = document.RootElement[0];
            if (eventNameElement.ValueKind != JsonValueKind.String)
            {
                throw new InvalidOperationException($"Socket.IO event name was not a string: {metadata.Payload}");
            }

            var payloadJson = document.RootElement.GetArrayLength() > 1
                ? document.RootElement[1].GetRawText()
                : "null";

            return new SocketEventPacket
            {
                Namespace = NormalizeSocketNamespace(metadata.Namespace),
                AckId = metadata.AckId,
                EventName = eventNameElement.GetString() ?? string.Empty,
                PayloadJson = payloadJson,
                RawPayload = metadata.Payload
            };
        }

        private static MediaSfuRoomValidation ParseRoomValidation(string payload)
        {
            if (string.IsNullOrWhiteSpace(payload))
            {
                throw new InvalidOperationException("joinRoom acknowledgment returned an empty payload.");
            }

            using var document = JsonDocument.Parse(payload);
            if (document.RootElement.ValueKind != JsonValueKind.Array || document.RootElement.GetArrayLength() == 0)
            {
                throw new InvalidOperationException($"joinRoom acknowledgment returned an unexpected payload: {payload}");
            }

            var root = document.RootElement[0];
            if (root.ValueKind != JsonValueKind.Object)
            {
                throw new InvalidOperationException($"joinRoom acknowledgment returned a non-object payload: {payload}");
            }

            var roomValidation = new MediaSfuRoomValidation
            {
                HasRtpCapabilities = TryGetNestedElement(root, "rtpCapabilities", "rtpCapabilities_") != null,
                RtpCapabilitiesJson = TryGetNestedElement(root, "rtpCapabilities", "rtpCapabilities_")?.GetRawText() ?? string.Empty,
                SecureCode = TryGetString(root, "secureCode") ?? string.Empty,
                RecordOnly = TryGetBool(root, "recordOnly") ?? false,
                IsHost = TryGetBool(root, "isHost") ?? false,
                SafeRoom = TryGetBool(root, "safeRoom") ?? false,
                AutoStartSafeRoom = TryGetBool(root, "autoStartSafeRoom") ?? false,
                SafeRoomStarted = TryGetBool(root, "safeRoomStarted") ?? false,
                SafeRoomEnded = TryGetBool(root, "safeRoomEnded") ?? false,
                Reason = TryGetString(root, "reason") ?? string.Empty,
                Banned = TryGetBool(root, "banned") ?? false,
                Suspended = TryGetBool(root, "suspended") ?? false,
                NoAdmin = TryGetBool(root, "noAdmin") ?? false,
                RawPayload = root.GetRawText()
            };

            if (TryGetNestedElement(root, "roomRecvIPs", "roomRecvIPs_") is JsonElement roomRecvIps &&
                roomRecvIps.ValueKind == JsonValueKind.Array)
            {
                foreach (var value in roomRecvIps.EnumerateArray())
                {
                    if (value.ValueKind == JsonValueKind.String)
                    {
                        var item = value.GetString();
                        if (!string.IsNullOrWhiteSpace(item))
                        {
                            roomValidation.RoomRecvIps.Add(item);
                        }
                    }
                }
            }

            return roomValidation;
        }

        private static RecordingControlAck ParseRecordingControlAck(string payload, string operation)
        {
            if (string.IsNullOrWhiteSpace(payload))
            {
                throw new InvalidOperationException($"{operation} acknowledgment returned an empty payload.");
            }

            using var document = JsonDocument.Parse(payload);
            JsonElement root;
            if (document.RootElement.ValueKind == JsonValueKind.Array)
            {
                if (document.RootElement.GetArrayLength() == 0)
                {
                    throw new InvalidOperationException($"{operation} acknowledgment returned an unexpected payload: {payload}");
                }

                root = document.RootElement[0];
            }
            else
            {
                root = document.RootElement;
            }

            if (root.ValueKind != JsonValueKind.Object)
            {
                throw new InvalidOperationException($"{operation} acknowledgment returned a non-object payload: {payload}");
            }

            return new RecordingControlAck
            {
                Success = TryGetBool(root, "success") ?? false,
                Reason = TryGetString(root, "reason") ?? string.Empty,
                RecordState = TryGetString(root, "recordState", "state") ?? string.Empty,
                PauseCount = TryGetInt(root, "pauseCount"),
                RawPayload = root.GetRawText()
            };
        }

        private static BooleanSocketAck ParseBooleanSocketAck(string payload, string operation)
        {
            var root = ParseAckPayloadRoot(payload, operation);

            if (root.ValueKind != JsonValueKind.Object)
            {
                throw new InvalidOperationException($"{operation} acknowledgment returned a non-object payload: {payload}");
            }

            var reason = TryGetString(root, "reason", "message") ?? string.Empty;
            if (TryGetNestedElement(root, "error") is JsonElement errorElement)
            {
                reason = DescribeAckError(errorElement);
            }

            return new BooleanSocketAck
            {
                Success = TryGetBool(root, "success") ?? false,
                Reason = reason,
                RawPayload = root.GetRawText()
            };
        }

        private static MediaSfuWebRtcTransport ParseWebRtcTransportAck(string payload, string operation)
        {
            var root = ParseAckPayloadRoot(payload, operation);
            if (TryGetNestedElement(root, "params") is JsonElement paramsElement &&
                paramsElement.ValueKind == JsonValueKind.Object)
            {
                root = paramsElement;
            }

            if (TryGetNestedElement(root, "error") is JsonElement errorElement)
            {
                throw new InvalidOperationException(
                    $"{operation} returned an error: {DescribeAckError(errorElement)}");
            }

            var transportId = TryGetString(root, "id");
            if (string.IsNullOrWhiteSpace(transportId))
            {
                throw new InvalidOperationException(
                    $"{operation} acknowledgment did not include a transport id.");
            }

            return new MediaSfuWebRtcTransport
            {
                Id = transportId,
                IceParametersJson = TryGetNestedElement(root, "iceParameters")?.GetRawText() ?? string.Empty,
                IceCandidatesJson = TryGetNestedElement(root, "iceCandidates")?.GetRawText() ?? string.Empty,
                DtlsParametersJson = TryGetNestedElement(root, "dtlsParameters")?.GetRawText() ?? string.Empty,
                SctpParametersJson = TryGetNestedElement(root, "sctpParameters")?.GetRawText() ?? string.Empty,
                RawPayload = root.GetRawText()
            };
        }

        private static MediaSfuProduceResponse ParseProduceTrackAck(string payload, string operation)
        {
            var root = ParseAckPayloadRoot(payload, operation);
            if (TryGetNestedElement(root, "params") is JsonElement paramsElement &&
                paramsElement.ValueKind == JsonValueKind.Object)
            {
                root = paramsElement;
            }

            if (TryGetNestedElement(root, "error") is JsonElement errorElement)
            {
                throw new InvalidOperationException(
                    $"{operation} returned an error: {DescribeAckError(errorElement)}");
            }

            var producerId = root.ValueKind == JsonValueKind.String
                ? root.GetString()
                : TryGetString(root, "id", "producerId");
            if (string.IsNullOrWhiteSpace(producerId))
            {
                throw new InvalidOperationException(
                    $"{operation} acknowledgment did not include a producer id.");
            }

            return new MediaSfuProduceResponse
            {
                ProducerId = producerId,
                RawPayload = root.GetRawText()
            };
        }

        private static MediaSfuConsumeResponse ParseConsumeTrackAck(string payload, string operation)
        {
            var root = ParseAckPayloadRoot(payload, operation);
            if (TryGetNestedElement(root, "params") is JsonElement paramsElement &&
                paramsElement.ValueKind == JsonValueKind.Object)
            {
                root = paramsElement;
            }

            if (TryGetNestedElement(root, "error") is JsonElement errorElement)
            {
                throw new InvalidOperationException(
                    $"{operation} returned an error: {DescribeAckError(errorElement)}");
            }

            var consumerId = TryGetString(root, "id", "consumerId");
            if (string.IsNullOrWhiteSpace(consumerId))
            {
                throw new InvalidOperationException(
                    $"{operation} acknowledgment did not include a consumer id.");
            }

            return new MediaSfuConsumeResponse
            {
                ConsumerId = consumerId,
                ProducerId = TryGetString(root, "producerId") ?? string.Empty,
                Kind = TryGetString(root, "kind") ?? string.Empty,
                RtpParametersJson = TryGetNestedElement(root, "rtpParameters")?.GetRawText() ?? string.Empty,
                ServerConsumerId = TryGetString(root, "serverConsumerId") ?? string.Empty,
                RawPayload = root.GetRawText()
            };
        }

        private static JsonElement ParseAckPayloadRoot(string payload, string operation)
        {
            if (string.IsNullOrWhiteSpace(payload))
            {
                throw new InvalidOperationException($"{operation} acknowledgment returned an empty payload.");
            }

            using var document = JsonDocument.Parse(payload);
            if (document.RootElement.ValueKind == JsonValueKind.Array)
            {
                if (document.RootElement.GetArrayLength() == 0)
                {
                    throw new InvalidOperationException($"{operation} acknowledgment returned an unexpected payload: {payload}");
                }

                return document.RootElement[0].Clone();
            }

            return document.RootElement.Clone();
        }

        private static string DescribeAckError(JsonElement errorElement)
        {
            if (errorElement.ValueKind == JsonValueKind.String)
            {
                return errorElement.GetString() ?? "unknown error";
            }

            if (errorElement.ValueKind == JsonValueKind.Object)
            {
                return TryGetString(errorElement, "message", "reason", "error") ?? errorElement.GetRawText();
            }

            return errorElement.GetRawText();
        }

        private static string DescribeAckPayloadShape(JsonElement root)
        {
            if (root.ValueKind != JsonValueKind.Object)
            {
                return "ackShape=" + root.ValueKind;
            }

            var keys = new List<string>();
            foreach (var property in root.EnumerateObject())
            {
                keys.Add(property.Name);
            }

            var success = TryGetBool(root, "success");
            return "ackKeys=" + string.Join(",", keys) +
                " success=" + (success.HasValue ? success.Value.ToString() : "null") +
                " hasError=" + (TryGetNestedElement(root, "error") != null) +
                " hasRtpCapabilities=" + (TryGetNestedElement(root, "rtpCapabilities", "rtpCapabilities_") != null);
        }

        private void DispatchSocketEvent(SocketEventPacket socketEvent)
        {
            SocketEventReceived?.Invoke(
                new MediaSfuSocketEvent
                {
                    Namespace = socketEvent.Namespace,
                    EventName = socketEvent.EventName,
                    AckId = socketEvent.AckId,
                    PayloadJson = socketEvent.PayloadJson,
                    RawPayload = socketEvent.RawPayload
                }
            );

            switch (socketEvent.EventName)
            {
                case "allMembers":
                case "allMembersRest":
                    HandleSocketParticipantSnapshot(socketEvent.PayloadJson);
                    break;
                case "personJoined":
                    HandleSocketPersonJoined(socketEvent.PayloadJson);
                    break;
                case "userWaiting":
                    HandleSocketUserWaiting(socketEvent.PayloadJson);
                    break;
                case "allWaitingRoomMembers":
                    HandleSocketAllWaitingRoomMembers(socketEvent.PayloadJson);
                    break;
                case "participantRequested":
                    HandleSocketParticipantRequested(socketEvent.PayloadJson);
                    break;
                case "updatedCoHost":
                    HandleSocketUpdatedCoHost(socketEvent.PayloadJson);
                    break;
                case "screenProducerId":
                    HandleSocketScreenProducerId(socketEvent.PayloadJson);
                    break;
                case "hostRequestResponse":
                    HandleSocketHostRequestResponse(socketEvent.PayloadJson);
                    break;
                case "pollUpdated":
                    HandleSocketPollUpdated(socketEvent.PayloadJson);
                    break;
                case "breakoutRoomUpdated":
                    HandleSocketBreakoutRoomUpdated(socketEvent.PayloadJson);
                    break;
                case "roomRecordParams":
                    HandleSocketRoomRecordParams(socketEvent.PayloadJson);
                    break;
                case "startRecords":
                    HandleSocketStartRecords();
                    break;
                case "reInitiateRecording":
                    HandleSocketReInitiateRecording();
                    break;
                case "RecordingNotice":
                case "recordingNotice":
                    HandleSocketRecordingNotice(socketEvent.PayloadJson);
                    break;
                case "ban":
                case "banParticipant":
                    HandleSocketBanParticipant(socketEvent.PayloadJson);
                    break;
                case "updateMediaSettings":
                    HandleSocketUpdateMediaSettings(socketEvent.PayloadJson);
                    break;
                case "meetingEnded":
                    HandleSocketMeetingEnded(socketEvent.PayloadJson);
                    break;
                case "controlMediaHost":
                    HandleSocketControlMediaHost(socketEvent.PayloadJson);
                    break;
                case "disconnectUserSelf":
                    HandleSocketDisconnectUserSelf(socketEvent.PayloadJson);
                    break;
                case "receiveMessage":
                    HandleSocketReceiveMessage(socketEvent.PayloadJson);
                    break;
                case "newProducer":
                case "new-producer":
                    HandleSocketRemoteProducerAvailable(socketEvent.PayloadJson, isPipeProducer: false);
                    break;
                case "newPipeProducer":
                case "new-pipe-producer":
                    HandleSocketRemoteProducerAvailable(socketEvent.PayloadJson, isPipeProducer: true);
                    break;
                case "producerClosed":
                case "producer-closed":
                    HandleSocketRemoteProducerClosed(socketEvent.PayloadJson);
                    break;
                case "meetingTimeRemaining":
                    HandleSocketMeetingTimeRemaining(socketEvent.PayloadJson);
                    break;
                case "meetingStillThere":
                    HandleSocketMeetingStillThere();
                    break;
                case "updateConsumingDomains":
                    HandleSocketUpdateConsumingDomains(socketEvent.PayloadJson);
                    break;
                case "whiteboardAction":
                    HandleSocketWhiteboardAction(socketEvent.PayloadJson);
                    break;
                case "whiteboardUpdated":
                    HandleSocketWhiteboardUpdated(socketEvent.PayloadJson);
                    break;
                case "timeLeftRecording":
                    HandleSocketTimeLeftRecording(socketEvent.PayloadJson);
                    break;
                case "stoppedRecording":
                    HandleSocketStoppedRecording(socketEvent.PayloadJson);
                    break;
                case "producer-media-paused":
                    HandleSocketProducerMediaPaused(socketEvent.PayloadJson);
                    break;
                case "producer-media-resumed":
                    HandleSocketProducerMediaResumed(socketEvent.PayloadJson);
                    break;
                case "producer-media-closed":
                    HandleSocketProducerMediaClosed(socketEvent.PayloadJson);
                    break;
            }
        }

        private void HandleSocketParticipantSnapshot(string payloadJson)
        {
            if (CurrentRoom == null || string.IsNullOrWhiteSpace(payloadJson) || payloadJson == "null")
            {
                return;
            }

            using var document = JsonDocument.Parse(payloadJson);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object)
            {
                return;
            }

            var hasMembers = root.TryGetProperty("members", out var membersElement) &&
                             membersElement.ValueKind == JsonValueKind.Array;
            var hasRequests = root.TryGetProperty("requests", out var requestsElement) &&
                              requestsElement.ValueKind == JsonValueKind.Array;
            var hasSettings = root.TryGetProperty("settings", out var settingsElement) &&
                              settingsElement.ValueKind == JsonValueKind.Array;
            var hasCoHost = root.TryGetProperty("coHost", out var coHostElement) &&
                            coHostElement.ValueKind == JsonValueKind.String;
            var responsibilitiesElement = TryGetCoHostResponsibilitiesElement(root);
            var hasCoHostResponsibilities = responsibilitiesElement.HasValue;

            if (!hasMembers && !hasRequests && !hasSettings && !hasCoHost && !hasCoHostResponsibilities)
            {
                return;
            }

            var participants = new List<MediaSfuParticipant>();
            if (hasMembers)
            {
                foreach (var memberElement in membersElement.EnumerateArray())
                {
                    var participant = TryParseParticipant(memberElement);
                    if (participant != null)
                    {
                        participants.Add(participant);
                    }
                }
            }

            var requests = hasRequests ? ParseRoomRequests(requestsElement) : null;
            var settings = hasSettings ? ParseStringList(settingsElement) : null;
            var coHost = hasCoHost ? coHostElement.GetString() ?? string.Empty : null;
            var responsibilities = hasCoHostResponsibilities
                ? ParseCoHostResponsibilities(responsibilitiesElement.Value)
                : null;

            UpdateCurrentRoomState(
                nextRoom =>
                {
                    if (hasMembers)
                    {
                        nextRoom.Participants.Clear();
                        foreach (var participant in participants)
                        {
                            nextRoom.Participants.Add(CloneParticipant(participant));
                        }

                        nextRoom.MembersReceived = true;
                    }

                    if (requests != null)
                    {
                        nextRoom.PendingRequests.Clear();
                        foreach (var request in requests)
                        {
                            nextRoom.PendingRequests.Add(CloneRoomRequest(request));
                        }
                    }

                    if (settings != null)
                    {
                        ApplyMediaSettings(nextRoom, settings);
                    }

                    if (coHost != null || responsibilities != null)
                    {
                        ApplyCoHostUpdateState(
                            nextRoom,
                            coHost ?? nextRoom.CoHost,
                            responsibilities ?? nextRoom.CoHostResponsibilities);
                    }
                });

            MaybePrimeRemoteMediaBackendSocketSession();
        }

        private void MaybePrimeRemoteMediaBackendSocketSession()
        {
            if (!ShouldValidateJoinedRoom() || _consumeRoomJoined)
            {
                return;
            }

            var room = CurrentRoom;
            if (room?.MembersReceived != true || room.Participants == null || room.Participants.Count == 0)
            {
                return;
            }

            var hasRemoteParticipants = false;
            foreach (var participant in room.Participants)
            {
                if (participant == null || participant.IsLocal)
                {
                    continue;
                }

                if (participant.DisplayName.Equals(_lastLocalUserName, StringComparison.OrdinalIgnoreCase))
                {
                    continue;
                }

                hasRemoteParticipants = true;
                break;
            }
            if (!hasRemoteParticipants)
            {
                return;
            }

            var activePrimeTask = _consumeSessionPrimeTask;
            if (activePrimeTask != null && !activePrimeTask.IsCompleted)
            {
                return;
            }

            _consumeSessionPrimeTask = PrimeRemoteMediaBackendSocketSessionAsync();
        }

        private async Task PrimeRemoteMediaBackendSocketSessionAsync()
        {
            try
            {
                await EnsureRemoteMediaBackendSocketSessionAsync(CancellationToken.None).ConfigureAwait(false);
            }
            catch (Exception error)
            {
                PublishError("PrimeConsumeSocketSession", error.Message, error.ToString());
            }
        }

        private void HandleSocketPersonJoined(string payloadJson)
        {
            if (CurrentRoom == null || string.IsNullOrWhiteSpace(payloadJson) || payloadJson == "null")
            {
                return;
            }

            using var document = JsonDocument.Parse(payloadJson);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object)
            {
                return;
            }

            var name = TryGetString(root, "name");
            if (string.IsNullOrWhiteSpace(name) || FindParticipantByName(name) != null)
            {
                return;
            }

            var placeholderParticipant = new MediaSfuParticipant
            {
                ParticipantId = $"person-joined-{SanitizeParticipantKey(name)}",
                DisplayName = name,
                Role = ResolveParticipantRole(name.Equals(_lastLocalUserName, StringComparison.OrdinalIgnoreCase) ? _lastLocalIsLevel : "0"),
                IsLocal = name.Equals(_lastLocalUserName, StringComparison.OrdinalIgnoreCase),
                AudioOn = false,
                VideoOn = false,
                ScreenOn = false
            };

            var nextParticipants = new List<MediaSfuParticipant>(CurrentRoom.Participants)
            {
                placeholderParticipant
            };
            UpdateCurrentRoomParticipants(nextParticipants);
        }

        private void HandleSocketAllWaitingRoomMembers(string payloadJson)
        {
            if (CurrentRoom == null || string.IsNullOrWhiteSpace(payloadJson) || payloadJson == "null")
            {
                return;
            }

            using var document = JsonDocument.Parse(payloadJson);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object)
            {
                return;
            }

            List<MediaSfuWaitingRoomParticipant> waitingParticipants = null;
            if (root.TryGetProperty("waitingParticipants", out var primaryWaitingElement) &&
                primaryWaitingElement.ValueKind == JsonValueKind.Array)
            {
                waitingParticipants = ParseWaitingRoomParticipants(primaryWaitingElement);
            }
            else if (root.TryGetProperty("waitingParticipantss", out var secondaryWaitingElement) &&
                     secondaryWaitingElement.ValueKind == JsonValueKind.Array)
            {
                waitingParticipants = ParseWaitingRoomParticipants(secondaryWaitingElement);
            }
            else
            {
                return;
            }

            UpdateCurrentRoomState(
                nextRoom =>
                {
                    nextRoom.WaitingRoomParticipants.Clear();
                    foreach (var participant in waitingParticipants)
                    {
                        nextRoom.WaitingRoomParticipants.Add(CloneWaitingRoomParticipant(participant));
                    }

                    nextRoom.PendingModerationCount = ComputePendingModerationCount(nextRoom);
                });
        }

        private void HandleSocketUserWaiting(string payloadJson)
        {
            if (CurrentRoom == null || string.IsNullOrWhiteSpace(payloadJson) || payloadJson == "null")
            {
                return;
            }

            using var document = JsonDocument.Parse(payloadJson);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object)
            {
                return;
            }

            var waitingName = TryGetString(root, "name");
            if (string.IsNullOrWhiteSpace(waitingName))
            {
                return;
            }

            UpdateCurrentRoomState(
                nextRoom =>
                {
                    var baselineCount = Math.Max(nextRoom.PendingModerationCount, ComputePendingModerationCount(nextRoom));
                    nextRoom.PendingModerationCount = baselineCount + 1;
                    nextRoom.LastWaitingParticipantName = waitingName;
                });
        }

        private void HandleSocketParticipantRequested(string payloadJson)
        {
            if (CurrentRoom == null || string.IsNullOrWhiteSpace(payloadJson) || payloadJson == "null")
            {
                return;
            }

            using var document = JsonDocument.Parse(payloadJson);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object ||
                !root.TryGetProperty("userRequest", out var userRequestElement))
            {
                return;
            }

            var request = TryParseRoomRequest(userRequestElement);
            if (request == null)
            {
                return;
            }

            UpdateCurrentRoomState(
                nextRoom =>
                {
                    var existingIndex = FindPendingRequestIndex(nextRoom.PendingRequests, request.RequestId);
                    if (existingIndex >= 0)
                    {
                        nextRoom.PendingRequests[existingIndex] = CloneRoomRequest(request);
                    }
                    else
                    {
                        nextRoom.PendingRequests.Add(CloneRoomRequest(request));
                    }

                    nextRoom.PendingModerationCount = ComputePendingModerationCount(nextRoom);
                });
        }

        private void HandleSocketScreenProducerId(string payloadJson)
        {
            if (CurrentRoom == null || string.IsNullOrWhiteSpace(payloadJson) || payloadJson == "null")
            {
                return;
            }

            using var document = JsonDocument.Parse(payloadJson);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object)
            {
                return;
            }

            var producerId = TryGetString(root, "producerId");
            if (string.IsNullOrWhiteSpace(producerId))
            {
                return;
            }

            TryMarkScreenProducerHint(producerId);
            ApplyScreenProducerHint(producerId);
        }

        private void HandleSocketHostRequestResponse(string payloadJson)
        {
            if (CurrentRoom == null || string.IsNullOrWhiteSpace(payloadJson) || payloadJson == "null")
            {
                return;
            }

            using var document = JsonDocument.Parse(payloadJson);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object ||
                !root.TryGetProperty("requestResponse", out var requestResponseElement) ||
                requestResponseElement.ValueKind != JsonValueKind.Object)
            {
                return;
            }

            var requestResponse = TryParseRequestResponse(requestResponseElement);
            if (requestResponse == null || string.IsNullOrWhiteSpace(requestResponse.RequestId))
            {
                return;
            }

            UpdateCurrentRoomState(
                nextRoom =>
                {
                    RemovePendingRequest(nextRoom.PendingRequests, requestResponse.RequestId);
                    ApplyRequestResponse(nextRoom, requestResponse);
                    nextRoom.PendingModerationCount = ComputePendingModerationCount(nextRoom);
                });
        }

        private void HandleSocketPollUpdated(string payloadJson)
        {
            if (CurrentRoom == null || string.IsNullOrWhiteSpace(payloadJson) || payloadJson == "null")
            {
                return;
            }

            using var document = JsonDocument.Parse(payloadJson);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object ||
                !root.TryGetProperty("poll", out var pollElement))
            {
                return;
            }

            var updatedPoll = TryParsePoll(pollElement);
            if (updatedPoll == null)
            {
                return;
            }

            var status = TryGetString(root, "status") ?? updatedPoll.Status ?? string.Empty;
            if (!string.IsNullOrWhiteSpace(status))
            {
                updatedPoll.Status = status;
            }

            List<MediaSfuPoll> incomingPolls = null;
            if (root.TryGetProperty("polls", out var pollsElement) &&
                pollsElement.ValueKind == JsonValueKind.Array)
            {
                incomingPolls = ParsePolls(pollsElement);
            }

            UpdateCurrentRoomState(
                nextRoom =>
                {
                    if (incomingPolls != null && incomingPolls.Count > 0)
                    {
                        nextRoom.Polls.Clear();
                        foreach (var poll in incomingPolls)
                        {
                            nextRoom.Polls.Add(ClonePoll(poll));
                        }
                    }
                    else
                    {
                        var existingIndex = FindPollIndex(nextRoom.Polls, updatedPoll.PollId);
                        if (existingIndex >= 0)
                        {
                            nextRoom.Polls[existingIndex] = ClonePoll(updatedPoll);
                        }
                        else
                        {
                            nextRoom.Polls.Add(ClonePoll(updatedPoll));
                        }
                    }

                    nextRoom.ActivePoll = ClonePoll(updatedPoll);
                    nextRoom.LastPollStatus = status;

                    if (string.Equals(status, "ended", StringComparison.OrdinalIgnoreCase))
                    {
                        nextRoom.PollModalVisible = false;
                    }
                    else if (ShouldOpenPollModal(updatedPoll, status))
                    {
                        nextRoom.PollModalVisible = true;
                    }
                });
        }

        private void HandleSocketBreakoutRoomUpdated(string payloadJson)
        {
            if (CurrentRoom == null || string.IsNullOrWhiteSpace(payloadJson) || payloadJson == "null")
            {
                return;
            }

            using var document = JsonDocument.Parse(payloadJson);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object)
            {
                return;
            }

            var isHostRoomUpdate = TryGetBool(root, "forHost") == true;
            var newHostRoom = TryGetInt(root, "newRoom");
            var breakoutStatus = TryGetString(root, "status") ?? string.Empty;

            if (isHostRoomUpdate)
            {
                if (!newHostRoom.HasValue)
                {
                    return;
                }

                UpdateCurrentRoomState(
                    nextRoom =>
                    {
                        var breakout = nextRoom.Breakout ?? (nextRoom.Breakout = new MediaSfuBreakoutState());
                        breakout.HostNewRoom = newHostRoom.Value;
                    });
                return;
            }

            var hasBreakoutRooms = root.TryGetProperty("breakoutRooms", out var breakoutRoomsElement) &&
                                   breakoutRoomsElement.ValueKind == JsonValueKind.Array;
            List<List<MediaSfuBreakoutParticipant>> breakoutRooms = null;
            if (hasBreakoutRooms)
            {
                breakoutRooms = ParseBreakoutRooms(breakoutRoomsElement);
            }

            List<MediaSfuParticipant> nextParticipants = null;
            if (string.Equals(_lastLocalIsLevel, "2", StringComparison.OrdinalIgnoreCase) &&
                root.TryGetProperty("members", out var membersElement) &&
                membersElement.ValueKind == JsonValueKind.Array)
            {
                nextParticipants = new List<MediaSfuParticipant>();
                foreach (var memberElement in membersElement.EnumerateArray())
                {
                    var participant = TryParseParticipant(memberElement);
                    if (participant != null)
                    {
                        nextParticipants.Add(participant);
                    }
                }
            }

            if (!hasBreakoutRooms &&
                nextParticipants == null &&
                string.IsNullOrWhiteSpace(breakoutStatus))
            {
                return;
            }

            UpdateCurrentRoomState(
                nextRoom =>
                {
                    var breakout = nextRoom.Breakout ?? (nextRoom.Breakout = new MediaSfuBreakoutState());

                    if (nextParticipants != null)
                    {
                        nextRoom.Participants.Clear();
                        foreach (var participant in nextParticipants)
                        {
                            nextRoom.Participants.Add(CloneParticipant(participant));
                        }

                        nextRoom.MembersReceived = true;
                    }

                    if (hasBreakoutRooms)
                    {
                        breakout.Rooms.Clear();
                        foreach (var breakoutRoom in breakoutRooms)
                        {
                            var clonedRoom = new List<MediaSfuBreakoutParticipant>();
                            foreach (var participant in breakoutRoom)
                            {
                                clonedRoom.Add(CloneBreakoutParticipant(participant));
                            }

                            breakout.Rooms.Add(clonedRoom);
                        }
                    }

                    if (!string.IsNullOrWhiteSpace(breakoutStatus))
                    {
                        breakout.Status = breakoutStatus;
                    }

                    if (string.Equals(breakoutStatus, "started", StringComparison.OrdinalIgnoreCase))
                    {
                        breakout.Started = true;
                        breakout.Ended = false;
                    }
                    else if (string.Equals(breakoutStatus, "ended", StringComparison.OrdinalIgnoreCase))
                    {
                        breakout.Ended = true;
                    }
                });
        }

        private void HandleSocketRoomRecordParams(string payloadJson)
        {
            if (CurrentRoom == null || string.IsNullOrWhiteSpace(payloadJson) || payloadJson == "null")
            {
                return;
            }

            using var document = JsonDocument.Parse(payloadJson);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object)
            {
                return;
            }

            UpdateCurrentRoomState(nextRoom => ApplyRecordingParameters(nextRoom, TryParseRecordingParameters(root)));
        }

        private void HandleSocketStartRecords()
        {
            TriggerSocketDrivenRecordingStart("StartRecords");
        }

        private void HandleSocketReInitiateRecording()
        {
            if (CurrentRoom?.AdminRestrictSetting == true)
            {
                return;
            }

            TriggerSocketDrivenRecordingStart("ReInitiateRecording");
        }

        private void HandleSocketRecordingNotice(string payloadJson)
        {
            if (CurrentRoom == null || string.IsNullOrWhiteSpace(payloadJson) || payloadJson == "null")
            {
                return;
            }

            using var document = JsonDocument.Parse(payloadJson);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object)
            {
                return;
            }

            var state = TryGetString(root, "state");
            if (string.IsNullOrWhiteSpace(state))
            {
                return;
            }

            var pauseCount = TryGetInt(root, "pauseCount") ?? 0;
            var timeDoneMs = TryGetInt(root, "timeDone") ?? 0;
            MediaSfuUserRecordingParams userRecordingParams = null;
            var userRecordingParamsElement = TryGetNestedElement(root, "userRecordingParam", "userRecordingParams");
            if (userRecordingParamsElement.HasValue && userRecordingParamsElement.Value.ValueKind == JsonValueKind.Object)
            {
                userRecordingParams = TryParseUserRecordingParams(userRecordingParamsElement.Value);
            }

            UpdateCurrentRoomState(
                nextRoom =>
                {
                    var recording = nextRoom.Recording ?? (nextRoom.Recording = new MediaSfuRecordingState());
                    recording.LastNoticeState = state;

                    if (pauseCount > 0 || string.Equals(state, "pause", StringComparison.OrdinalIgnoreCase))
                    {
                        recording.PauseCount = pauseCount;
                    }

                    if (timeDoneMs > 0)
                    {
                        var elapsedSeconds = timeDoneMs / 1000;
                        recording.RecordElapsedTimeSeconds = elapsedSeconds;
                        recording.RecordStartTimeEpochMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() - timeDoneMs;
                        recording.ProgressTime = FormatElapsedTime(elapsedSeconds);
                    }

                    if (userRecordingParams != null)
                    {
                        recording.UserRecordingParams = CloneUserRecordingParams(userRecordingParams);
                    }

                    switch (state.Trim().ToLowerInvariant())
                    {
                        case "pause":
                            recording.State = "yellow";
                            recording.RecordStarted = true;
                            recording.RecordPaused = true;
                            recording.RecordStopped = false;
                            recording.CanLaunchRecord = false;
                            recording.ShowRecordButtons = true;
                            recording.IsTimerRunning = false;
                            recording.CanPauseResume = true;
                            break;
                        case "stop":
                            recording.State = "green";
                            recording.RecordStarted = true;
                            recording.RecordPaused = false;
                            recording.RecordStopped = true;
                            recording.CanLaunchRecord = false;
                            recording.ShowRecordButtons = false;
                            recording.IsTimerRunning = false;
                            recording.TimeLeftSeconds = null;
                            break;
                        default:
                            recording.State = "red";
                            recording.RecordStarted = true;
                            recording.RecordPaused = false;
                            recording.RecordStopped = false;
                            recording.CanLaunchRecord = false;
                            recording.ShowRecordButtons = true;
                            recording.IsTimerRunning = true;
                            break;
                    }
                });
        }

        private void HandleSocketTimeLeftRecording(string payloadJson)
        {
            if (CurrentRoom == null || string.IsNullOrWhiteSpace(payloadJson) || payloadJson == "null")
            {
                return;
            }

            using var document = JsonDocument.Parse(payloadJson);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object)
            {
                return;
            }

            var timeLeftSeconds = TryGetInt(root, "timeLeft");
            if (!timeLeftSeconds.HasValue)
            {
                return;
            }

            UpdateCurrentRoomState(
                nextRoom =>
                {
                    var recording = nextRoom.Recording ?? (nextRoom.Recording = new MediaSfuRecordingState());
                    recording.TimeLeftSeconds = timeLeftSeconds.Value;
                });
        }

        private void HandleSocketStoppedRecording(string payloadJson)
        {
            if (CurrentRoom == null || string.IsNullOrWhiteSpace(payloadJson) || payloadJson == "null")
            {
                return;
            }

            using var document = JsonDocument.Parse(payloadJson);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object)
            {
                return;
            }

            var state = TryGetString(root, "state") ?? string.Empty;
            var reason = TryGetString(root, "reason") ?? string.Empty;
            if (string.IsNullOrWhiteSpace(state) && string.IsNullOrWhiteSpace(reason))
            {
                return;
            }

            UpdateCurrentRoomState(
                nextRoom =>
                {
                    var recording = nextRoom.Recording ?? (nextRoom.Recording = new MediaSfuRecordingState());
                    recording.LastNoticeState = state;
                    recording.LastStopReason = reason;
                    recording.TimeLeftSeconds = null;

                    if (string.Equals(state, "stop", StringComparison.OrdinalIgnoreCase))
                    {
                        recording.State = "green";
                        recording.RecordStarted = true;
                        recording.RecordPaused = false;
                        recording.RecordStopped = true;
                        recording.ShowRecordButtons = false;
                        recording.IsTimerRunning = false;
                        recording.CanLaunchRecord = false;
                    }
                });
        }

        private void TriggerSocketDrivenRecordingStart(string operation)
        {
            var session = ResolveRoomControlSocketSession();
            var roomName = CurrentRoom?.RoomName;
            if (session?.Socket == null ||
                session.Socket.State != WebSocketState.Open ||
                string.IsNullOrWhiteSpace(roomName))
            {
                return;
            }

            var memberName = string.IsNullOrWhiteSpace(_lastLocalUserName) ? "tester" : _lastLocalUserName;
            _ = SendSocketDrivenRecordingStartAsync(session, roomName, memberName, operation);
        }

        private async Task SendSocketDrivenRecordingStartAsync(
            SocketTransportSession session,
            string roomName,
            string memberName,
            string operation)
        {
            try
            {
                await SendSocketEventAsync(
                        session,
                        "startRecordIng",
                        new Dictionary<string, object>
                        {
                            ["roomName"] = roomName,
                            ["member"] = memberName
                        },
                        CancellationToken.None)
                    .ConfigureAwait(false);
            }
            catch (Exception error)
            {
                PublishError(operation, error.Message, error.ToString());
            }
        }

        private void HandleSocketReceiveMessage(string payloadJson)
        {
            if (string.IsNullOrWhiteSpace(payloadJson) || payloadJson == "null")
            {
                return;
            }

            using var document = JsonDocument.Parse(payloadJson);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object ||
                !root.TryGetProperty("message", out var messageElement) ||
                messageElement.ValueKind != JsonValueKind.Object)
            {
                return;
            }

            var chatMessage = TryParseChatMessage(messageElement);
            if (chatMessage == null)
            {
                return;
            }

            MessageReceived?.Invoke(chatMessage);
        }

        private void HandleSocketRemoteProducerAvailable(string payloadJson, bool isPipeProducer)
        {
            if (string.IsNullOrWhiteSpace(payloadJson) || payloadJson == "null")
            {
                return;
            }

            using var document = JsonDocument.Parse(payloadJson);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object)
            {
                return;
            }

            var producerId = TryGetString(root, "producerId", "remoteProducerId") ?? string.Empty;
            if (string.IsNullOrWhiteSpace(producerId))
            {
                return;
            }

            var isLevel = TryGetString(root, "islevel", "isLevel") ?? string.Empty;

            if (!_consumeRoomJoined)
            {
                MaybePrimeRemoteMediaBackendSocketSession();
                return;
            }

            if (isPipeProducer && string.Equals(isLevel, "2", StringComparison.OrdinalIgnoreCase))
            {
                TryMarkScreenProducerHint(producerId);
                ApplyScreenProducerHint(producerId);
            }

            if (!TryMarkKnownRemoteProducer(producerId))
            {
                return;
            }

            PublishRemoteProducerAvailable(
                new MediaSfuRemoteProducerEvent
                {
                    ProducerId = producerId,
                    IsLevel = isLevel,
                    IsPipeProducer = isPipeProducer,
                    TranslationMetaJson = TryGetNestedElement(root, "translationMeta")?.GetRawText() ?? string.Empty,
                    RawPayload = root.GetRawText()
                });
        }

        private void HandleSocketRemoteProducerClosed(string payloadJson)
        {
            if (string.IsNullOrWhiteSpace(payloadJson) || payloadJson == "null")
            {
                return;
            }

            using var document = JsonDocument.Parse(payloadJson);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object)
            {
                return;
            }

            var remoteProducerId = TryGetString(root, "remoteProducerId", "producerId") ?? string.Empty;
            if (string.IsNullOrWhiteSpace(remoteProducerId))
            {
                return;
            }

            ForgetScreenProducerHint(remoteProducerId);
            ForgetKnownRemoteProducer(remoteProducerId);

            PublishRemoteProducerClosed(
                new MediaSfuRemoteProducerClosedEvent
                {
                    RemoteProducerId = remoteProducerId,
                    RawPayload = root.GetRawText()
                });
        }

        private void PublishRemoteProducerAvailable(MediaSfuRemoteProducerEvent remoteProducer)
        {
            if (remoteProducer == null || string.IsNullOrWhiteSpace(remoteProducer.ProducerId))
            {
                return;
            }

            RemoteProducerAvailable?.Invoke(remoteProducer);

            var remoteMediaBridge = RemoteMediaBridge;
            if (remoteMediaBridge != null)
            {
                _ = DispatchRemoteProducerAvailableToBridgeAsync(remoteMediaBridge, remoteProducer);
            }
        }

        private void PublishRemoteProducerClosed(MediaSfuRemoteProducerClosedEvent remoteProducerClosed)
        {
            if (remoteProducerClosed == null || string.IsNullOrWhiteSpace(remoteProducerClosed.RemoteProducerId))
            {
                return;
            }

            RemoteProducerClosed?.Invoke(remoteProducerClosed);

            var remoteMediaBridge = RemoteMediaBridge;
            if (remoteMediaBridge != null)
            {
                _ = DispatchRemoteProducerClosedToBridgeAsync(remoteMediaBridge, remoteProducerClosed);
            }
        }

        private async Task DispatchRemoteProducerAvailableToBridgeAsync(
            IMediaSfuRemoteMediaBridge remoteMediaBridge,
            MediaSfuRemoteProducerEvent remoteProducer)
        {
            try
            {
                Console.WriteLine(
                    "MediaSfu receive dispatch start producerId=" + (remoteProducer?.ProducerId ?? string.Empty) +
                    " isPipeProducer=" + (remoteProducer?.IsPipeProducer ?? false));

                await Task.Yield();

                var result = await remoteMediaBridge.OnRemoteProducerAvailableAsync(
                        BuildRemoteMediaRequest(remoteProducer))
                    .ConfigureAwait(false);

                Console.WriteLine(
                    "MediaSfu receive dispatch result producerId=" + (remoteProducer?.ProducerId ?? string.Empty) +
                    " success=" + (result?.Success ?? false) +
                    " status=" + (result?.Status.ToString() ?? string.Empty) +
                    " error=" + (result?.Error ?? string.Empty));

                if (result != null && result.Success)
                {
                    PublishTrackAddedForRemoteProducer(remoteProducer);
                }

                PublishRemoteMediaBridgeResult("RemoteProducerAvailable", result);
            }
            catch (Exception error)
            {
                PublishError("RemoteProducerAvailable", error.Message, error.ToString());
            }
        }

        private async Task DispatchRemoteProducerClosedToBridgeAsync(
            IMediaSfuRemoteMediaBridge remoteMediaBridge,
            MediaSfuRemoteProducerClosedEvent remoteProducerClosed)
        {
            try
            {
                var result = await remoteMediaBridge.OnRemoteProducerClosedAsync(remoteProducerClosed)
                    .ConfigureAwait(false);

                PublishRemoteMediaBridgeResult("RemoteProducerClosed", result);
            }
            catch (Exception error)
            {
                PublishError("RemoteProducerClosed", error.Message, error.ToString());
            }
        }

        private void PublishRemoteMediaBridgeResult(string operation, MediaSfuOperationResult<bool> result)
        {
            if (result == null || result.Success || result.Status == MediaSfuOperationStatus.Deferred)
            {
                return;
            }

            PublishError(operation, result.Error ?? $"{operation} failed.", result.Detail ?? string.Empty);
        }

        private void PublishTrackAddedForRemoteProducer(MediaSfuRemoteProducerEvent remoteProducer)
        {
            if (remoteProducer == null)
            {
                return;
            }

            var payloadJson = remoteProducer.RawPayload;
            if (string.IsNullOrWhiteSpace(payloadJson) || payloadJson == "null")
            {
                return;
            }

            try
            {
                using var document = JsonDocument.Parse(payloadJson);
                var root = document.RootElement;
                if (root.ValueKind != JsonValueKind.Object)
                {
                    return;
                }

                var producerId = TryGetString(root, "producerId", "remoteProducerId") ?? remoteProducer.ProducerId;
                var rawKind = TryGetString(root, "kind");
                if (string.IsNullOrWhiteSpace(rawKind))
                {
                    return;
                }

                var participantName = TryGetString(root, "name", "participantName", "userName", "displayName") ?? string.Empty;
                var effectiveKind = ResolveRemoteProducerEffectiveKind(rawKind, producerId, participantName);
                if (!string.IsNullOrWhiteSpace(participantName))
                {
                    UpdateParticipantTrackStateFromProducer(participantName, effectiveKind, producerId, enabled: true);
                }

                var participant = FindParticipantByName(participantName);

                TrackAdded?.Invoke(
                    new MediaSfuTrackEvent
                    {
                        RoomName = CurrentRoom?.RoomName ?? string.Empty,
                        Participant = participant,
                        Track = new MediaSfuTrack
                        {
                            TrackId = producerId,
                            ParticipantId = participant?.ParticipantId ?? participantName,
                            Kind = ResolveTrackKind(effectiveKind),
                            IsRemote = participant?.IsLocal != true &&
                                !participantName.Equals(_lastLocalUserName, StringComparison.OrdinalIgnoreCase),
                            IsMuted = false
                        }
                    });
            }
            catch (JsonException)
            {
            }
        }

        private void HandleSocketUpdatedCoHost(string payloadJson)
        {
            if (CurrentRoom == null || string.IsNullOrWhiteSpace(payloadJson) || payloadJson == "null")
            {
                return;
            }

            using var document = JsonDocument.Parse(payloadJson);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object)
            {
                return;
            }

            var coHostName = TryGetString(root, "coHost");
            if (string.IsNullOrWhiteSpace(coHostName))
            {
                return;
            }

            var responsibilitiesElement = TryGetCoHostResponsibilitiesElement(root);
            var responsibilities = responsibilitiesElement.HasValue
                ? ParseCoHostResponsibilities(responsibilitiesElement.Value)
                : null;

            UpdateCurrentRoomState(
                nextRoom =>
                {
                    nextRoom.CoHost = coHostName;

                    if (responsibilities != null)
                    {
                        nextRoom.CoHostResponsibilities.Clear();
                        foreach (var responsibility in responsibilities)
                        {
                            nextRoom.CoHostResponsibilities.Add(CloneCoHostResponsibility(responsibility));
                        }
                    }

                    foreach (var participant in nextRoom.Participants)
                    {
                        if (participant.Role == MediaSfuParticipantRole.Host)
                        {
                            continue;
                        }

                        if (participant.DisplayName.Equals(coHostName, StringComparison.OrdinalIgnoreCase))
                        {
                            participant.Role = MediaSfuParticipantRole.CoHost;
                        }
                        else if (participant.IsLocal)
                        {
                            participant.Role = ResolveParticipantRole(_lastLocalIsLevel);
                        }
                        else if (participant.Role == MediaSfuParticipantRole.CoHost)
                        {
                            participant.Role = MediaSfuParticipantRole.Participant;
                        }
                    }
                });
        }

        private void HandleSocketBanParticipant(string payloadJson)
        {
            if (CurrentRoom == null || string.IsNullOrWhiteSpace(payloadJson) || payloadJson == "null")
            {
                return;
            }

            using var document = JsonDocument.Parse(payloadJson);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object)
            {
                return;
            }

            var name = TryGetString(root, "name");
            if (string.IsNullOrWhiteSpace(name) || FindParticipantByName(name) == null)
            {
                return;
            }

            UpdateCurrentRoomState(
                nextRoom =>
                {
                    nextRoom.Participants.RemoveAll(
                        participant => participant.DisplayName.Equals(name, StringComparison.OrdinalIgnoreCase));
                });
        }

        private void HandleSocketUpdateMediaSettings(string payloadJson)
        {
            if (CurrentRoom == null || string.IsNullOrWhiteSpace(payloadJson) || payloadJson == "null")
            {
                return;
            }

            using var document = JsonDocument.Parse(payloadJson);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object ||
                !root.TryGetProperty("settings", out var settingsElement) ||
                settingsElement.ValueKind != JsonValueKind.Array)
            {
                return;
            }

            var settings = ParseStringList(settingsElement);
            UpdateCurrentRoomState(nextRoom => ApplyMediaSettings(nextRoom, settings));
        }

        private void HandleSocketControlMediaHost(string payloadJson)
        {
            if (CurrentRoom == null || string.IsNullOrWhiteSpace(payloadJson) || payloadJson == "null")
            {
                return;
            }

            using var document = JsonDocument.Parse(payloadJson);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object)
            {
                return;
            }

            var controlType = NormalizeMediaControlType(TryGetString(root, "type"));
            if (string.IsNullOrWhiteSpace(controlType))
            {
                return;
            }

            UpdateCurrentRoomState(
                nextRoom =>
                {
                    nextRoom.AdminRestrictSetting = true;
                    ApplyHostRestriction(nextRoom, controlType);
                    ApplyHostRestrictionToLocalParticipant(nextRoom.Participants, controlType);
                });
        }

        private void HandleSocketMeetingTimeRemaining(string payloadJson)
        {
            if (CurrentRoom == null || string.IsNullOrWhiteSpace(payloadJson) || payloadJson == "null")
            {
                return;
            }

            using var document = JsonDocument.Parse(payloadJson);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object)
            {
                return;
            }

            var timeRemaining = TryGetInt(root, "timeRemaining");
            if (!timeRemaining.HasValue)
            {
                return;
            }

            UpdateCurrentRoomState(nextRoom => nextRoom.MeetingTimeRemainingMs = timeRemaining.Value);
        }

        private void HandleSocketMeetingStillThere()
        {
            if (CurrentRoom == null)
            {
                return;
            }

            if (_suppressConfirmHere)
            {
                return;
            }

            UpdateCurrentRoomState(nextRoom => nextRoom.ConfirmHereRequested = true);
        }

        private void HandleSocketUpdateConsumingDomains(string payloadJson)
        {
            if (CurrentRoom == null ||
                string.IsNullOrWhiteSpace(payloadJson) ||
                payloadJson == "null" ||
                CurrentRoom.Participants == null ||
                CurrentRoom.Participants.Count == 0)
            {
                return;
            }

            using var document = JsonDocument.Parse(payloadJson);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object ||
                !root.TryGetProperty("domains", out var domainsElement) ||
                domainsElement.ValueKind != JsonValueKind.Array)
            {
                return;
            }

            var altDomainsElement = TryGetNestedElement(root, "altDomains");
            var hasAltDomains = altDomainsElement.HasValue &&
                                altDomainsElement.Value.ValueKind == JsonValueKind.Object &&
                                altDomainsElement.Value.EnumerateObject().MoveNext();
            var altDomainsJson = altDomainsElement.HasValue
                ? altDomainsElement.Value.GetRawText()
                : string.Empty;
            var domains = ParseStringList(domainsElement);

            Console.WriteLine(
                "MediaSfu updateConsumingDomains domains=" + string.Join(",", domains) +
                " altDomains=" + altDomainsJson);

            UpdateCurrentRoomState(
                nextRoom =>
                {
                    var consumingDomains = nextRoom.ConsumingDomains ??
                                           (nextRoom.ConsumingDomains = new MediaSfuConsumingDomainsState());
                    consumingDomains.Domains.Clear();
                    consumingDomains.Domains.AddRange(domains);
                    consumingDomains.HasAltDomains = hasAltDomains;
                    consumingDomains.AltDomainsJson = altDomainsJson;
                });

                    MaybePrimeRemoteMediaBackendSocketSession();
        }

        private void HandleSocketWhiteboardAction(string payloadJson)
        {
            if (CurrentRoom == null || string.IsNullOrWhiteSpace(payloadJson) || payloadJson == "null")
            {
                return;
            }

            using var document = JsonDocument.Parse(payloadJson);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object)
            {
                return;
            }

            var action = TryGetString(root, "action");
            if (string.IsNullOrWhiteSpace(action))
            {
                return;
            }

            var actionPayload = TryGetNestedElement(root, "payload");

            UpdateCurrentRoomState(
                nextRoom =>
                {
                    var whiteboard = nextRoom.Whiteboard ?? (nextRoom.Whiteboard = new MediaSfuWhiteboardState());
                    whiteboard.LastAction = action;

                    switch (action.Trim().ToLowerInvariant())
                    {
                        case "draw":
                            AppendWhiteboardShape(whiteboard.Shapes, CreateWhiteboardDrawShape(actionPayload));
                            break;
                        case "shape":
                            AppendWhiteboardShape(whiteboard.Shapes, CreateWhiteboardShapeFromElement(actionPayload));
                            break;
                        case "erase":
                            ApplyWhiteboardErase(whiteboard.Shapes, actionPayload);
                            break;
                        case "clear":
                            whiteboard.Shapes.Clear();
                            break;
                        case "uploadimage":
                            AppendWhiteboardShape(whiteboard.Shapes, CreateWhiteboardImageShape(actionPayload));
                            break;
                        case "togglebackground":
                            whiteboard.UseImageBackground = !whiteboard.UseImageBackground;
                            break;
                        case "undo":
                            ApplyWhiteboardUndo(whiteboard);
                            break;
                        case "redo":
                            ApplyWhiteboardRedo(whiteboard);
                            break;
                        case "text":
                            AppendWhiteboardShape(whiteboard.Shapes, CreateWhiteboardTextShape(actionPayload));
                            break;
                        case "deleteshape":
                            ApplyWhiteboardDeleteShape(whiteboard.Shapes, actionPayload);
                            break;
                        case "shapes":
                            ReplaceWhiteboardShapes(whiteboard.Shapes, actionPayload);
                            break;
                    }
                });
        }

        private void HandleSocketWhiteboardUpdated(string payloadJson)
        {
            if (CurrentRoom == null || string.IsNullOrWhiteSpace(payloadJson) || payloadJson == "null")
            {
                return;
            }

            using var document = JsonDocument.Parse(payloadJson);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object)
            {
                return;
            }

            var status = TryGetString(root, "status");
            var whiteboardUsersElement = TryGetNestedElement(root, "whiteboardUsers");
            var whiteboardDataElement = TryGetNestedElement(root, "whiteboardData");

            UpdateCurrentRoomState(
                nextRoom =>
                {
                    var whiteboard = nextRoom.Whiteboard ?? (nextRoom.Whiteboard = new MediaSfuWhiteboardState());
                    whiteboard.LastAction = string.IsNullOrWhiteSpace(status)
                        ? "updated"
                        : status.Trim().ToLowerInvariant();

                    ReplaceWhiteboardUsers(whiteboard.Users, whiteboardUsersElement);

                    if (whiteboardDataElement.HasValue && whiteboardDataElement.Value.ValueKind == JsonValueKind.Object)
                    {
                        ReplaceWhiteboardShapes(whiteboard.Shapes, whiteboardDataElement);
                    }

                    if (!string.IsNullOrWhiteSpace(status))
                    {
                        switch (status.Trim().ToLowerInvariant())
                        {
                            case "started":
                                whiteboard.Started = true;
                                whiteboard.Ended = false;
                                whiteboard.CanStart = false;
                                break;
                            case "ended":
                            case "stopped":
                                whiteboard.Started = false;
                                whiteboard.Ended = true;
                                whiteboard.CanStart = true;
                                break;
                        }
                    }
                });
        }

        private void HandleSocketMeetingEnded(string payloadJson)
        {
            var message = "Server reported that the meeting has ended.";

            if (!string.IsNullOrWhiteSpace(payloadJson) && payloadJson != "null")
            {
                using var document = JsonDocument.Parse(payloadJson);
                var root = document.RootElement;
                if (root.ValueKind == JsonValueKind.Object)
                {
                    var payloadMessage = TryGetString(root, "message", "reason");
                    if (!string.IsNullOrWhiteSpace(payloadMessage))
                    {
                        message = payloadMessage;
                    }
                }
            }

            ClearCurrentRoom();
            SetConnectionState(MediaSfuConnectionState.Idle);
            PublishError("meetingEnded", message, payloadJson);
        }

        private void HandleSocketDisconnectUserSelf(string payloadJson)
        {
            var message = "Server requested this participant to disconnect.";

            if (!string.IsNullOrWhiteSpace(payloadJson) && payloadJson != "null")
            {
                using var document = JsonDocument.Parse(payloadJson);
                var root = document.RootElement;
                if (root.ValueKind == JsonValueKind.Object)
                {
                    if (TryGetBool(root, "ban") == true || TryGetBool(root, "banned") == true)
                    {
                        message = "Server requested this participant to disconnect and marked the user as banned.";
                    }
                    else if (TryGetBool(root, "suspended") == true)
                    {
                        message = "Server requested this participant to disconnect and marked the user as suspended.";
                    }
                    else if (!string.IsNullOrWhiteSpace(TryGetString(root, "reason")))
                    {
                        message = TryGetString(root, "reason");
                    }
                    else if (!string.IsNullOrWhiteSpace(TryGetString(root, "message")))
                    {
                        message = TryGetString(root, "message");
                    }
                }
            }

            ClearCurrentRoom();
            SetConnectionState(MediaSfuConnectionState.Idle);
            PublishError("disconnectUserSelf", message, payloadJson);
        }

        private void HandleSocketProducerMediaPaused(string payloadJson)
        {
            HandleSocketProducerMediaState(payloadJson, enabled: false);
        }

        private void HandleSocketProducerMediaResumed(string payloadJson)
        {
            HandleSocketProducerMediaState(payloadJson, enabled: true);
        }

        private void HandleSocketProducerMediaState(string payloadJson, bool enabled)
        {
            if (string.IsNullOrWhiteSpace(payloadJson) || payloadJson == "null")
            {
                return;
            }

            using var document = JsonDocument.Parse(payloadJson);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object)
            {
                return;
            }

            var kind = TryGetString(root, "kind");
            var producerId = TryGetString(root, "producerId");
            var participantName = TryGetString(root, "name") ?? string.Empty;
            if (string.IsNullOrWhiteSpace(kind) || string.IsNullOrWhiteSpace(participantName))
            {
                return;
            }

            var effectiveKind = ResolveRemoteProducerEffectiveKind(kind, producerId, participantName);
            UpdateParticipantTrackStateFromProducer(participantName, effectiveKind, producerId, enabled);
        }

        private void HandleSocketProducerMediaClosed(string payloadJson)
        {
            if (string.IsNullOrWhiteSpace(payloadJson) || payloadJson == "null")
            {
                return;
            }

            using var document = JsonDocument.Parse(payloadJson);
            var root = document.RootElement;
            if (root.ValueKind != JsonValueKind.Object)
            {
                return;
            }

            var kind = TryGetString(root, "kind");
            if (string.IsNullOrWhiteSpace(kind))
            {
                return;
            }

            var producerId = TryGetString(root, "producerId") ?? string.Empty;
            var participantName = TryGetString(root, "name") ?? string.Empty;
            var effectiveKind = ResolveRemoteProducerEffectiveKind(kind, producerId, participantName);
            UpdateParticipantTrackStateFromProducer(participantName, effectiveKind, producerId, enabled: false);
            var participant = FindParticipantByName(participantName);

            TrackRemoved?.Invoke(
                new MediaSfuTrackEvent
                {
                    RoomName = CurrentRoom?.RoomName ?? string.Empty,
                    Participant = participant,
                    Track = new MediaSfuTrack
                    {
                        TrackId = producerId,
                        ParticipantId = participant?.ParticipantId ?? participantName,
                        Kind = ResolveTrackKind(effectiveKind),
                        IsRemote = !participantName.Equals(_lastLocalUserName, StringComparison.OrdinalIgnoreCase),
                        IsMuted = true
                    }
                }
            );
        }

        private void UpdateCurrentRoomParticipants(List<MediaSfuParticipant> nextParticipants)
        {
            UpdateCurrentRoomState(
                nextRoom =>
                {
                    nextRoom.Participants.Clear();
                    if (nextParticipants == null)
                    {
                        return;
                    }

                    foreach (var participant in nextParticipants)
                    {
                        nextRoom.Participants.Add(CloneParticipant(participant));
                    }
                });

            MaybePrimeRemoteMediaBackendSocketSession();
        }

        private void UpdateCurrentRoomState(Action<MediaSfuRoom> applyMutation)
        {
            if (CurrentRoom == null || applyMutation == null)
            {
                return;
            }

            var previousRoom = CloneRoom(CurrentRoom);
            var nextRoom = CloneRoom(CurrentRoom);
            applyMutation(nextRoom);

            if (AreRoomsEquivalent(previousRoom, nextRoom))
            {
                return;
            }

            CurrentRoom = nextRoom;
            RoomChanged?.Invoke(
                new MediaSfuRoomChangedEvent
                {
                    PreviousRoom = previousRoom,
                    CurrentRoom = CloneRoom(CurrentRoom)
                }
            );

            PublishParticipantDiffs(previousRoom.Participants, CurrentRoom.Participants);
            ReconcileRemoteProducerState(previousRoom.Participants, CurrentRoom.Participants);
        }

        private void ReconcileRemoteProducerState(
            IReadOnlyList<MediaSfuParticipant> previousParticipants,
            IReadOnlyList<MediaSfuParticipant> nextParticipants)
        {
            var previousByKey = BuildParticipantLookup(previousParticipants);
            var nextByKey = BuildParticipantLookup(nextParticipants);

            foreach (var pair in nextByKey)
            {
                previousByKey.TryGetValue(pair.Key, out var previousParticipant);
                ReconcileRemoteProducerTrack(previousParticipant, pair.Value, "audio", previousParticipant?.AudioTrackId, pair.Value.AudioTrackId);
                ReconcileRemoteProducerTrack(previousParticipant, pair.Value, "video", previousParticipant?.VideoTrackId, pair.Value.VideoTrackId);
                ReconcileRemoteProducerTrack(previousParticipant, pair.Value, "screen", previousParticipant?.ScreenTrackId, pair.Value.ScreenTrackId);
            }

            foreach (var pair in previousByKey)
            {
                if (nextByKey.ContainsKey(pair.Key))
                {
                    continue;
                }

                ReconcileRemoteProducerTrack(pair.Value, null, "audio", pair.Value.AudioTrackId, string.Empty);
                ReconcileRemoteProducerTrack(pair.Value, null, "video", pair.Value.VideoTrackId, string.Empty);
                ReconcileRemoteProducerTrack(pair.Value, null, "screen", pair.Value.ScreenTrackId, string.Empty);
            }
        }

        private void ReconcileRemoteProducerTrack(
            MediaSfuParticipant previousParticipant,
            MediaSfuParticipant nextParticipant,
            string kind,
            string previousProducerId,
            string nextProducerId)
        {
            var participant = nextParticipant ?? previousParticipant;
            if (participant == null || participant.IsLocal)
            {
                return;
            }

            if (!string.IsNullOrWhiteSpace(_lastLocalUserName) &&
                participant.DisplayName.Equals(_lastLocalUserName, StringComparison.OrdinalIgnoreCase))
            {
                return;
            }

            if (!string.Equals(previousProducerId, nextProducerId, StringComparison.OrdinalIgnoreCase) &&
                ForgetKnownRemoteProducer(previousProducerId))
            {
                PublishRemoteProducerClosed(
                    new MediaSfuRemoteProducerClosedEvent
                    {
                        RemoteProducerId = previousProducerId,
                        RawPayload = BuildSyntheticRemoteProducerClosedPayload(previousProducerId, participant, kind)
                    });
            }

            if (!TryMarkKnownRemoteProducer(nextProducerId))
            {
                return;
            }

            PublishRemoteProducerAvailable(
                new MediaSfuRemoteProducerEvent
                {
                    ProducerId = nextProducerId,
                    IsLevel = ResolveRemoteProducerIsLevel(),
                    IsPipeProducer = false,
                    TranslationMetaJson = string.Empty,
                    RawPayload = BuildSyntheticRemoteProducerPayload(nextProducerId, participant, kind)
                });
        }

        private bool TryMarkKnownRemoteProducer(string producerId)
        {
            if (string.IsNullOrWhiteSpace(producerId))
            {
                return false;
            }

            lock (_knownRemoteProducerIdsGate)
            {
                return _knownRemoteProducerIds.Add(producerId);
            }
        }

        private bool ForgetKnownRemoteProducer(string producerId)
        {
            if (string.IsNullOrWhiteSpace(producerId))
            {
                return false;
            }

            lock (_knownRemoteProducerIdsGate)
            {
                return _knownRemoteProducerIds.Remove(producerId);
            }
        }

        private void ClearKnownRemoteProducers()
        {
            lock (_knownRemoteProducerIdsGate)
            {
                _knownRemoteProducerIds.Clear();
                _screenProducerHintIds.Clear();
            }
        }

        private bool TryMarkScreenProducerHint(string producerId)
        {
            if (string.IsNullOrWhiteSpace(producerId))
            {
                return false;
            }

            lock (_knownRemoteProducerIdsGate)
            {
                return _screenProducerHintIds.Add(producerId);
            }
        }

        private bool ForgetScreenProducerHint(string producerId)
        {
            if (string.IsNullOrWhiteSpace(producerId))
            {
                return false;
            }

            lock (_knownRemoteProducerIdsGate)
            {
                return _screenProducerHintIds.Remove(producerId);
            }
        }

        private bool IsScreenProducerHint(string producerId)
        {
            if (string.IsNullOrWhiteSpace(producerId))
            {
                return false;
            }

            lock (_knownRemoteProducerIdsGate)
            {
                return _screenProducerHintIds.Contains(producerId);
            }
        }

        private bool TryMarkScreenProducerParticipantHint(string participantName)
        {
            if (string.IsNullOrWhiteSpace(participantName))
            {
                return false;
            }

            lock (_knownRemoteProducerIdsGate)
            {
                return _screenProducerHintParticipantNames.Add(participantName);
            }
        }

        private bool TryConsumeScreenProducerParticipantHint(string participantName)
        {
            if (string.IsNullOrWhiteSpace(participantName))
            {
                return false;
            }

            lock (_knownRemoteProducerIdsGate)
            {
                return _screenProducerHintParticipantNames.Remove(participantName);
            }
        }

        private static bool IsScreenRequestIcon(string icon)
        {
            return string.Equals(icon?.Trim(), "fa-desktop", StringComparison.OrdinalIgnoreCase);
        }

        private static string BuildSyntheticRemoteProducerPayload(
            string producerId,
            MediaSfuParticipant participant,
            string kind)
        {
            return JsonSerializer.Serialize(
                new Dictionary<string, string>
                {
                    ["producerId"] = producerId ?? string.Empty,
                    ["remoteProducerId"] = producerId ?? string.Empty,
                    ["kind"] = kind ?? string.Empty,
                    ["name"] = participant?.DisplayName ?? string.Empty,
                    ["participantName"] = participant?.DisplayName ?? string.Empty,
                    ["participantId"] = participant?.ParticipantId ?? string.Empty,
                    ["source"] = "roomStateReconciliation"
                });
        }

        private static string BuildSyntheticRemoteProducerClosedPayload(
            string producerId,
            MediaSfuParticipant participant,
            string kind)
        {
            return JsonSerializer.Serialize(
                new Dictionary<string, string>
                {
                    ["producerId"] = producerId ?? string.Empty,
                    ["remoteProducerId"] = producerId ?? string.Empty,
                    ["kind"] = kind ?? string.Empty,
                    ["name"] = participant?.DisplayName ?? string.Empty,
                    ["participantName"] = participant?.DisplayName ?? string.Empty,
                    ["participantId"] = participant?.ParticipantId ?? string.Empty,
                    ["source"] = "roomStateReconciliation"
                });
        }

        private string ResolveRemoteProducerIsLevel()
        {
            return string.IsNullOrWhiteSpace(_lastLocalIsLevel)
                ? "0"
                : _lastLocalIsLevel;
        }

        private bool AreRoomsEquivalent(MediaSfuRoom left, MediaSfuRoom right)
        {
            if (ReferenceEquals(left, right))
            {
                return true;
            }

            if (left == null || right == null)
            {
                return false;
            }

            return string.Equals(left.RoomName, right.RoomName, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.PublicUrl, right.PublicUrl, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.Link, right.Link, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.Secret, right.Secret, StringComparison.Ordinal) &&
                   string.Equals(left.SecureCode, right.SecureCode, StringComparison.Ordinal) &&
                   string.Equals(left.ApiUserName, right.ApiUserName, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.CoHost, right.CoHost, StringComparison.OrdinalIgnoreCase) &&
                     left.PendingModerationCount == right.PendingModerationCount &&
                     string.Equals(left.LastWaitingParticipantName, right.LastWaitingParticipantName, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.AudioSetting, right.AudioSetting, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.VideoSetting, right.VideoSetting, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.ScreenshareSetting, right.ScreenshareSetting, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.ChatSetting, right.ChatSetting, StringComparison.OrdinalIgnoreCase) &&
                   left.HostRestrictedAudio == right.HostRestrictedAudio &&
                   left.HostRestrictedVideo == right.HostRestrictedVideo &&
                   left.HostRestrictedScreenshare == right.HostRestrictedScreenshare &&
                   left.HostRestrictedChat == right.HostRestrictedChat &&
                   left.ConfirmHereRequested == right.ConfirmHereRequested &&
                   left.MeetingTimeRemainingMs == right.MeetingTimeRemainingMs &&
                   left.AdminRestrictSetting == right.AdminRestrictSetting &&
                   AreRecordingParametersEquivalent(left.RecordingParameters, right.RecordingParameters) &&
                   AreRecordingStatesEquivalent(left.Recording, right.Recording) &&
                   AreLocalRequestStatesEquivalent(left.LocalRequests, right.LocalRequests) &&
                   AreRequestResponsesEquivalent(left.LastRequestResponse, right.LastRequestResponse) &&
                   ArePollListsEquivalent(left.Polls, right.Polls) &&
                   ArePollsEquivalent(left.ActivePoll, right.ActivePoll) &&
                   left.PollModalVisible == right.PollModalVisible &&
                   string.Equals(left.LastPollStatus, right.LastPollStatus, StringComparison.OrdinalIgnoreCase) &&
                   AreBreakoutStatesEquivalent(left.Breakout, right.Breakout) &&
                   left.MembersReceived == right.MembersReceived &&
                   string.Equals(left.ScreenProducerId, right.ScreenProducerId, StringComparison.OrdinalIgnoreCase) &&
                   left.ShareScreenStarted == right.ShareScreenStarted &&
                   left.DeferScreenReceived == right.DeferScreenReceived &&
                   AreConsumingDomainsEquivalent(left.ConsumingDomains, right.ConsumingDomains) &&
                   AreWhiteboardStatesEquivalent(left.Whiteboard, right.Whiteboard) &&
                   AreParticipantListsEquivalent(left.Participants, right.Participants) &&
                   AreRoomRequestListsEquivalent(left.PendingRequests, right.PendingRequests) &&
                   AreWaitingRoomParticipantListsEquivalent(left.WaitingRoomParticipants, right.WaitingRoomParticipants) &&
                   AreCoHostResponsibilityListsEquivalent(left.CoHostResponsibilities, right.CoHostResponsibilities);
        }

        private static bool AreRequestResponsesEquivalent(MediaSfuRequestResponse left, MediaSfuRequestResponse right)
        {
            if (ReferenceEquals(left, right))
            {
                return true;
            }

            if (left == null || right == null)
            {
                return false;
            }

            return string.Equals(left.RequestId, right.RequestId, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.Icon, right.Icon, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.DisplayName, right.DisplayName, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.UserName, right.UserName, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.Action, right.Action, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.Type, right.Type, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.RawPayload, right.RawPayload, StringComparison.Ordinal);
        }

        private static bool ArePollsEquivalent(MediaSfuPoll left, MediaSfuPoll right)
        {
            if (ReferenceEquals(left, right))
            {
                return true;
            }

            if (left == null || right == null)
            {
                return false;
            }

            return string.Equals(left.PollId, right.PollId, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.Question, right.Question, StringComparison.Ordinal) &&
                   string.Equals(left.Type, right.Type, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.Status, right.Status, StringComparison.OrdinalIgnoreCase) &&
                   AreStringListsEquivalent(left.Options, right.Options) &&
                   AreIntListsEquivalent(left.Votes, right.Votes) &&
                   AreVoteMapsEquivalent(left.Voters, right.Voters);
        }

        private static bool ArePollListsEquivalent(IReadOnlyList<MediaSfuPoll> left, IReadOnlyList<MediaSfuPoll> right)
        {
            var leftLookup = BuildPollLookup(left);
            var rightLookup = BuildPollLookup(right);
            if (leftLookup.Count != rightLookup.Count)
            {
                return false;
            }

            foreach (var pair in leftLookup)
            {
                if (!rightLookup.TryGetValue(pair.Key, out var otherPoll) ||
                    !ArePollsEquivalent(pair.Value, otherPoll))
                {
                    return false;
                }
            }

            return true;
        }

        private static Dictionary<string, MediaSfuPoll> BuildPollLookup(IReadOnlyList<MediaSfuPoll> polls)
        {
            var lookup = new Dictionary<string, MediaSfuPoll>(StringComparer.OrdinalIgnoreCase);
            if (polls == null)
            {
                return lookup;
            }

            foreach (var poll in polls)
            {
                var key = GetPollKey(poll);
                if (string.IsNullOrWhiteSpace(key) || lookup.ContainsKey(key))
                {
                    continue;
                }

                lookup[key] = poll;
            }

            return lookup;
        }

        private static bool AreStringListsEquivalent(IReadOnlyList<string> left, IReadOnlyList<string> right)
        {
            if (ReferenceEquals(left, right))
            {
                return true;
            }

            if (left == null || right == null || left.Count != right.Count)
            {
                return false;
            }

            for (var index = 0; index < left.Count; index += 1)
            {
                if (!string.Equals(left[index], right[index], StringComparison.Ordinal))
                {
                    return false;
                }
            }

            return true;
        }

        private static bool AreIntListsEquivalent(IReadOnlyList<int> left, IReadOnlyList<int> right)
        {
            if (ReferenceEquals(left, right))
            {
                return true;
            }

            if (left == null || right == null || left.Count != right.Count)
            {
                return false;
            }

            for (var index = 0; index < left.Count; index += 1)
            {
                if (left[index] != right[index])
                {
                    return false;
                }
            }

            return true;
        }

        private static bool AreVoteMapsEquivalent(IReadOnlyDictionary<string, int> left, IReadOnlyDictionary<string, int> right)
        {
            if (ReferenceEquals(left, right))
            {
                return true;
            }

            if (left == null || right == null || left.Count != right.Count)
            {
                return false;
            }

            foreach (var pair in left)
            {
                if (!right.TryGetValue(pair.Key, out var otherVote) || otherVote != pair.Value)
                {
                    return false;
                }
            }

            return true;
        }

        private static bool AreConsumingDomainsEquivalent(MediaSfuConsumingDomainsState left, MediaSfuConsumingDomainsState right)
        {
            if (ReferenceEquals(left, right))
            {
                return true;
            }

            if (left == null || right == null)
            {
                return false;
            }

            return left.HasAltDomains == right.HasAltDomains &&
                   string.Equals(left.AltDomainsJson, right.AltDomainsJson, StringComparison.Ordinal) &&
                   AreStringListsEquivalent(left.Domains, right.Domains);
        }

        private static bool AreWhiteboardStatesEquivalent(MediaSfuWhiteboardState left, MediaSfuWhiteboardState right)
        {
            if (ReferenceEquals(left, right))
            {
                return true;
            }

            if (left == null || right == null)
            {
                return false;
            }

            return string.Equals(left.LastAction, right.LastAction, StringComparison.OrdinalIgnoreCase) &&
                   left.Started == right.Started &&
                   left.Ended == right.Ended &&
                   left.CanStart == right.CanStart &&
                   left.UseImageBackground == right.UseImageBackground &&
                   AreWhiteboardUserListsEquivalent(left.Users, right.Users) &&
                   AreWhiteboardShapeListsEquivalent(left.Shapes, right.Shapes) &&
                   AreWhiteboardShapeStackEquivalent(left.RedoStack, right.RedoStack) &&
                   AreWhiteboardShapeStackEquivalent(left.UndoStack, right.UndoStack);
        }

        private static bool AreWhiteboardUserListsEquivalent(
            IReadOnlyList<MediaSfuWhiteboardUser> left,
            IReadOnlyList<MediaSfuWhiteboardUser> right)
        {
            if (ReferenceEquals(left, right))
            {
                return true;
            }

            if (left == null || right == null || left.Count != right.Count)
            {
                return false;
            }

            for (var index = 0; index < left.Count; index += 1)
            {
                if (!AreWhiteboardUsersEquivalent(left[index], right[index]))
                {
                    return false;
                }
            }

            return true;
        }

        private static bool AreWhiteboardUsersEquivalent(MediaSfuWhiteboardUser left, MediaSfuWhiteboardUser right)
        {
            if (ReferenceEquals(left, right))
            {
                return true;
            }

            if (left == null || right == null)
            {
                return false;
            }

            return string.Equals(left.Name, right.Name, StringComparison.OrdinalIgnoreCase) &&
                   left.UseBoard == right.UseBoard;
        }

        private static bool AreWhiteboardShapeStackEquivalent(
            IReadOnlyList<List<MediaSfuWhiteboardShape>> left,
            IReadOnlyList<List<MediaSfuWhiteboardShape>> right)
        {
            if (ReferenceEquals(left, right))
            {
                return true;
            }

            if (left == null || right == null || left.Count != right.Count)
            {
                return false;
            }

            for (var index = 0; index < left.Count; index += 1)
            {
                if (!AreWhiteboardShapeListsEquivalent(left[index], right[index]))
                {
                    return false;
                }
            }

            return true;
        }

        private static bool AreWhiteboardShapeListsEquivalent(
            IReadOnlyList<MediaSfuWhiteboardShape> left,
            IReadOnlyList<MediaSfuWhiteboardShape> right)
        {
            if (ReferenceEquals(left, right))
            {
                return true;
            }

            if (left == null || right == null || left.Count != right.Count)
            {
                return false;
            }

            for (var index = 0; index < left.Count; index += 1)
            {
                if (!AreWhiteboardShapesEquivalent(left[index], right[index]))
                {
                    return false;
                }
            }

            return true;
        }

        private static bool AreWhiteboardShapesEquivalent(MediaSfuWhiteboardShape left, MediaSfuWhiteboardShape right)
        {
            if (ReferenceEquals(left, right))
            {
                return true;
            }

            if (left == null || right == null)
            {
                return false;
            }

            return string.Equals(left.Type, right.Type, StringComparison.OrdinalIgnoreCase) &&
                   left.X == right.X &&
                   left.Y == right.Y &&
                   left.X1 == right.X1 &&
                   left.Y1 == right.Y1 &&
                   left.X2 == right.X2 &&
                   left.Y2 == right.Y2 &&
                   string.Equals(left.Color, right.Color, StringComparison.Ordinal) &&
                   left.Thickness.Equals(right.Thickness) &&
                   string.Equals(left.LineType, right.LineType, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.Text, right.Text, StringComparison.Ordinal) &&
                   string.Equals(left.FontFamily, right.FontFamily, StringComparison.Ordinal) &&
                   left.FontSize.Equals(right.FontSize) &&
                   string.Equals(left.ImageSrc, right.ImageSrc, StringComparison.Ordinal) &&
                   string.Equals(left.Signature, right.Signature, StringComparison.Ordinal) &&
                   AreWhiteboardPointListsEquivalent(left.Points, right.Points);
        }

        private static bool AreWhiteboardPointListsEquivalent(
            IReadOnlyList<MediaSfuWhiteboardPoint> left,
            IReadOnlyList<MediaSfuWhiteboardPoint> right)
        {
            if (ReferenceEquals(left, right))
            {
                return true;
            }

            if (left == null || right == null || left.Count != right.Count)
            {
                return false;
            }

            for (var index = 0; index < left.Count; index += 1)
            {
                var leftPoint = left[index];
                var rightPoint = right[index];
                if (leftPoint == null || rightPoint == null)
                {
                    if (!ReferenceEquals(leftPoint, rightPoint))
                    {
                        return false;
                    }

                    continue;
                }

                if (!leftPoint.X.Equals(rightPoint.X) || !leftPoint.Y.Equals(rightPoint.Y))
                {
                    return false;
                }
            }

            return true;
        }

        private static bool AreBreakoutStatesEquivalent(MediaSfuBreakoutState left, MediaSfuBreakoutState right)
        {
            if (ReferenceEquals(left, right))
            {
                return true;
            }

            if (left == null || right == null)
            {
                return false;
            }

            return left.Started == right.Started &&
                   left.Ended == right.Ended &&
                   left.HostNewRoom == right.HostNewRoom &&
                   string.Equals(left.Status, right.Status, StringComparison.OrdinalIgnoreCase) &&
                   AreBreakoutRoomListsEquivalent(left.Rooms, right.Rooms);
        }

        private static bool AreBreakoutRoomListsEquivalent(
            IReadOnlyList<List<MediaSfuBreakoutParticipant>> left,
            IReadOnlyList<List<MediaSfuBreakoutParticipant>> right)
        {
            if (ReferenceEquals(left, right))
            {
                return true;
            }

            if (left == null || right == null || left.Count != right.Count)
            {
                return false;
            }

            for (var index = 0; index < left.Count; index += 1)
            {
                if (!AreBreakoutParticipantListsEquivalent(left[index], right[index]))
                {
                    return false;
                }
            }

            return true;
        }

        private static bool AreBreakoutParticipantListsEquivalent(
            IReadOnlyList<MediaSfuBreakoutParticipant> left,
            IReadOnlyList<MediaSfuBreakoutParticipant> right)
        {
            if (ReferenceEquals(left, right))
            {
                return true;
            }

            if (left == null || right == null || left.Count != right.Count)
            {
                return false;
            }

            for (var index = 0; index < left.Count; index += 1)
            {
                if (!AreBreakoutParticipantsEquivalent(left[index], right[index]))
                {
                    return false;
                }
            }

            return true;
        }

        private static bool AreBreakoutParticipantsEquivalent(MediaSfuBreakoutParticipant left, MediaSfuBreakoutParticipant right)
        {
            if (ReferenceEquals(left, right))
            {
                return true;
            }

            if (left == null || right == null)
            {
                return false;
            }

            return string.Equals(left.DisplayName, right.DisplayName, StringComparison.OrdinalIgnoreCase) &&
                   left.BreakRoom == right.BreakRoom;
        }

        private static bool AreLocalRequestStatesEquivalent(MediaSfuLocalRequestState left, MediaSfuLocalRequestState right)
        {
            if (ReferenceEquals(left, right))
            {
                return true;
            }

            if (left == null || right == null)
            {
                return false;
            }

            return string.Equals(left.AudioRequestState, right.AudioRequestState, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.VideoRequestState, right.VideoRequestState, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.ScreenshareRequestState, right.ScreenshareRequestState, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.ChatRequestState, right.ChatRequestState, StringComparison.OrdinalIgnoreCase) &&
                   left.AudioRequestRetryAtEpochMs == right.AudioRequestRetryAtEpochMs &&
                   left.VideoRequestRetryAtEpochMs == right.VideoRequestRetryAtEpochMs &&
                   left.ScreenshareRequestRetryAtEpochMs == right.ScreenshareRequestRetryAtEpochMs &&
                   left.ChatRequestRetryAtEpochMs == right.ChatRequestRetryAtEpochMs &&
                   left.AudioActionGranted == right.AudioActionGranted &&
                   left.VideoActionGranted == right.VideoActionGranted &&
                   left.ScreenshareActionGranted == right.ScreenshareActionGranted &&
                   left.ChatActionGranted == right.ChatActionGranted;
        }

        private static bool AreRecordingParametersEquivalent(MediaSfuRecordingParameters left, MediaSfuRecordingParameters right)
        {
            if (ReferenceEquals(left, right))
            {
                return true;
            }

            if (left == null || right == null)
            {
                return false;
            }

            return left.RecordingAudioPausesLimit == right.RecordingAudioPausesLimit &&
                   left.RecordingAudioSupport == right.RecordingAudioSupport &&
                   left.RecordingAudioPeopleLimit == right.RecordingAudioPeopleLimit &&
                   left.RecordingAudioParticipantsTimeLimit == right.RecordingAudioParticipantsTimeLimit &&
                   left.RecordingVideoPausesLimit == right.RecordingVideoPausesLimit &&
                   left.RecordingVideoSupport == right.RecordingVideoSupport &&
                   left.RecordingVideoPeopleLimit == right.RecordingVideoPeopleLimit &&
                   left.RecordingVideoParticipantsTimeLimit == right.RecordingVideoParticipantsTimeLimit &&
                   left.RecordingAllParticipantsSupport == right.RecordingAllParticipantsSupport &&
                   left.RecordingVideoParticipantsSupport == right.RecordingVideoParticipantsSupport &&
                   left.RecordingAllParticipantsFullRoomSupport == right.RecordingAllParticipantsFullRoomSupport &&
                   left.RecordingVideoParticipantsFullRoomSupport == right.RecordingVideoParticipantsFullRoomSupport &&
                   string.Equals(left.RecordingPreferredOrientation, right.RecordingPreferredOrientation, StringComparison.OrdinalIgnoreCase) &&
                   left.RecordingSupportForOtherOrientation == right.RecordingSupportForOtherOrientation &&
                   left.RecordingMultiFormatsSupport == right.RecordingMultiFormatsSupport &&
                   left.RecordingHlsSupport == right.RecordingHlsSupport &&
                   left.RecordingAudioPausesCount == right.RecordingAudioPausesCount &&
                   left.RecordingVideoPausesCount == right.RecordingVideoPausesCount;
        }

        private static bool AreRecordingStatesEquivalent(MediaSfuRecordingState left, MediaSfuRecordingState right)
        {
            if (ReferenceEquals(left, right))
            {
                return true;
            }

            if (left == null || right == null)
            {
                return false;
            }

            return string.Equals(left.State, right.State, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.LastNoticeState, right.LastNoticeState, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.LastStopReason, right.LastStopReason, StringComparison.Ordinal) &&
                   string.Equals(left.ProgressTime, right.ProgressTime, StringComparison.Ordinal) &&
                   left.RecordElapsedTimeSeconds == right.RecordElapsedTimeSeconds &&
                   left.RecordStartTimeEpochMs == right.RecordStartTimeEpochMs &&
                   left.PauseCount == right.PauseCount &&
                   left.TimeLeftSeconds == right.TimeLeftSeconds &&
                   left.RecordStarted == right.RecordStarted &&
                   left.RecordPaused == right.RecordPaused &&
                   left.RecordStopped == right.RecordStopped &&
                   left.CanLaunchRecord == right.CanLaunchRecord &&
                   left.CanPauseResume == right.CanPauseResume &&
                   left.ShowRecordButtons == right.ShowRecordButtons &&
                   left.IsTimerRunning == right.IsTimerRunning &&
                   AreUserRecordingParamsEquivalent(left.UserRecordingParams, right.UserRecordingParams);
        }

        private static bool AreUserRecordingParamsEquivalent(MediaSfuUserRecordingParams left, MediaSfuUserRecordingParams right)
        {
            if (ReferenceEquals(left, right))
            {
                return true;
            }

            if (left == null || right == null)
            {
                return false;
            }

            return AreRecordingMainSpecsEquivalent(left.MainSpecs, right.MainSpecs) &&
                   AreRecordingDisplaySpecsEquivalent(left.DisplaySpecs, right.DisplaySpecs) &&
                   AreRecordingTextSpecsEquivalent(left.TextSpecs, right.TextSpecs) &&
                   string.Equals(left.RawPayload, right.RawPayload, StringComparison.Ordinal);
        }

        private static bool AreRecordingMainSpecsEquivalent(MediaSfuRecordingMainSpecs left, MediaSfuRecordingMainSpecs right)
        {
            if (ReferenceEquals(left, right))
            {
                return true;
            }

            if (left == null || right == null)
            {
                return false;
            }

            return string.Equals(left.MediaOptions, right.MediaOptions, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.AudioOptions, right.AudioOptions, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.VideoOptions, right.VideoOptions, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.VideoType, right.VideoType, StringComparison.OrdinalIgnoreCase) &&
                   left.VideoOptimized == right.VideoOptimized &&
                   string.Equals(left.RecordingDisplayType, right.RecordingDisplayType, StringComparison.OrdinalIgnoreCase) &&
                   left.AddHls == right.AddHls;
        }

        private static bool AreRecordingDisplaySpecsEquivalent(MediaSfuRecordingDisplaySpecs left, MediaSfuRecordingDisplaySpecs right)
        {
            if (ReferenceEquals(left, right))
            {
                return true;
            }

            if (left == null || right == null)
            {
                return false;
            }

            return left.NameTags == right.NameTags &&
                   string.Equals(left.BackgroundColor, right.BackgroundColor, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.NameTagsColor, right.NameTagsColor, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.OrientationVideo, right.OrientationVideo, StringComparison.OrdinalIgnoreCase);
        }

        private static bool AreRecordingTextSpecsEquivalent(MediaSfuRecordingTextSpecs left, MediaSfuRecordingTextSpecs right)
        {
            if (ReferenceEquals(left, right))
            {
                return true;
            }

            if (left == null || right == null)
            {
                return false;
            }

            return left.AddText == right.AddText &&
                   string.Equals(left.CustomText, right.CustomText, StringComparison.Ordinal) &&
                   string.Equals(left.CustomTextPosition, right.CustomTextPosition, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.CustomTextColor, right.CustomTextColor, StringComparison.OrdinalIgnoreCase);
        }

        private void PublishParticipantDiffs(
            IReadOnlyList<MediaSfuParticipant> previousParticipants,
            IReadOnlyList<MediaSfuParticipant> nextParticipants)
        {
            var previousByKey = BuildParticipantLookup(previousParticipants);
            var nextByKey = BuildParticipantLookup(nextParticipants);

            foreach (var pair in nextByKey)
            {
                if (!previousByKey.ContainsKey(pair.Key))
                {
                    ParticipantJoined?.Invoke(
                        new MediaSfuParticipantEvent
                        {
                            RoomName = CurrentRoom?.RoomName ?? string.Empty,
                            Participant = CloneParticipant(pair.Value)
                        }
                    );
                }
            }

            foreach (var pair in previousByKey)
            {
                if (!nextByKey.ContainsKey(pair.Key))
                {
                    ParticipantLeft?.Invoke(
                        new MediaSfuParticipantEvent
                        {
                            RoomName = CurrentRoom?.RoomName ?? string.Empty,
                            Participant = CloneParticipant(pair.Value)
                        }
                    );
                }
            }
        }

        private Dictionary<string, MediaSfuParticipant> BuildParticipantLookup(IReadOnlyList<MediaSfuParticipant> participants)
        {
            var lookup = new Dictionary<string, MediaSfuParticipant>(StringComparer.OrdinalIgnoreCase);
            if (participants == null)
            {
                return lookup;
            }

            foreach (var participant in participants)
            {
                var key = GetParticipantKey(participant);
                if (string.IsNullOrWhiteSpace(key) || lookup.ContainsKey(key))
                {
                    continue;
                }

                lookup[key] = participant;
            }

            return lookup;
        }

        private bool AreParticipantListsEquivalent(
            IReadOnlyList<MediaSfuParticipant> left,
            IReadOnlyList<MediaSfuParticipant> right)
        {
            var leftLookup = BuildParticipantLookup(left);
            var rightLookup = BuildParticipantLookup(right);
            if (leftLookup.Count != rightLookup.Count)
            {
                return false;
            }

            foreach (var pair in leftLookup)
            {
                if (!rightLookup.TryGetValue(pair.Key, out var otherParticipant) ||
                    !AreParticipantsEquivalent(pair.Value, otherParticipant))
                {
                    return false;
                }
            }

            return true;
        }

        private static bool AreParticipantsEquivalent(MediaSfuParticipant left, MediaSfuParticipant right)
        {
            return string.Equals(left.ParticipantId, right.ParticipantId, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.DisplayName, right.DisplayName, StringComparison.OrdinalIgnoreCase) &&
                   left.Role == right.Role &&
                   left.IsLocal == right.IsLocal &&
                   left.AudioOn == right.AudioOn &&
                   left.VideoOn == right.VideoOn &&
                                     left.ScreenOn == right.ScreenOn &&
                                     string.Equals(left.AudioTrackId, right.AudioTrackId, StringComparison.OrdinalIgnoreCase) &&
                                     string.Equals(left.VideoTrackId, right.VideoTrackId, StringComparison.OrdinalIgnoreCase) &&
                                     string.Equals(left.ScreenTrackId, right.ScreenTrackId, StringComparison.OrdinalIgnoreCase);
        }

        private static bool AreRoomRequestListsEquivalent(
            IReadOnlyList<MediaSfuRoomRequest> left,
            IReadOnlyList<MediaSfuRoomRequest> right)
        {
            var leftLookup = BuildRoomRequestLookup(left);
            var rightLookup = BuildRoomRequestLookup(right);
            if (leftLookup.Count != rightLookup.Count)
            {
                return false;
            }

            foreach (var pair in leftLookup)
            {
                if (!rightLookup.TryGetValue(pair.Key, out var otherRequest) ||
                    !AreRoomRequestsEquivalent(pair.Value, otherRequest))
                {
                    return false;
                }
            }

            return true;
        }

        private static bool AreRoomRequestsEquivalent(MediaSfuRoomRequest left, MediaSfuRoomRequest right)
        {
            return string.Equals(left.RequestId, right.RequestId, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.Icon, right.Icon, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.DisplayName, right.DisplayName, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.UserName, right.UserName, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.RawPayload, right.RawPayload, StringComparison.Ordinal);
        }

        private static bool AreWaitingRoomParticipantListsEquivalent(
            IReadOnlyList<MediaSfuWaitingRoomParticipant> left,
            IReadOnlyList<MediaSfuWaitingRoomParticipant> right)
        {
            var leftLookup = BuildWaitingRoomParticipantLookup(left);
            var rightLookup = BuildWaitingRoomParticipantLookup(right);
            if (leftLookup.Count != rightLookup.Count)
            {
                return false;
            }

            foreach (var pair in leftLookup)
            {
                if (!rightLookup.TryGetValue(pair.Key, out var otherParticipant) ||
                    !AreWaitingRoomParticipantsEquivalent(pair.Value, otherParticipant))
                {
                    return false;
                }
            }

            return true;
        }

        private static bool AreWaitingRoomParticipantsEquivalent(
            MediaSfuWaitingRoomParticipant left,
            MediaSfuWaitingRoomParticipant right)
        {
            return string.Equals(left.ParticipantId, right.ParticipantId, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.DisplayName, right.DisplayName, StringComparison.OrdinalIgnoreCase) &&
                   string.Equals(left.RawPayload, right.RawPayload, StringComparison.Ordinal);
        }

        private static bool AreCoHostResponsibilityListsEquivalent(
            IReadOnlyList<MediaSfuCoHostResponsibility> left,
            IReadOnlyList<MediaSfuCoHostResponsibility> right)
        {
            var leftLookup = BuildCoHostResponsibilityLookup(left);
            var rightLookup = BuildCoHostResponsibilityLookup(right);
            if (leftLookup.Count != rightLookup.Count)
            {
                return false;
            }

            foreach (var pair in leftLookup)
            {
                if (!rightLookup.TryGetValue(pair.Key, out var otherResponsibility) ||
                    !AreCoHostResponsibilitiesEquivalent(pair.Value, otherResponsibility))
                {
                    return false;
                }
            }

            return true;
        }

        private static bool AreCoHostResponsibilitiesEquivalent(
            MediaSfuCoHostResponsibility left,
            MediaSfuCoHostResponsibility right)
        {
            return string.Equals(left.Name, right.Name, StringComparison.OrdinalIgnoreCase) &&
                   left.Value == right.Value &&
                   left.Dedicated == right.Dedicated;
        }

        private static Dictionary<string, MediaSfuRoomRequest> BuildRoomRequestLookup(
            IReadOnlyList<MediaSfuRoomRequest> requests)
        {
            var lookup = new Dictionary<string, MediaSfuRoomRequest>(StringComparer.OrdinalIgnoreCase);
            if (requests == null)
            {
                return lookup;
            }

            foreach (var request in requests)
            {
                var key = GetRoomRequestKey(request);
                if (string.IsNullOrWhiteSpace(key) || lookup.ContainsKey(key))
                {
                    continue;
                }

                lookup[key] = request;
            }

            return lookup;
        }

        private static Dictionary<string, MediaSfuCoHostResponsibility> BuildCoHostResponsibilityLookup(
            IReadOnlyList<MediaSfuCoHostResponsibility> responsibilities)
        {
            var lookup = new Dictionary<string, MediaSfuCoHostResponsibility>(StringComparer.OrdinalIgnoreCase);
            if (responsibilities == null)
            {
                return lookup;
            }

            foreach (var responsibility in responsibilities)
            {
                var key = NormalizeParticipantKey(responsibility?.Name);
                if (string.IsNullOrWhiteSpace(key) || lookup.ContainsKey(key))
                {
                    continue;
                }

                lookup[key] = responsibility;
            }

            return lookup;
        }

        private static Dictionary<string, MediaSfuWaitingRoomParticipant> BuildWaitingRoomParticipantLookup(
            IReadOnlyList<MediaSfuWaitingRoomParticipant> participants)
        {
            var lookup = new Dictionary<string, MediaSfuWaitingRoomParticipant>(StringComparer.OrdinalIgnoreCase);
            if (participants == null)
            {
                return lookup;
            }

            foreach (var participant in participants)
            {
                var key = GetWaitingRoomParticipantKey(participant);
                if (string.IsNullOrWhiteSpace(key) || lookup.ContainsKey(key))
                {
                    continue;
                }

                lookup[key] = participant;
            }

            return lookup;
        }

        private static void AppendWhiteboardShape(List<MediaSfuWhiteboardShape> shapes, MediaSfuWhiteboardShape shape)
        {
            if (shapes == null || shape == null)
            {
                return;
            }

            shape.Signature = BuildWhiteboardShapeSignature(shape);
            shapes.Add(shape);
        }

        private static void ReplaceWhiteboardShapes(List<MediaSfuWhiteboardShape> shapes, JsonElement? payloadElement)
        {
            if (shapes == null || !payloadElement.HasValue || payloadElement.Value.ValueKind != JsonValueKind.Object)
            {
                return;
            }

            if (!payloadElement.Value.TryGetProperty("shapes", out var shapesElement) ||
                shapesElement.ValueKind != JsonValueKind.Array)
            {
                return;
            }

            var nextShapes = new List<MediaSfuWhiteboardShape>();
            foreach (var shapeElement in shapesElement.EnumerateArray())
            {
                AppendWhiteboardShape(nextShapes, CreateWhiteboardShapeFromElement(shapeElement));
            }

            shapes.Clear();
            shapes.AddRange(nextShapes);
        }

        private static void ReplaceWhiteboardUsers(List<MediaSfuWhiteboardUser> users, JsonElement? usersElement)
        {
            if (users == null || !usersElement.HasValue || usersElement.Value.ValueKind != JsonValueKind.Array)
            {
                return;
            }

            users.Clear();
            foreach (var userElement in usersElement.Value.EnumerateArray())
            {
                var user = CreateWhiteboardUser(userElement);
                if (user != null)
                {
                    users.Add(user);
                }
            }
        }

        private static void ApplyWhiteboardErase(List<MediaSfuWhiteboardShape> shapes, JsonElement? payloadElement)
        {
            if (shapes == null || !payloadElement.HasValue || payloadElement.Value.ValueKind != JsonValueKind.Object)
            {
                return;
            }

            var x = TryGetFloat(payloadElement.Value, "x") ?? 0f;
            var y = TryGetFloat(payloadElement.Value, "y") ?? 0f;
            var radius = (TryGetFloat(payloadElement.Value, "thickness") ?? 20f) / 2f;
            shapes.RemoveAll(shape => IsWhiteboardShapeNearPoint(shape, x, y, radius));
        }

        private static void ApplyWhiteboardDeleteShape(List<MediaSfuWhiteboardShape> shapes, JsonElement? payloadElement)
        {
            if (shapes == null)
            {
                return;
            }

            var signature = BuildWhiteboardShapeSignature(payloadElement);
            if (string.IsNullOrWhiteSpace(signature))
            {
                return;
            }

            shapes.RemoveAll(shape => string.Equals(shape?.Signature, signature, StringComparison.Ordinal));
        }

        private static void ApplyWhiteboardUndo(MediaSfuWhiteboardState whiteboard)
        {
            if (whiteboard?.Shapes == null || whiteboard.Shapes.Count == 0)
            {
                return;
            }

            whiteboard.RedoStack.Add(CloneWhiteboardShapeList(whiteboard.Shapes));
            whiteboard.Shapes.RemoveAt(whiteboard.Shapes.Count - 1);
        }

        private static void ApplyWhiteboardRedo(MediaSfuWhiteboardState whiteboard)
        {
            if (whiteboard?.RedoStack == null || whiteboard.RedoStack.Count == 0)
            {
                return;
            }

            whiteboard.UndoStack.Add(CloneWhiteboardShapeList(whiteboard.Shapes));
            var lastIndex = whiteboard.RedoStack.Count - 1;
            var restoredShapes = CloneWhiteboardShapeList(whiteboard.RedoStack[lastIndex]);
            whiteboard.RedoStack.RemoveAt(lastIndex);
            whiteboard.Shapes.Clear();
            whiteboard.Shapes.AddRange(restoredShapes);
        }

        private static MediaSfuWhiteboardShape CreateWhiteboardDrawShape(JsonElement? payloadElement)
        {
            if (!payloadElement.HasValue || payloadElement.Value.ValueKind != JsonValueKind.Object)
            {
                return null;
            }

            var type = NormalizeWhiteboardShapeType(TryGetString(payloadElement.Value, "type"));
            if (string.Equals(type, "freehand", StringComparison.OrdinalIgnoreCase))
            {
                var shape = new MediaSfuWhiteboardShape
                {
                    Type = type,
                    Color = TryGetString(payloadElement.Value, "color") ?? string.Empty,
                    Thickness = TryGetFloat(payloadElement.Value, "thickness") ?? 6f,
                    LineType = NormalizeWhiteboardLineType(TryGetString(payloadElement.Value, "lineType"))
                };

                if (TryGetNestedElement(payloadElement.Value, "points") is JsonElement pointsElement &&
                    pointsElement.ValueKind == JsonValueKind.Array)
                {
                    shape.Points.AddRange(ParseWhiteboardPoints(pointsElement));
                }

                shape.Signature = BuildWhiteboardShapeSignature(shape);
                return shape;
            }

            return CreateWhiteboardShapeFromElement(payloadElement);
        }

        private static MediaSfuWhiteboardShape CreateWhiteboardTextShape(JsonElement? payloadElement)
        {
            if (!payloadElement.HasValue || payloadElement.Value.ValueKind != JsonValueKind.Object)
            {
                return null;
            }

            var shape = new MediaSfuWhiteboardShape
            {
                Type = "text",
                X = TryGetFloat(payloadElement.Value, "x") ?? TryGetFloat(payloadElement.Value, "x1"),
                Y = TryGetFloat(payloadElement.Value, "y") ?? TryGetFloat(payloadElement.Value, "y1"),
                Text = TryGetString(payloadElement.Value, "text") ?? string.Empty,
                Color = TryGetString(payloadElement.Value, "color") ?? string.Empty,
                Thickness = 1f,
                FontFamily = TryGetString(payloadElement.Value, "font", "fontFamily") ?? "Arial",
                FontSize = TryGetFloat(payloadElement.Value, "fontSize") ?? 20f,
                LineType = "solid"
            };

            shape.Signature = BuildWhiteboardShapeSignature(shape);
            return shape;
        }

        private static MediaSfuWhiteboardShape CreateWhiteboardImageShape(JsonElement? payloadElement)
        {
            if (!payloadElement.HasValue || payloadElement.Value.ValueKind != JsonValueKind.Object)
            {
                return null;
            }

            var shape = new MediaSfuWhiteboardShape
            {
                Type = "image",
                X1 = TryGetFloat(payloadElement.Value, "x1"),
                Y1 = TryGetFloat(payloadElement.Value, "y1"),
                X2 = TryGetFloat(payloadElement.Value, "x2"),
                Y2 = TryGetFloat(payloadElement.Value, "y2"),
                ImageSrc = TryGetString(payloadElement.Value, "src", "imageSrc") ?? string.Empty,
                Thickness = 1f,
                Color = string.Empty,
                LineType = "solid"
            };

            shape.Signature = BuildWhiteboardShapeSignature(shape);
            return shape;
        }

        private static MediaSfuWhiteboardShape CreateWhiteboardShapeFromElement(JsonElement? payloadElement)
        {
            if (!payloadElement.HasValue || payloadElement.Value.ValueKind != JsonValueKind.Object)
            {
                return null;
            }

            var payload = payloadElement.Value;
            var type = NormalizeWhiteboardShapeType(TryGetString(payload, "type"));
            if (string.Equals(type, "text", StringComparison.OrdinalIgnoreCase))
            {
                return CreateWhiteboardTextShape(payloadElement);
            }

            if (string.Equals(type, "image", StringComparison.OrdinalIgnoreCase))
            {
                return CreateWhiteboardImageShape(payloadElement);
            }

            var shape = new MediaSfuWhiteboardShape
            {
                Type = string.IsNullOrWhiteSpace(type) ? "line" : type,
                X1 = TryGetFloat(payload, "x1"),
                Y1 = TryGetFloat(payload, "y1"),
                X2 = TryGetFloat(payload, "x2"),
                Y2 = TryGetFloat(payload, "y2"),
                Color = TryGetString(payload, "color") ?? string.Empty,
                Thickness = TryGetFloat(payload, "thickness") ?? 6f,
                LineType = NormalizeWhiteboardLineType(TryGetString(payload, "lineType"))
            };

            if (string.Equals(shape.Type, "freehand", StringComparison.OrdinalIgnoreCase) &&
                TryGetNestedElement(payload, "points") is JsonElement pointsElement &&
                pointsElement.ValueKind == JsonValueKind.Array)
            {
                shape.Points.AddRange(ParseWhiteboardPoints(pointsElement));
            }

            shape.Signature = BuildWhiteboardShapeSignature(shape);
            return shape;
        }

        private static MediaSfuWhiteboardUser CreateWhiteboardUser(JsonElement userElement)
        {
            if (userElement.ValueKind != JsonValueKind.Object)
            {
                return null;
            }

            var name = TryGetString(userElement, "name");
            if (string.IsNullOrWhiteSpace(name))
            {
                return null;
            }

            return new MediaSfuWhiteboardUser
            {
                Name = name,
                UseBoard = ParseFlexibleBool(userElement, true, "useBoard")
            };
        }

        private static List<MediaSfuWhiteboardPoint> ParseWhiteboardPoints(JsonElement pointsElement)
        {
            var points = new List<MediaSfuWhiteboardPoint>();
            foreach (var pointElement in pointsElement.EnumerateArray())
            {
                if (pointElement.ValueKind != JsonValueKind.Object)
                {
                    continue;
                }

                points.Add(
                    new MediaSfuWhiteboardPoint
                    {
                        X = TryGetFloat(pointElement, "x") ?? 0f,
                        Y = TryGetFloat(pointElement, "y") ?? 0f
                    });
            }

            return points;
        }

        private static bool IsWhiteboardShapeNearPoint(MediaSfuWhiteboardShape shape, float x, float y, float radius)
        {
            if (shape == null)
            {
                return false;
            }

            if (string.Equals(shape.Type, "freehand", StringComparison.OrdinalIgnoreCase) && shape.Points != null)
            {
                foreach (var point in shape.Points)
                {
                    if (Distance(point.X, point.Y, x, y) < radius)
                    {
                        return true;
                    }
                }

                return false;
            }

            var startX = shape.X ?? shape.X1;
            var startY = shape.Y ?? shape.Y1;
            if (!startX.HasValue || !startY.HasValue)
            {
                return false;
            }

            var endX = shape.X2 ?? startX.Value;
            var endY = shape.Y2 ?? startY.Value;
            var centerX = (startX.Value + endX) / 2f;
            var centerY = (startY.Value + endY) / 2f;
            var extentX = Math.Abs(endX - startX.Value);
            var extentY = Math.Abs(endY - startY.Value);
            var threshold = radius + (float)Math.Max(extentX, extentY) / 2f;
            return Distance(centerX, centerY, x, y) < threshold;
        }

        private static float Distance(float x1, float y1, float x2, float y2)
        {
            var dx = x1 - x2;
            var dy = y1 - y2;
            return (float)Math.Sqrt((dx * dx) + (dy * dy));
        }

        private static string BuildWhiteboardShapeSignature(JsonElement? payloadElement)
        {
            return CreateWhiteboardShapeFromElement(payloadElement)?.Signature ?? string.Empty;
        }

        private static string BuildWhiteboardShapeSignature(MediaSfuWhiteboardShape shape)
        {
            if (shape == null)
            {
                return string.Empty;
            }

            var builder = new StringBuilder();
            builder.Append(NormalizeWhiteboardShapeType(shape.Type));
            builder.Append('|').Append(FormatWhiteboardNumber(shape.X));
            builder.Append('|').Append(FormatWhiteboardNumber(shape.Y));
            builder.Append('|').Append(FormatWhiteboardNumber(shape.X1));
            builder.Append('|').Append(FormatWhiteboardNumber(shape.Y1));
            builder.Append('|').Append(FormatWhiteboardNumber(shape.X2));
            builder.Append('|').Append(FormatWhiteboardNumber(shape.Y2));
            builder.Append('|').Append(shape.Color ?? string.Empty);
            builder.Append('|').Append(FormatWhiteboardNumber(shape.Thickness));
            builder.Append('|').Append(NormalizeWhiteboardLineType(shape.LineType));
            builder.Append('|').Append(shape.Text ?? string.Empty);
            builder.Append('|').Append(shape.FontFamily ?? string.Empty);
            builder.Append('|').Append(FormatWhiteboardNumber(shape.FontSize));
            builder.Append('|').Append(shape.ImageSrc ?? string.Empty);

            if (shape.Points != null)
            {
                foreach (var point in shape.Points)
                {
                    builder.Append('|').Append(FormatWhiteboardNumber(point?.X));
                    builder.Append(':').Append(FormatWhiteboardNumber(point?.Y));
                }
            }

            return builder.ToString();
        }

        private static string FormatWhiteboardNumber(float? value)
        {
            return value.HasValue
                ? value.Value.ToString("0.###", CultureInfo.InvariantCulture)
                : string.Empty;
        }

        private static string NormalizeWhiteboardShapeType(string value)
        {
            if (string.IsNullOrWhiteSpace(value))
            {
                return string.Empty;
            }

            switch (value.Trim().ToLowerInvariant())
            {
                case "rect":
                    return "rectangle";
                case "ellipse":
                    return "circle";
                case "dash_dot":
                case "dashdot":
                    return "dashdot";
                default:
                    return value.Trim().ToLowerInvariant();
            }
        }

        private static string NormalizeWhiteboardLineType(string value)
        {
            if (string.IsNullOrWhiteSpace(value))
            {
                return "solid";
            }

            switch (value.Trim().ToLowerInvariant())
            {
                case "dash_dot":
                    return "dashdot";
                default:
                    return value.Trim().ToLowerInvariant();
            }
        }

        private MediaSfuParticipant FindParticipantByName(string name)
        {
            if (CurrentRoom?.Participants == null || string.IsNullOrWhiteSpace(name))
            {
                return null;
            }

            foreach (var participant in CurrentRoom.Participants)
            {
                if (participant.DisplayName.Equals(name, StringComparison.OrdinalIgnoreCase))
                {
                    return participant;
                }
            }

            return null;
        }

        private MediaSfuParticipant FindLocalParticipant()
        {
            if (CurrentRoom?.Participants == null)
            {
                return null;
            }

            foreach (var participant in CurrentRoom.Participants)
            {
                if (participant.IsLocal)
                {
                    return participant;
                }
            }

            return string.IsNullOrWhiteSpace(_lastLocalUserName)
                ? null
                : FindParticipantByName(_lastLocalUserName);
        }

        private void UpdateParticipantTrackState(string participantName, string kind, bool enabled)
        {
            if (CurrentRoom?.Participants == null || string.IsNullOrWhiteSpace(participantName))
            {
                return;
            }

            var participants = new List<MediaSfuParticipant>(CurrentRoom.Participants.Count);
            var changed = false;

            foreach (var participant in CurrentRoom.Participants)
            {
                if (!participant.DisplayName.Equals(participantName, StringComparison.OrdinalIgnoreCase))
                {
                    participants.Add(CloneParticipant(participant));
                    continue;
                }

                var updatedParticipant = CloneParticipant(participant);
                switch (kind.ToLowerInvariant())
                {
                    case "audio":
                        if (updatedParticipant.AudioOn != enabled)
                        {
                            updatedParticipant.AudioOn = enabled;
                            changed = true;
                        }
                        break;
                    case "video":
                        if (updatedParticipant.VideoOn != enabled)
                        {
                            updatedParticipant.VideoOn = enabled;
                            changed = true;
                        }
                        break;
                    case "screen":
                    case "screenshare":
                    case "screen-share":
                        if (updatedParticipant.ScreenOn != enabled)
                        {
                            updatedParticipant.ScreenOn = enabled;
                            changed = true;
                        }
                        break;
                }

                participants.Add(updatedParticipant);
            }

            if (changed)
            {
                UpdateCurrentRoomParticipants(participants);
            }

            if (!enabled && IsScreenMediaTag(kind))
            {
                ClearScreenShareRoomStateIfNoParticipantsSharing();
            }
        }

        private void ClearScreenShareRoomStateIfNoParticipantsSharing()
        {
            if (CurrentRoom == null || HasParticipantSharingScreen(CurrentRoom.Participants))
            {
                return;
            }

            UpdateCurrentRoomState(
                nextRoom =>
                {
                    foreach (var participant in nextRoom.Participants)
                    {
                        participant.ScreenTrackId = string.Empty;
                    }

                    nextRoom.ScreenProducerId = string.Empty;
                    nextRoom.ShareScreenStarted = false;
                    nextRoom.DeferScreenReceived = false;
                });
        }

        private static bool HasParticipantSharingScreen(IEnumerable<MediaSfuParticipant> participants)
        {
            if (participants == null)
            {
                return false;
            }

            foreach (var participant in participants)
            {
                if (participant?.ScreenOn == true)
                {
                    return true;
                }
            }

            return false;
        }

        private void UpdateParticipantTrackStateFromProducer(string participantName, string kind, string producerId, bool enabled)
        {
            var normalizedKind = NormalizeTrackKindLabel(kind);
            if (!string.Equals(normalizedKind, "screen", StringComparison.OrdinalIgnoreCase))
            {
                UpdateParticipantTrackState(participantName, normalizedKind, enabled);
                return;
            }

            if (CurrentRoom?.Participants == null || string.IsNullOrWhiteSpace(participantName))
            {
                return;
            }

            var participants = new List<MediaSfuParticipant>(CurrentRoom.Participants.Count);
            var changed = false;

            foreach (var participant in CurrentRoom.Participants)
            {
                if (!participant.DisplayName.Equals(participantName, StringComparison.OrdinalIgnoreCase))
                {
                    participants.Add(CloneParticipant(participant));
                    continue;
                }

                var updatedParticipant = CloneParticipant(participant);
                if (updatedParticipant.ScreenOn != enabled)
                {
                    updatedParticipant.ScreenOn = enabled;
                    changed = true;
                }

                var nextTrackId = enabled ? producerId ?? string.Empty : string.Empty;
                if (!string.Equals(updatedParticipant.ScreenTrackId, nextTrackId, StringComparison.OrdinalIgnoreCase))
                {
                    updatedParticipant.ScreenTrackId = nextTrackId;
                    changed = true;
                }

                participants.Add(updatedParticipant);
            }

            if (!changed)
            {
                return;
            }

            if (enabled)
            {
                TryMarkScreenProducerHint(producerId);
            }
            else
            {
                ForgetScreenProducerHint(producerId);
            }

            UpdateCurrentRoomState(
                nextRoom =>
                {
                    nextRoom.Participants = participants;
                    if (enabled)
                    {
                        nextRoom.ScreenProducerId = producerId ?? string.Empty;
                        nextRoom.ShareScreenStarted = !string.IsNullOrWhiteSpace(nextRoom.ScreenProducerId);
                        nextRoom.DeferScreenReceived = false;
                    }
                    else if (string.IsNullOrWhiteSpace(producerId) ||
                             string.Equals(nextRoom.ScreenProducerId, producerId, StringComparison.OrdinalIgnoreCase))
                    {
                        nextRoom.ScreenProducerId = string.Empty;
                        nextRoom.ShareScreenStarted = false;
                        nextRoom.DeferScreenReceived = false;
                    }
                });
        }

        private string ResolveRemoteProducerEffectiveKind(string kind, string producerId, string participantName)
        {
            var normalizedKind = NormalizeTrackKindLabel(kind);
            if (!string.Equals(normalizedKind, "video", StringComparison.OrdinalIgnoreCase))
            {
                return normalizedKind;
            }

            if (IsScreenProducerHint(producerId))
            {
                return "screen";
            }

            if (!string.IsNullOrWhiteSpace(producerId) &&
                string.Equals(CurrentRoom?.ScreenProducerId, producerId, StringComparison.OrdinalIgnoreCase))
            {
                return "screen";
            }

            if (TryConsumeScreenProducerParticipantHint(participantName))
            {
                TryMarkScreenProducerHint(producerId);
                return "screen";
            }

            var participant = FindParticipantByName(participantName);
            if (participant != null &&
                !string.IsNullOrWhiteSpace(producerId) &&
                string.Equals(participant.ScreenTrackId, producerId, StringComparison.OrdinalIgnoreCase))
            {
                return "screen";
            }

            return normalizedKind;
        }

        private void ApplyScreenProducerHint(string producerId)
        {
            if (CurrentRoom == null || string.IsNullOrWhiteSpace(producerId))
            {
                return;
            }

            UpdateCurrentRoomState(
                nextRoom =>
                {
                    var previousScreenId = nextRoom.ScreenProducerId;
                    var hasKnownScreenOwner = false;

                    foreach (var participant in nextRoom.Participants)
                    {
                        if (!string.IsNullOrWhiteSpace(previousScreenId) &&
                            !string.Equals(previousScreenId, producerId, StringComparison.OrdinalIgnoreCase) &&
                            string.Equals(participant.ScreenTrackId, previousScreenId, StringComparison.OrdinalIgnoreCase))
                        {
                            participant.ScreenOn = false;
                            participant.ScreenTrackId = string.Empty;
                        }

                        if (string.Equals(participant.ScreenTrackId, producerId, StringComparison.OrdinalIgnoreCase) ||
                            string.Equals(participant.VideoTrackId, producerId, StringComparison.OrdinalIgnoreCase))
                        {
                            participant.ScreenOn = true;
                            participant.ScreenTrackId = producerId;
                            hasKnownScreenOwner = true;
                        }
                    }

                    nextRoom.ScreenProducerId = producerId;
                    if (hasKnownScreenOwner && nextRoom.MembersReceived)
                    {
                        nextRoom.ShareScreenStarted = true;
                        nextRoom.DeferScreenReceived = false;
                    }
                    else
                    {
                        nextRoom.DeferScreenReceived = true;
                    }
                });
        }

        private static string NormalizeTrackKindLabel(string kind)
        {
            switch ((kind ?? string.Empty).Trim().ToLowerInvariant())
            {
                case "screen":
                case "screenshare":
                case "screen-share":
                    return "screen";
                case "audio":
                    return "audio";
                case "video":
                    return "video";
                case "whiteboard":
                    return "whiteboard";
                default:
                    return kind ?? string.Empty;
            }
        }

        private MediaSfuParticipant TryParseParticipant(JsonElement element)
        {
            if (element.ValueKind != JsonValueKind.Object)
            {
                return null;
            }

            var displayName = TryGetString(element, "name", "username");
            if (string.IsNullOrWhiteSpace(displayName))
            {
                return null;
            }

            return new MediaSfuParticipant
            {
                ParticipantId = TryGetString(element, "id") ?? displayName,
                DisplayName = displayName,
                Role = ResolveParticipantRole(element),
                IsLocal = displayName.Equals(_lastLocalUserName, StringComparison.OrdinalIgnoreCase),
                AudioOn = TryGetBool(element, "audioOn") ?? !string.IsNullOrWhiteSpace(TryGetString(element, "audioID")),
                VideoOn = TryGetBool(element, "videoOn") ?? !string.IsNullOrWhiteSpace(TryGetString(element, "videoID")),
                AudioTrackId = TryGetString(element, "audioID") ?? string.Empty,
                VideoTrackId = TryGetString(element, "videoID") ?? string.Empty,
                ScreenOn = TryGetBool(element, "ScreenOn", "screenOn") ?? !string.IsNullOrWhiteSpace(TryGetString(element, "ScreenID", "screenID")),
                ScreenTrackId = TryGetString(element, "ScreenID", "screenID") ?? string.Empty
            };
        }

        private static List<string> ParseStringList(JsonElement arrayElement)
        {
            var values = new List<string>();
            if (arrayElement.ValueKind != JsonValueKind.Array)
            {
                return values;
            }

            foreach (var item in arrayElement.EnumerateArray())
            {
                if (item.ValueKind == JsonValueKind.String)
                {
                    values.Add(item.GetString() ?? string.Empty);
                }
            }

            return values;
        }

        private static List<MediaSfuRoomRequest> ParseRoomRequests(JsonElement arrayElement)
        {
            var requests = new List<MediaSfuRoomRequest>();
            if (arrayElement.ValueKind != JsonValueKind.Array)
            {
                return requests;
            }

            foreach (var item in arrayElement.EnumerateArray())
            {
                var request = TryParseRoomRequest(item);
                if (request != null)
                {
                    requests.Add(request);
                }
            }

            return requests;
        }

        private static List<MediaSfuWaitingRoomParticipant> ParseWaitingRoomParticipants(JsonElement arrayElement)
        {
            var participants = new List<MediaSfuWaitingRoomParticipant>();
            if (arrayElement.ValueKind != JsonValueKind.Array)
            {
                return participants;
            }

            foreach (var item in arrayElement.EnumerateArray())
            {
                var participant = TryParseWaitingRoomParticipant(item);
                if (participant != null)
                {
                    participants.Add(participant);
                }
            }

            return participants;
        }

        private static List<MediaSfuPoll> ParsePolls(JsonElement arrayElement)
        {
            var polls = new List<MediaSfuPoll>();
            if (arrayElement.ValueKind != JsonValueKind.Array)
            {
                return polls;
            }

            foreach (var item in arrayElement.EnumerateArray())
            {
                var poll = TryParsePoll(item);
                if (poll != null)
                {
                    polls.Add(poll);
                }
            }

            return polls;
        }

        private static List<List<MediaSfuBreakoutParticipant>> ParseBreakoutRooms(JsonElement arrayElement)
        {
            var rooms = new List<List<MediaSfuBreakoutParticipant>>();
            if (arrayElement.ValueKind != JsonValueKind.Array)
            {
                return rooms;
            }

            foreach (var roomElement in arrayElement.EnumerateArray())
            {
                if (roomElement.ValueKind != JsonValueKind.Array)
                {
                    continue;
                }

                var roomParticipants = new List<MediaSfuBreakoutParticipant>();
                foreach (var participantElement in roomElement.EnumerateArray())
                {
                    var participant = TryParseBreakoutParticipant(participantElement);
                    if (participant != null)
                    {
                        roomParticipants.Add(participant);
                    }
                }

                rooms.Add(roomParticipants);
            }

            return rooms;
        }

        private static MediaSfuRoomRequest TryParseRoomRequest(JsonElement element)
        {
            if (element.ValueKind != JsonValueKind.Object)
            {
                return null;
            }

            var requestId = TryGetString(element, "id") ??
                            TryGetString(element, "username", "name");
            if (string.IsNullOrWhiteSpace(requestId))
            {
                return null;
            }

            return new MediaSfuRoomRequest
            {
                RequestId = requestId,
                Icon = TryGetString(element, "icon") ?? string.Empty,
                DisplayName = TryGetString(element, "name") ?? string.Empty,
                UserName = TryGetString(element, "username") ?? string.Empty,
                RawPayload = element.GetRawText()
            };
        }

        private static MediaSfuRequestResponse TryParseRequestResponse(JsonElement element)
        {
            if (element.ValueKind != JsonValueKind.Object)
            {
                return null;
            }

            var requestId = TryGetString(element, "id") ??
                            TryGetString(element, "username", "name");
            if (string.IsNullOrWhiteSpace(requestId))
            {
                return null;
            }

            return new MediaSfuRequestResponse
            {
                RequestId = requestId,
                Icon = TryGetString(element, "icon", "type") ?? string.Empty,
                DisplayName = TryGetString(element, "name") ?? string.Empty,
                UserName = TryGetString(element, "username") ?? string.Empty,
                Action = TryGetString(element, "action") ?? string.Empty,
                Type = TryGetString(element, "type", "icon") ?? string.Empty,
                RawPayload = element.GetRawText()
            };
        }

        private static MediaSfuWaitingRoomParticipant TryParseWaitingRoomParticipant(JsonElement element)
        {
            if (element.ValueKind != JsonValueKind.Object)
            {
                return null;
            }

            var participantId = TryGetString(element, "id");
            var displayName = TryGetString(element, "name");
            if (string.IsNullOrWhiteSpace(participantId) || string.IsNullOrWhiteSpace(displayName))
            {
                return null;
            }

            return new MediaSfuWaitingRoomParticipant
            {
                ParticipantId = participantId,
                DisplayName = displayName,
                RawPayload = element.GetRawText()
            };
        }

        private static MediaSfuPoll TryParsePoll(JsonElement element)
        {
            if (element.ValueKind != JsonValueKind.Object)
            {
                return null;
            }

            var question = TryGetString(element, "question") ?? string.Empty;
            var pollId = TryGetString(element, "id") ?? question;
            if (string.IsNullOrWhiteSpace(pollId) || string.IsNullOrWhiteSpace(question))
            {
                return null;
            }

            var poll = new MediaSfuPoll
            {
                PollId = pollId,
                Question = question,
                Type = TryGetString(element, "type") ?? string.Empty,
                Status = TryGetString(element, "status") ?? string.Empty
            };

            if (element.TryGetProperty("options", out var optionsElement) &&
                optionsElement.ValueKind == JsonValueKind.Array)
            {
                poll.Options.AddRange(ParseStringList(optionsElement));
            }

            if (element.TryGetProperty("votes", out var votesElement) &&
                votesElement.ValueKind == JsonValueKind.Array)
            {
                foreach (var voteElement in votesElement.EnumerateArray())
                {
                    if (voteElement.ValueKind == JsonValueKind.Number && voteElement.TryGetInt32(out var numericVote))
                    {
                        poll.Votes.Add(numericVote);
                    }
                    else if (voteElement.ValueKind == JsonValueKind.String &&
                             int.TryParse(voteElement.GetString(), out var stringVote))
                    {
                        poll.Votes.Add(stringVote);
                    }
                }
            }

            if (element.TryGetProperty("voters", out var votersElement) &&
                votersElement.ValueKind == JsonValueKind.Object)
            {
                foreach (var voter in votersElement.EnumerateObject())
                {
                    var rawValue = voter.Value;
                    if (rawValue.ValueKind == JsonValueKind.Number && rawValue.TryGetInt32(out var numericVote))
                    {
                        poll.Voters[voter.Name] = numericVote;
                    }
                    else if (rawValue.ValueKind == JsonValueKind.String &&
                             int.TryParse(rawValue.GetString(), out var stringVote))
                    {
                        poll.Voters[voter.Name] = stringVote;
                    }
                }
            }

            return poll;
        }

        private static MediaSfuBreakoutParticipant TryParseBreakoutParticipant(JsonElement element)
        {
            if (element.ValueKind != JsonValueKind.Object)
            {
                return null;
            }

            var displayName = TryGetString(element, "name") ?? string.Empty;
            if (string.IsNullOrWhiteSpace(displayName))
            {
                return null;
            }

            return new MediaSfuBreakoutParticipant
            {
                DisplayName = displayName,
                BreakRoom = TryGetInt(element, "breakRoom")
            };
        }

        private static List<MediaSfuCoHostResponsibility> ParseCoHostResponsibilities(JsonElement arrayElement)
        {
            var responsibilities = new List<MediaSfuCoHostResponsibility>();
            if (arrayElement.ValueKind != JsonValueKind.Array)
            {
                return responsibilities;
            }

            foreach (var item in arrayElement.EnumerateArray())
            {
                var responsibility = TryParseCoHostResponsibility(item);
                if (responsibility != null)
                {
                    responsibilities.Add(responsibility);
                }
            }

            return responsibilities;
        }

        private static MediaSfuCoHostResponsibility TryParseCoHostResponsibility(JsonElement element)
        {
            if (element.ValueKind != JsonValueKind.Object)
            {
                return null;
            }

            var name = TryGetString(element, "name");
            if (string.IsNullOrWhiteSpace(name))
            {
                return null;
            }

            return new MediaSfuCoHostResponsibility
            {
                Name = name,
                Value = TryGetBool(element, "value") ?? false,
                Dedicated = TryGetBool(element, "dedicated") ?? false
            };
        }

        private static MediaSfuRecordingParameters TryParseRecordingParameters(JsonElement element)
        {
            if (element.ValueKind != JsonValueKind.Object)
            {
                return new MediaSfuRecordingParameters();
            }

            return new MediaSfuRecordingParameters
            {
                RecordingAudioPausesLimit = TryGetInt(element, "recordingAudioPausesLimit") ?? 0,
                RecordingAudioSupport = TryGetBool(element, "recordingAudioSupport") ?? false,
                RecordingAudioPeopleLimit = TryGetInt(element, "recordingAudioPeopleLimit") ?? 0,
                RecordingAudioParticipantsTimeLimit = TryGetInt(element, "recordingAudioParticipantsTimeLimit") ?? 0,
                RecordingVideoPausesLimit = TryGetInt(element, "recordingVideoPausesLimit") ?? 0,
                RecordingVideoSupport = TryGetBool(element, "recordingVideoSupport") ?? false,
                RecordingVideoPeopleLimit = TryGetInt(element, "recordingVideoPeopleLimit") ?? 0,
                RecordingVideoParticipantsTimeLimit = TryGetInt(element, "recordingVideoParticipantsTimeLimit") ?? 0,
                RecordingAllParticipantsSupport = TryGetBool(element, "recordingAllParticipantsSupport") ?? false,
                RecordingVideoParticipantsSupport = TryGetBool(element, "recordingVideoParticipantsSupport") ?? false,
                RecordingAllParticipantsFullRoomSupport = TryGetBool(element, "recordingAllParticipantsFullRoomSupport") ?? false,
                RecordingVideoParticipantsFullRoomSupport = TryGetBool(element, "recordingVideoParticipantsFullRoomSupport") ?? false,
                RecordingPreferredOrientation = TryGetString(element, "recordingPreferredOrientation") ?? string.Empty,
                RecordingSupportForOtherOrientation = TryGetBool(element, "recordingSupportForOtherOrientation") ?? false,
                RecordingMultiFormatsSupport = TryGetBool(element, "recordingMultiFormatsSupport") ?? false,
                RecordingHlsSupport = TryGetBool(element, "recordingHlsSupport") ?? false,
                RecordingAudioPausesCount = TryGetInt(element, "recordingAudioPausesCount"),
                RecordingVideoPausesCount = TryGetInt(element, "recordingVideoPausesCount")
            };
        }

        private static MediaSfuUserRecordingParams TryParseUserRecordingParams(JsonElement element)
        {
            if (element.ValueKind != JsonValueKind.Object)
            {
                return null;
            }

            var mainSpecs = new MediaSfuRecordingMainSpecs();
            if (element.TryGetProperty("mainSpecs", out var mainSpecsElement) &&
                mainSpecsElement.ValueKind == JsonValueKind.Object)
            {
                mainSpecs.MediaOptions = TryGetString(mainSpecsElement, "mediaOptions") ?? "video";
                mainSpecs.AudioOptions = TryGetString(mainSpecsElement, "audioOptions") ?? "all";
                mainSpecs.VideoOptions = TryGetString(mainSpecsElement, "videoOptions") ?? "all";
                mainSpecs.VideoType = TryGetString(mainSpecsElement, "videoType") ?? "fullDisplay";
                mainSpecs.VideoOptimized = TryGetBool(mainSpecsElement, "videoOptimized") ?? false;
                mainSpecs.RecordingDisplayType = TryGetString(mainSpecsElement, "recordingDisplayType") ?? "media";
                mainSpecs.AddHls = TryGetBool(mainSpecsElement, "addHls") ?? false;
            }

            var displaySpecs = new MediaSfuRecordingDisplaySpecs();
            if (element.TryGetProperty("dispSpecs", out var displaySpecsElement) &&
                displaySpecsElement.ValueKind == JsonValueKind.Object)
            {
                displaySpecs.NameTags = TryGetBool(displaySpecsElement, "nameTags") ?? true;
                displaySpecs.BackgroundColor = TryGetString(displaySpecsElement, "backgroundColor") ?? "#000000";
                displaySpecs.NameTagsColor = TryGetString(displaySpecsElement, "nameTagsColor") ?? "#ffffff";
                displaySpecs.OrientationVideo = TryGetString(displaySpecsElement, "orientationVideo") ?? "landscape";
            }

            var textSpecs = new MediaSfuRecordingTextSpecs();
            if (element.TryGetProperty("textSpecs", out var textSpecsElement) &&
                textSpecsElement.ValueKind == JsonValueKind.Object)
            {
                textSpecs.AddText = TryGetBool(textSpecsElement, "addText") ?? false;
                textSpecs.CustomText = TryGetString(textSpecsElement, "customText") ?? string.Empty;
                textSpecs.CustomTextPosition = TryGetString(textSpecsElement, "customTextPosition") ?? string.Empty;
                textSpecs.CustomTextColor = TryGetString(textSpecsElement, "customTextColor") ?? string.Empty;
            }

            return new MediaSfuUserRecordingParams
            {
                MainSpecs = mainSpecs,
                DisplaySpecs = displaySpecs,
                TextSpecs = textSpecs,
                RawPayload = element.GetRawText()
            };
        }

        private static void ApplyMediaSettings(MediaSfuRoom room, IReadOnlyList<string> settings)
        {
            if (room == null || settings == null || settings.Count == 0)
            {
                return;
            }

            if (settings.Count > 0)
            {
                room.AudioSetting = settings[0] ?? string.Empty;
            }

            if (settings.Count > 1)
            {
                room.VideoSetting = settings[1] ?? string.Empty;
            }

            if (settings.Count > 2)
            {
                room.ScreenshareSetting = settings[2] ?? string.Empty;
            }

            if (settings.Count > 3)
            {
                room.ChatSetting = settings[3] ?? string.Empty;
            }
        }

        private static void ApplyRecordingParameters(MediaSfuRoom room, MediaSfuRecordingParameters recordingParameters)
        {
            if (room == null)
            {
                return;
            }

            room.RecordingParameters = CloneRecordingParameters(recordingParameters) ?? new MediaSfuRecordingParameters();
            room.Recording ??= new MediaSfuRecordingState();
            room.Recording.CanPauseResume = room.RecordingParameters.RecordingAudioPausesLimit > 0 ||
                                            room.RecordingParameters.RecordingVideoPausesLimit > 0;
            room.Recording.CanLaunchRecord = room.RecordingParameters.RecordingAudioSupport ||
                                             room.RecordingParameters.RecordingVideoSupport;
        }

        private static int ComputePendingModerationCount(MediaSfuRoom room)
        {
            if (room == null)
            {
                return 0;
            }

            return (room.PendingRequests?.Count ?? 0) + (room.WaitingRoomParticipants?.Count ?? 0);
        }

        private static void ApplyRequestResponse(MediaSfuRoom room, MediaSfuRequestResponse requestResponse)
        {
            if (room == null || requestResponse == null)
            {
                return;
            }

            room.LastRequestResponse = CloneRequestResponse(requestResponse);
            room.LocalRequests ??= new MediaSfuLocalRequestState();

            var action = requestResponse.Action ?? string.Empty;
            var isAccepted = string.Equals(action, "accepted", StringComparison.OrdinalIgnoreCase);
            long? retryAt = null;
            if (string.Equals(action, "rejected", StringComparison.OrdinalIgnoreCase))
            {
                retryAt = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds() + (DefaultRequestCooldownSeconds * 1000L);
            }

            switch (NormalizeRequestType(requestResponse.Type))
            {
                case "audio":
                    room.LocalRequests.AudioActionGranted = isAccepted;
                    room.LocalRequests.AudioRequestState = string.IsNullOrWhiteSpace(action) ? "none" : action;
                    room.LocalRequests.AudioRequestRetryAtEpochMs = retryAt;
                    break;
                case "video":
                    room.LocalRequests.VideoActionGranted = isAccepted;
                    room.LocalRequests.VideoRequestState = string.IsNullOrWhiteSpace(action) ? "none" : action;
                    room.LocalRequests.VideoRequestRetryAtEpochMs = retryAt;
                    break;
                case "screenshare":
                    room.LocalRequests.ScreenshareActionGranted = isAccepted;
                    room.LocalRequests.ScreenshareRequestState = string.IsNullOrWhiteSpace(action) ? "none" : action;
                    room.LocalRequests.ScreenshareRequestRetryAtEpochMs = retryAt;
                    break;
                case "chat":
                    room.LocalRequests.ChatActionGranted = isAccepted;
                    room.LocalRequests.ChatRequestState = string.IsNullOrWhiteSpace(action) ? "none" : action;
                    room.LocalRequests.ChatRequestRetryAtEpochMs = retryAt;
                    break;
            }
        }

        private static void ApplyHostRestriction(MediaSfuRoom room, string controlType)
        {
            if (room == null || string.IsNullOrWhiteSpace(controlType))
            {
                return;
            }

            switch (controlType)
            {
                case "audio":
                    room.HostRestrictedAudio = true;
                    break;
                case "video":
                    room.HostRestrictedVideo = true;
                    break;
                case "screenshare":
                    room.HostRestrictedScreenshare = true;
                    break;
                case "chat":
                    room.HostRestrictedChat = true;
                    break;
                case "all":
                    room.HostRestrictedAudio = true;
                    room.HostRestrictedVideo = true;
                    room.HostRestrictedScreenshare = true;
                    room.HostRestrictedChat = true;
                    break;
            }
        }

        private static void ApplyHostRestrictionToLocalParticipant(
            IList<MediaSfuParticipant> participants,
            string controlType)
        {
            if (participants == null || string.IsNullOrWhiteSpace(controlType))
            {
                return;
            }

            foreach (var participant in participants)
            {
                if (!participant.IsLocal)
                {
                    continue;
                }

                switch (controlType)
                {
                    case "audio":
                        participant.AudioOn = false;
                        break;
                    case "video":
                        participant.VideoOn = false;
                        break;
                    case "screenshare":
                        participant.ScreenOn = false;
                        break;
                    case "all":
                        participant.AudioOn = false;
                        participant.VideoOn = false;
                        participant.ScreenOn = false;
                        break;
                }

                break;
            }
        }

        private static string NormalizeMediaControlType(string rawType)
        {
            if (string.IsNullOrWhiteSpace(rawType))
            {
                return string.Empty;
            }

            return rawType.Trim().ToLowerInvariant() switch
            {
                "screen" => "screenshare",
                "screen-share" => "screenshare",
                _ => rawType.Trim().ToLowerInvariant()
            };
        }

        private static string NormalizeRequestType(string rawType)
        {
            if (string.IsNullOrWhiteSpace(rawType))
            {
                return string.Empty;
            }

            return rawType.Trim().ToLowerInvariant() switch
            {
                "fa-microphone" => "audio",
                "microphone" => "audio",
                "audio" => "audio",
                "fa-video" => "video",
                "video" => "video",
                "fa-desktop" => "screenshare",
                "desktop" => "screenshare",
                "screen" => "screenshare",
                "screen-share" => "screenshare",
                "screenshare" => "screenshare",
                "fa-comments" => "chat",
                "comments" => "chat",
                "chat" => "chat",
                _ => rawType.Trim().ToLowerInvariant()
            };
        }

        private bool ShouldOpenPollModal(MediaSfuPoll poll, string status)
        {
            if (poll == null ||
                !string.Equals(status, "started", StringComparison.OrdinalIgnoreCase) ||
                string.Equals(_lastLocalIsLevel, "2", StringComparison.OrdinalIgnoreCase))
            {
                return false;
            }

            if (string.IsNullOrWhiteSpace(_lastLocalUserName))
            {
                return true;
            }

            return poll.Voters == null || !poll.Voters.ContainsKey(_lastLocalUserName);
        }

        private MediaSfuParticipantRole ResolveParticipantRole(JsonElement element)
        {
            if (TryGetBool(element, "isHost") == true)
            {
                return MediaSfuParticipantRole.Host;
            }

            if (TryGetBool(element, "isAdmin") == true)
            {
                return MediaSfuParticipantRole.CoHost;
            }

            var isLevel = TryGetString(element, "islevel") ?? string.Empty;
            return ResolveParticipantRole(isLevel);
        }

        private static MediaSfuChatMessage TryParseChatMessage(JsonElement messageElement)
        {
            if (messageElement.ValueKind != JsonValueKind.Object)
            {
                return null;
            }

            var message = TryGetString(messageElement, "message") ?? string.Empty;
            if (string.IsNullOrWhiteSpace(message))
            {
                return null;
            }

            var chatMessage = new MediaSfuChatMessage
            {
                Sender = TryGetString(messageElement, "sender") ?? string.Empty,
                Message = message,
                Timestamp = TryGetString(messageElement, "timestamp") ?? string.Empty,
                Group = TryGetBool(messageElement, "group") ?? false,
                RawPayload = messageElement.GetRawText()
            };

            if (messageElement.TryGetProperty("receivers", out var receiversElement) &&
                receiversElement.ValueKind == JsonValueKind.Array)
            {
                foreach (var receiver in receiversElement.EnumerateArray())
                {
                    if (receiver.ValueKind == JsonValueKind.String)
                    {
                        var value = receiver.GetString();
                        if (!string.IsNullOrWhiteSpace(value))
                        {
                            chatMessage.Receivers.Add(value);
                        }
                    }
                }
            }

            return chatMessage;
        }

        private static MediaSfuTrackKind ResolveTrackKind(string kind)
        {
            return kind?.ToLowerInvariant() switch
            {
                "audio" => MediaSfuTrackKind.Audio,
                "screen" => MediaSfuTrackKind.Screen,
                "screenshare" => MediaSfuTrackKind.Screen,
                "screen-share" => MediaSfuTrackKind.Screen,
                "whiteboard" => MediaSfuTrackKind.Whiteboard,
                _ => MediaSfuTrackKind.Video
            };
        }

        private static string NormalizeSocketNamespace(string socketNamespace)
        {
            return string.IsNullOrWhiteSpace(socketNamespace) ? "/" : socketNamespace;
        }

        private static string SanitizeParticipantKey(string value)
        {
            var sanitized = ParticipantKeyPattern.Replace(value.Trim().ToLowerInvariant(), "-").Trim('-');
            return string.IsNullOrWhiteSpace(sanitized) ? "participant" : sanitized;
        }

        private static string GetParticipantKey(MediaSfuParticipant participant)
        {
            return NormalizeParticipantKey(
                !string.IsNullOrWhiteSpace(participant?.DisplayName)
                    ? participant.DisplayName
                    : participant?.ParticipantId);
        }

        private static string GetRoomRequestKey(MediaSfuRoomRequest request)
        {
            return NormalizeParticipantKey(
                !string.IsNullOrWhiteSpace(request?.RequestId)
                    ? request.RequestId
                    : !string.IsNullOrWhiteSpace(request?.UserName)
                        ? request.UserName
                        : request?.DisplayName);
        }

        private static string GetWaitingRoomParticipantKey(MediaSfuWaitingRoomParticipant participant)
        {
            return NormalizeParticipantKey(
                !string.IsNullOrWhiteSpace(participant?.ParticipantId)
                    ? participant.ParticipantId
                    : participant?.DisplayName);
        }

        private static string GetPollKey(MediaSfuPoll poll)
        {
            return NormalizeParticipantKey(
                !string.IsNullOrWhiteSpace(poll?.PollId)
                    ? poll.PollId
                    : poll?.Question);
        }

        private static int FindPendingRequestIndex(IReadOnlyList<MediaSfuRoomRequest> requests, string requestId)
        {
            if (requests == null || string.IsNullOrWhiteSpace(requestId))
            {
                return -1;
            }

            var normalizedRequestId = NormalizeParticipantKey(requestId);
            for (var index = 0; index < requests.Count; index += 1)
            {
                if (NormalizeParticipantKey(requests[index]?.RequestId) == normalizedRequestId)
                {
                    return index;
                }
            }

            return -1;
        }

        private static int FindWaitingParticipantIndex(
            IReadOnlyList<MediaSfuWaitingRoomParticipant> participants,
            string participantId)
        {
            if (participants == null || string.IsNullOrWhiteSpace(participantId))
            {
                return -1;
            }

            var normalizedParticipantId = NormalizeParticipantKey(participantId);
            for (var index = 0; index < participants.Count; index += 1)
            {
                if (NormalizeParticipantKey(participants[index]?.ParticipantId) == normalizedParticipantId)
                {
                    return index;
                }
            }

            return -1;
        }

        private static int FindPollIndex(IReadOnlyList<MediaSfuPoll> polls, string pollId)
        {
            if (polls == null || string.IsNullOrWhiteSpace(pollId))
            {
                return -1;
            }

            var normalizedPollId = NormalizeParticipantKey(pollId);
            for (var index = 0; index < polls.Count; index += 1)
            {
                if (GetPollKey(polls[index]) == normalizedPollId)
                {
                    return index;
                }
            }

            return -1;
        }

        private static void RemoveParticipant(
            IList<MediaSfuParticipant> participants,
            string participantId,
            string participantName)
        {
            if (participants == null)
            {
                return;
            }

            var normalizedParticipantId = NormalizeParticipantKey(participantId);
            var normalizedParticipantName = NormalizeParticipantKey(participantName);
            for (var index = participants.Count - 1; index >= 0; index -= 1)
            {
                var participant = participants[index];
                if ((!string.IsNullOrWhiteSpace(normalizedParticipantId) &&
                     NormalizeParticipantKey(participant?.ParticipantId) == normalizedParticipantId) ||
                    (!string.IsNullOrWhiteSpace(normalizedParticipantName) &&
                     NormalizeParticipantKey(participant?.DisplayName) == normalizedParticipantName))
                {
                    participants.RemoveAt(index);
                }
            }
        }

        private static void RemovePendingRequest(IList<MediaSfuRoomRequest> requests, string requestId)
        {
            if (requests == null || string.IsNullOrWhiteSpace(requestId))
            {
                return;
            }

            for (var index = requests.Count - 1; index >= 0; index -= 1)
            {
                if (NormalizeParticipantKey(requests[index]?.RequestId) == NormalizeParticipantKey(requestId))
                {
                    requests.RemoveAt(index);
                }
            }
        }

        private static void RemoveWaitingParticipant(
            IList<MediaSfuWaitingRoomParticipant> participants,
            string participantId)
        {
            if (participants == null || string.IsNullOrWhiteSpace(participantId))
            {
                return;
            }

            for (var index = participants.Count - 1; index >= 0; index -= 1)
            {
                if (NormalizeParticipantKey(participants[index]?.ParticipantId) == NormalizeParticipantKey(participantId))
                {
                    participants.RemoveAt(index);
                }
            }
        }

        private static string NormalizeParticipantKey(string value)
        {
            return string.IsNullOrWhiteSpace(value) ? string.Empty : value.Trim().ToLowerInvariant();
        }

        private static string FormatElapsedTime(int elapsedSeconds)
        {
            var hours = elapsedSeconds / 3600;
            var minutes = (elapsedSeconds % 3600) / 60;
            var seconds = elapsedSeconds % 60;
            return $"{hours:D2}:{minutes:D2}:{seconds:D2}";
        }

        private static MediaSfuPoll ClonePoll(MediaSfuPoll poll)
        {
            if (poll == null)
            {
                return null;
            }

            var clonedPoll = new MediaSfuPoll
            {
                PollId = poll.PollId,
                Question = poll.Question,
                Type = poll.Type,
                Status = poll.Status
            };

            if (poll.Options != null)
            {
                foreach (var option in poll.Options)
                {
                    clonedPoll.Options.Add(option ?? string.Empty);
                }
            }

            if (poll.Votes != null)
            {
                foreach (var vote in poll.Votes)
                {
                    clonedPoll.Votes.Add(vote);
                }
            }

            if (poll.Voters != null)
            {
                foreach (var voter in poll.Voters)
                {
                    clonedPoll.Voters[voter.Key] = voter.Value;
                }
            }

            return clonedPoll;
        }

        private static MediaSfuConsumingDomainsState CloneConsumingDomainsState(MediaSfuConsumingDomainsState consumingDomains)
        {
            if (consumingDomains == null)
            {
                return null;
            }

            var clonedState = new MediaSfuConsumingDomainsState
            {
                HasAltDomains = consumingDomains.HasAltDomains,
                AltDomainsJson = consumingDomains.AltDomainsJson
            };

            if (consumingDomains.Domains != null)
            {
                foreach (var domain in consumingDomains.Domains)
                {
                    clonedState.Domains.Add(domain ?? string.Empty);
                }
            }

            return clonedState;
        }

        private static MediaSfuWhiteboardState CloneWhiteboardState(MediaSfuWhiteboardState whiteboard)
        {
            if (whiteboard == null)
            {
                return null;
            }

            return new MediaSfuWhiteboardState
            {
                LastAction = whiteboard.LastAction,
                Started = whiteboard.Started,
                Ended = whiteboard.Ended,
                CanStart = whiteboard.CanStart,
                UseImageBackground = whiteboard.UseImageBackground,
                Users = CloneWhiteboardUserList(whiteboard.Users),
                Shapes = CloneWhiteboardShapeList(whiteboard.Shapes),
                RedoStack = CloneWhiteboardShapeStack(whiteboard.RedoStack),
                UndoStack = CloneWhiteboardShapeStack(whiteboard.UndoStack)
            };
        }

        private static List<MediaSfuWhiteboardUser> CloneWhiteboardUserList(
            IReadOnlyList<MediaSfuWhiteboardUser> users)
        {
            var clonedUsers = new List<MediaSfuWhiteboardUser>();
            if (users == null)
            {
                return clonedUsers;
            }

            foreach (var user in users)
            {
                clonedUsers.Add(CloneWhiteboardUser(user));
            }

            return clonedUsers;
        }

        private static MediaSfuWhiteboardUser CloneWhiteboardUser(MediaSfuWhiteboardUser user)
        {
            if (user == null)
            {
                return null;
            }

            return new MediaSfuWhiteboardUser
            {
                Name = user.Name,
                UseBoard = user.UseBoard
            };
        }

        private static List<List<MediaSfuWhiteboardShape>> CloneWhiteboardShapeStack(
            IReadOnlyList<List<MediaSfuWhiteboardShape>> stack)
        {
            var clonedStack = new List<List<MediaSfuWhiteboardShape>>();
            if (stack == null)
            {
                return clonedStack;
            }

            foreach (var snapshot in stack)
            {
                clonedStack.Add(CloneWhiteboardShapeList(snapshot));
            }

            return clonedStack;
        }

        private static List<MediaSfuWhiteboardShape> CloneWhiteboardShapeList(
            IReadOnlyList<MediaSfuWhiteboardShape> shapes)
        {
            var clonedShapes = new List<MediaSfuWhiteboardShape>();
            if (shapes == null)
            {
                return clonedShapes;
            }

            foreach (var shape in shapes)
            {
                clonedShapes.Add(CloneWhiteboardShape(shape));
            }

            return clonedShapes;
        }

        private static MediaSfuWhiteboardShape CloneWhiteboardShape(MediaSfuWhiteboardShape shape)
        {
            if (shape == null)
            {
                return null;
            }

            var clonedShape = new MediaSfuWhiteboardShape
            {
                Type = shape.Type,
                X = shape.X,
                Y = shape.Y,
                X1 = shape.X1,
                Y1 = shape.Y1,
                X2 = shape.X2,
                Y2 = shape.Y2,
                Color = shape.Color,
                Thickness = shape.Thickness,
                LineType = shape.LineType,
                Text = shape.Text,
                FontFamily = shape.FontFamily,
                FontSize = shape.FontSize,
                ImageSrc = shape.ImageSrc,
                Signature = shape.Signature
            };

            if (shape.Points != null)
            {
                foreach (var point in shape.Points)
                {
                    clonedShape.Points.Add(
                        point == null
                            ? null
                            : new MediaSfuWhiteboardPoint
                            {
                                X = point.X,
                                Y = point.Y
                            });
                }
            }

            return clonedShape;
        }

        private static MediaSfuBreakoutParticipant CloneBreakoutParticipant(MediaSfuBreakoutParticipant participant)
        {
            if (participant == null)
            {
                return null;
            }

            return new MediaSfuBreakoutParticipant
            {
                DisplayName = participant.DisplayName,
                BreakRoom = participant.BreakRoom
            };
        }

        private static MediaSfuBreakoutState CloneBreakoutState(MediaSfuBreakoutState breakout)
        {
            if (breakout == null)
            {
                return null;
            }

            var clonedBreakout = new MediaSfuBreakoutState
            {
                Started = breakout.Started,
                Ended = breakout.Ended,
                HostNewRoom = breakout.HostNewRoom,
                Status = breakout.Status
            };

            if (breakout.Rooms != null)
            {
                foreach (var room in breakout.Rooms)
                {
                    var clonedRoom = new List<MediaSfuBreakoutParticipant>();
                    if (room != null)
                    {
                        foreach (var participant in room)
                        {
                            clonedRoom.Add(CloneBreakoutParticipant(participant));
                        }
                    }

                    clonedBreakout.Rooms.Add(clonedRoom);
                }
            }

            return clonedBreakout;
        }

        private static MediaSfuParticipant CloneParticipant(MediaSfuParticipant participant)
        {
            if (participant == null)
            {
                return null;
            }

            return new MediaSfuParticipant
            {
                ParticipantId = participant.ParticipantId,
                DisplayName = participant.DisplayName,
                Role = participant.Role,
                IsLocal = participant.IsLocal,
                AudioOn = participant.AudioOn,
                VideoOn = participant.VideoOn,
                ScreenOn = participant.ScreenOn,
                AudioTrackId = participant.AudioTrackId,
                VideoTrackId = participant.VideoTrackId,
                ScreenTrackId = participant.ScreenTrackId
            };
        }

        private static MediaSfuRoomRequest CloneRoomRequest(MediaSfuRoomRequest request)
        {
            if (request == null)
            {
                return null;
            }

            return new MediaSfuRoomRequest
            {
                RequestId = request.RequestId,
                Icon = request.Icon,
                DisplayName = request.DisplayName,
                UserName = request.UserName,
                RawPayload = request.RawPayload
            };
        }

        private static MediaSfuWaitingRoomParticipant CloneWaitingRoomParticipant(MediaSfuWaitingRoomParticipant participant)
        {
            if (participant == null)
            {
                return null;
            }

            return new MediaSfuWaitingRoomParticipant
            {
                ParticipantId = participant.ParticipantId,
                DisplayName = participant.DisplayName,
                RawPayload = participant.RawPayload
            };
        }

        private static MediaSfuCoHostResponsibility CloneCoHostResponsibility(MediaSfuCoHostResponsibility responsibility)
        {
            if (responsibility == null)
            {
                return null;
            }

            return new MediaSfuCoHostResponsibility
            {
                Name = responsibility.Name,
                Value = responsibility.Value,
                Dedicated = responsibility.Dedicated
            };
        }

        private static MediaSfuRequestResponse CloneRequestResponse(MediaSfuRequestResponse requestResponse)
        {
            if (requestResponse == null)
            {
                return null;
            }

            return new MediaSfuRequestResponse
            {
                RequestId = requestResponse.RequestId,
                Icon = requestResponse.Icon,
                DisplayName = requestResponse.DisplayName,
                UserName = requestResponse.UserName,
                Action = requestResponse.Action,
                Type = requestResponse.Type,
                RawPayload = requestResponse.RawPayload
            };
        }

        private static MediaSfuLocalRequestState CloneLocalRequestState(MediaSfuLocalRequestState requestState)
        {
            if (requestState == null)
            {
                return null;
            }

            return new MediaSfuLocalRequestState
            {
                AudioRequestState = requestState.AudioRequestState,
                VideoRequestState = requestState.VideoRequestState,
                ScreenshareRequestState = requestState.ScreenshareRequestState,
                ChatRequestState = requestState.ChatRequestState,
                AudioRequestRetryAtEpochMs = requestState.AudioRequestRetryAtEpochMs,
                VideoRequestRetryAtEpochMs = requestState.VideoRequestRetryAtEpochMs,
                ScreenshareRequestRetryAtEpochMs = requestState.ScreenshareRequestRetryAtEpochMs,
                ChatRequestRetryAtEpochMs = requestState.ChatRequestRetryAtEpochMs,
                AudioActionGranted = requestState.AudioActionGranted,
                VideoActionGranted = requestState.VideoActionGranted,
                ScreenshareActionGranted = requestState.ScreenshareActionGranted,
                ChatActionGranted = requestState.ChatActionGranted
            };
        }

        private static MediaSfuRecordingMainSpecs CloneRecordingMainSpecs(MediaSfuRecordingMainSpecs value)
        {
            if (value == null)
            {
                return null;
            }

            return new MediaSfuRecordingMainSpecs
            {
                MediaOptions = value.MediaOptions,
                AudioOptions = value.AudioOptions,
                VideoOptions = value.VideoOptions,
                VideoType = value.VideoType,
                VideoOptimized = value.VideoOptimized,
                RecordingDisplayType = value.RecordingDisplayType,
                AddHls = value.AddHls
            };
        }

        private static MediaSfuRecordingDisplaySpecs CloneRecordingDisplaySpecs(MediaSfuRecordingDisplaySpecs value)
        {
            if (value == null)
            {
                return null;
            }

            return new MediaSfuRecordingDisplaySpecs
            {
                NameTags = value.NameTags,
                BackgroundColor = value.BackgroundColor,
                NameTagsColor = value.NameTagsColor,
                OrientationVideo = value.OrientationVideo
            };
        }

        private static MediaSfuRecordingTextSpecs CloneRecordingTextSpecs(MediaSfuRecordingTextSpecs value)
        {
            if (value == null)
            {
                return null;
            }

            return new MediaSfuRecordingTextSpecs
            {
                AddText = value.AddText,
                CustomText = value.CustomText,
                CustomTextPosition = value.CustomTextPosition,
                CustomTextColor = value.CustomTextColor
            };
        }

        private static MediaSfuUserRecordingParams CloneUserRecordingParams(MediaSfuUserRecordingParams value)
        {
            if (value == null)
            {
                return null;
            }

            return new MediaSfuUserRecordingParams
            {
                MainSpecs = CloneRecordingMainSpecs(value.MainSpecs) ?? new MediaSfuRecordingMainSpecs(),
                DisplaySpecs = CloneRecordingDisplaySpecs(value.DisplaySpecs) ?? new MediaSfuRecordingDisplaySpecs(),
                TextSpecs = CloneRecordingTextSpecs(value.TextSpecs) ?? new MediaSfuRecordingTextSpecs(),
                RawPayload = value.RawPayload
            };
        }

        private static MediaSfuRecordingState CloneRecordingState(MediaSfuRecordingState value)
        {
            if (value == null)
            {
                return null;
            }

            return new MediaSfuRecordingState
            {
                State = value.State,
                LastNoticeState = value.LastNoticeState,
                LastStopReason = value.LastStopReason,
                ProgressTime = value.ProgressTime,
                RecordElapsedTimeSeconds = value.RecordElapsedTimeSeconds,
                RecordStartTimeEpochMs = value.RecordStartTimeEpochMs,
                PauseCount = value.PauseCount,
                TimeLeftSeconds = value.TimeLeftSeconds,
                RecordStarted = value.RecordStarted,
                RecordPaused = value.RecordPaused,
                RecordStopped = value.RecordStopped,
                CanLaunchRecord = value.CanLaunchRecord,
                CanPauseResume = value.CanPauseResume,
                ShowRecordButtons = value.ShowRecordButtons,
                IsTimerRunning = value.IsTimerRunning,
                UserRecordingParams = CloneUserRecordingParams(value.UserRecordingParams) ?? new MediaSfuUserRecordingParams()
            };
        }

        private static MediaSfuRecordingParameters CloneRecordingParameters(MediaSfuRecordingParameters value)
        {
            if (value == null)
            {
                return null;
            }

            return new MediaSfuRecordingParameters
            {
                RecordingAudioPausesLimit = value.RecordingAudioPausesLimit,
                RecordingAudioSupport = value.RecordingAudioSupport,
                RecordingAudioPeopleLimit = value.RecordingAudioPeopleLimit,
                RecordingAudioParticipantsTimeLimit = value.RecordingAudioParticipantsTimeLimit,
                RecordingVideoPausesLimit = value.RecordingVideoPausesLimit,
                RecordingVideoSupport = value.RecordingVideoSupport,
                RecordingVideoPeopleLimit = value.RecordingVideoPeopleLimit,
                RecordingVideoParticipantsTimeLimit = value.RecordingVideoParticipantsTimeLimit,
                RecordingAllParticipantsSupport = value.RecordingAllParticipantsSupport,
                RecordingVideoParticipantsSupport = value.RecordingVideoParticipantsSupport,
                RecordingAllParticipantsFullRoomSupport = value.RecordingAllParticipantsFullRoomSupport,
                RecordingVideoParticipantsFullRoomSupport = value.RecordingVideoParticipantsFullRoomSupport,
                RecordingPreferredOrientation = value.RecordingPreferredOrientation,
                RecordingSupportForOtherOrientation = value.RecordingSupportForOtherOrientation,
                RecordingMultiFormatsSupport = value.RecordingMultiFormatsSupport,
                RecordingHlsSupport = value.RecordingHlsSupport,
                RecordingAudioPausesCount = value.RecordingAudioPausesCount,
                RecordingVideoPausesCount = value.RecordingVideoPausesCount
            };
        }

        private static MediaSfuRoom CloneRoom(MediaSfuRoom room, IReadOnlyList<MediaSfuParticipant> participants = null)
        {
            if (room == null)
            {
                return null;
            }

            var clonedRoom = new MediaSfuRoom
            {
                RoomName = room.RoomName,
                PublicUrl = room.PublicUrl,
                Link = room.Link,
                Secret = room.Secret,
                SecureCode = room.SecureCode,
                ApiUserName = room.ApiUserName,
                CoHost = room.CoHost,
                PendingModerationCount = room.PendingModerationCount,
                LastWaitingParticipantName = room.LastWaitingParticipantName,
                AudioSetting = room.AudioSetting,
                VideoSetting = room.VideoSetting,
                ScreenshareSetting = room.ScreenshareSetting,
                ChatSetting = room.ChatSetting,
                HostRestrictedAudio = room.HostRestrictedAudio,
                HostRestrictedVideo = room.HostRestrictedVideo,
                HostRestrictedScreenshare = room.HostRestrictedScreenshare,
                HostRestrictedChat = room.HostRestrictedChat,
                ConfirmHereRequested = room.ConfirmHereRequested,
                MeetingTimeRemainingMs = room.MeetingTimeRemainingMs,
                AdminRestrictSetting = room.AdminRestrictSetting,
                RecordingParameters = CloneRecordingParameters(room.RecordingParameters) ?? new MediaSfuRecordingParameters(),
                Recording = CloneRecordingState(room.Recording) ?? new MediaSfuRecordingState(),
                LocalRequests = CloneLocalRequestState(room.LocalRequests) ?? new MediaSfuLocalRequestState(),
                LastRequestResponse = CloneRequestResponse(room.LastRequestResponse),
                ActivePoll = ClonePoll(room.ActivePoll),
                PollModalVisible = room.PollModalVisible,
                LastPollStatus = room.LastPollStatus,
                Breakout = CloneBreakoutState(room.Breakout) ?? new MediaSfuBreakoutState(),
                MembersReceived = room.MembersReceived,
                ScreenProducerId = room.ScreenProducerId,
                ShareScreenStarted = room.ShareScreenStarted,
                DeferScreenReceived = room.DeferScreenReceived,
                ConsumingDomains = CloneConsumingDomainsState(room.ConsumingDomains) ?? new MediaSfuConsumingDomainsState(),
                Whiteboard = CloneWhiteboardState(room.Whiteboard) ?? new MediaSfuWhiteboardState()
            };

            if (room.CoHostResponsibilities != null)
            {
                foreach (var responsibility in room.CoHostResponsibilities)
                {
                    clonedRoom.CoHostResponsibilities.Add(CloneCoHostResponsibility(responsibility));
                }
            }

            if (room.PendingRequests != null)
            {
                foreach (var request in room.PendingRequests)
                {
                    clonedRoom.PendingRequests.Add(CloneRoomRequest(request));
                }
            }

            if (room.WaitingRoomParticipants != null)
            {
                foreach (var participant in room.WaitingRoomParticipants)
                {
                    clonedRoom.WaitingRoomParticipants.Add(CloneWaitingRoomParticipant(participant));
                }
            }

            if (room.Polls != null)
            {
                foreach (var poll in room.Polls)
                {
                    clonedRoom.Polls.Add(ClonePoll(poll));
                }
            }

            var sourceParticipants = participants ?? room.Participants;
            if (sourceParticipants != null)
            {
                foreach (var participant in sourceParticipants)
                {
                    clonedRoom.Participants.Add(CloneParticipant(participant));
                }
            }

            return clonedRoom;
        }

        private void ClearCurrentRoom()
        {
            if (CurrentRoom == null)
            {
                _consumeRoomJoined = false;
                ClearKnownRemoteProducers();
                LastSocketConnectionPlan = null;
                LastSocketHandshake = null;
                LastRoomValidation = null;
                return;
            }

            var previousRoom = CloneRoom(CurrentRoom);
            CurrentRoom = null;
            _consumeRoomJoined = false;
            ClearKnownRemoteProducers();
            LastSocketConnectionPlan = null;
            LastSocketHandshake = null;
            LastRoomValidation = null;

            RoomChanged?.Invoke(
                new MediaSfuRoomChangedEvent
                {
                    PreviousRoom = previousRoom,
                    CurrentRoom = null
                }
            );
        }

        private static string BuildSocketIoConnectPacket(string socketNamespace)
        {
            return socketNamespace == "/"
                ? "40"
                : $"40{socketNamespace},";
        }

        private async Task SendSocketPacketAsync(
            ClientWebSocket socket,
            string packet,
            CancellationToken cancellationToken)
        {
            var payload = Encoding.UTF8.GetBytes(packet);
            await _socketSendLock.WaitAsync(cancellationToken).ConfigureAwait(false);
            try
            {
                await socket.SendAsync(
                        new ArraySegment<byte>(payload),
                        WebSocketMessageType.Text,
                        true,
                        cancellationToken)
                    .ConfigureAwait(false);
            }
            finally
            {
                _socketSendLock.Release();
            }
        }

        private static async Task<string> ReceiveNextTextPacketAsync(
            ClientWebSocket socket,
            CancellationToken cancellationToken)
        {
            var builder = new StringBuilder();
            var buffer = new byte[4096];

            while (true)
            {
                var result = await socket.ReceiveAsync(new ArraySegment<byte>(buffer), cancellationToken)
                    .ConfigureAwait(false);

                if (result.MessageType == WebSocketMessageType.Close)
                {
                    throw new InvalidOperationException(
                        $"WebSocket transport closed: {(int?)socket.CloseStatus} {socket.CloseStatusDescription}");
                }

                if (result.MessageType == WebSocketMessageType.Text && result.Count > 0)
                {
                    builder.Append(Encoding.UTF8.GetString(buffer, 0, result.Count));
                }

                if (result.EndOfMessage)
                {
                    return builder.ToString();
                }
            }
        }

        private static MediaSfuSocketHandshake ParseSocketHandshake(string bodyText, string transportUrl)
        {
            if (string.IsNullOrWhiteSpace(bodyText))
            {
                throw new InvalidOperationException("Socket polling handshake returned an empty response.");
            }

            var jsonStart = bodyText.IndexOf('{');
            var jsonEnd = bodyText.LastIndexOf('}');
            if (jsonStart < 0 || jsonEnd <= jsonStart)
            {
                throw new InvalidOperationException($"Socket polling handshake returned an unexpected payload: {bodyText}");
            }

            var jsonPayload = bodyText.Substring(jsonStart, jsonEnd - jsonStart + 1);
            using var document = JsonDocument.Parse(jsonPayload);
            var root = document.RootElement;
            var sessionId = TryGetString(root, "sid");
            if (string.IsNullOrWhiteSpace(sessionId))
            {
                throw new InvalidOperationException($"Socket polling handshake did not include a sid: {bodyText}");
            }

            var handshake = new MediaSfuSocketHandshake
            {
                TransportUrl = transportUrl,
                SessionId = sessionId,
                PingIntervalMs = TryGetInt(root, "pingInterval") ?? 0,
                PingTimeoutMs = TryGetInt(root, "pingTimeout") ?? 0,
                MaxPayload = TryGetInt(root, "maxPayload") ?? 0
            };

            if (root.TryGetProperty("upgrades", out var upgradesElement) && upgradesElement.ValueKind == JsonValueKind.Array)
            {
                foreach (var upgrade in upgradesElement.EnumerateArray())
                {
                    if (upgrade.ValueKind == JsonValueKind.String)
                    {
                        var value = upgrade.GetString();
                        if (!string.IsNullOrWhiteSpace(value))
                        {
                            handshake.Upgrades.Add(value);
                        }
                    }
                }
            }

            return handshake;
        }

        private static Dictionary<string, object> BuildCreatePayload(MediaSfuCreateRoomRequest request)
        {
            var payload = new Dictionary<string, object>
            {
                ["action"] = "create",
                ["duration"] = request.DurationMinutes,
                ["capacity"] = request.Capacity,
                ["userName"] = request.UserName
            };

            if (request.ScheduledDateEpochMillis.HasValue)
            {
                payload["scheduledDate"] = request.ScheduledDateEpochMillis.Value;
            }

            AddIfNotBlank(payload, "secureCode", request.SecureCode);

            var eventType = ToEventTypeValue(request.EventType);
            if (!string.IsNullOrWhiteSpace(eventType))
            {
                payload["eventType"] = eventType;
            }

            AddIfNotBlank(payload, "roomName", request.RoomName);
            AddIfNotBlank(payload, "adminPasscode", request.AdminPasscode);
            AddIfNotBlank(payload, "islevel", request.IsLevel);

            var meetingRoomParameters = SerializeMeetingRoomParameters(request.MeetingRoomParameters);
            if (meetingRoomParameters != null && meetingRoomParameters.Count > 0)
            {
                payload["meetingRoomParams"] = meetingRoomParameters;
            }

            var recordingParameters = SerializeRecordingParameters(request.RecordingParameters);
            if (recordingParameters != null && recordingParameters.Count > 0)
            {
                payload["recordingParams"] = recordingParameters;
            }

            if (request.RecordOnly)
            {
                payload["recordOnly"] = true;
            }

            if (request.SafeRoom)
            {
                payload["safeRoom"] = true;
            }

            if (request.AutoStartSafeRoom)
            {
                payload["autoStartSafeRoom"] = true;
            }

            if (request.SafeRoom || request.AutoStartSafeRoom)
            {
                payload["safeRoomAction"] = request.SafeRoomAction.ToString().ToLowerInvariant();
            }

            if (request.DataBuffer)
            {
                payload["dataBuffer"] = true;
                payload["bufferType"] = request.BufferType.ToString().ToLowerInvariant();
            }

            if (request.SupportSip)
            {
                payload["supportSIP"] = true;
            }

            AddIfNotBlank(payload, "directionSIP", request.DirectionSip);

            if (request.PreferPcma)
            {
                payload["preferPCMA"] = true;
            }

            if (request.SupportTranslation)
            {
                payload["supportTranslation"] = true;
            }

            AddIfNotBlank(payload, "translationConfigNickName", request.TranslationConfigNickName);

            if (request.SupportFlexRoom)
            {
                payload["supportFlexRoom"] = true;
            }

            if (request.SupportMaxRoom)
            {
                payload["supportMaxRoom"] = true;
            }

            return payload;
        }

        private static Dictionary<string, object> BuildJoinPayload(MediaSfuJoinRoomRequest request)
        {
            var payload = new Dictionary<string, object>
            {
                ["action"] = "join",
                ["meetingID"] = request.MeetingId,
                ["userName"] = request.UserName,
                ["islevel"] = request.IsLevel
            };

            AddIfNotBlank(payload, "adminPasscode", request.AdminPasscode);
            return payload;
        }

        private static Dictionary<string, object> SerializeMeetingRoomParameters(MediaSfuMeetingRoomParameters value)
        {
            if (value == null)
            {
                return null;
            }

            var payload = new Dictionary<string, object>();
            AddIfGreaterThanZero(payload, "itemPageLimit", value.ItemPageLimit);
            AddIfNotBlank(payload, "mediaType", value.MediaType);
            AddIfTrue(payload, "addCoHost", value.AddCoHost);
            AddIfNotBlank(payload, "targetOrientation", value.TargetOrientation);
            AddIfNotBlank(payload, "targetOrientationHost", value.TargetOrientationHost);
            AddIfNotBlank(payload, "targetResolution", value.TargetResolution);
            AddIfNotBlank(payload, "targetResolutionHost", value.TargetResolutionHost);
            AddIfNotBlank(payload, "type", value.Type);
            AddIfNotBlank(payload, "audioSetting", value.AudioSetting);
            AddIfNotBlank(payload, "videoSetting", value.VideoSetting);
            AddIfNotBlank(payload, "screenshareSetting", value.ScreenshareSetting);
            AddIfNotBlank(payload, "chatSetting", value.ChatSetting);
            return payload;
        }

        private static Dictionary<string, object> SerializeRecordingParameters(MediaSfuRecordingParameters value)
        {
            if (value == null)
            {
                return null;
            }

            var payload = new Dictionary<string, object>();
            AddIfGreaterThanZero(payload, "recordingAudioPausesLimit", value.RecordingAudioPausesLimit);
            AddIfTrue(payload, "recordingAudioSupport", value.RecordingAudioSupport);
            AddIfGreaterThanZero(payload, "recordingAudioPeopleLimit", value.RecordingAudioPeopleLimit);
            AddIfGreaterThanZero(payload, "recordingAudioParticipantsTimeLimit", value.RecordingAudioParticipantsTimeLimit);
            AddIfGreaterThanZero(payload, "recordingVideoPausesLimit", value.RecordingVideoPausesLimit);
            AddIfTrue(payload, "recordingVideoSupport", value.RecordingVideoSupport);
            AddIfGreaterThanZero(payload, "recordingVideoPeopleLimit", value.RecordingVideoPeopleLimit);
            AddIfGreaterThanZero(payload, "recordingVideoParticipantsTimeLimit", value.RecordingVideoParticipantsTimeLimit);
            AddIfTrue(payload, "recordingAllParticipantsSupport", value.RecordingAllParticipantsSupport);
            AddIfTrue(payload, "recordingVideoParticipantsSupport", value.RecordingVideoParticipantsSupport);
            AddIfTrue(payload, "recordingAllParticipantsFullRoomSupport", value.RecordingAllParticipantsFullRoomSupport);
            AddIfTrue(payload, "recordingVideoParticipantsFullRoomSupport", value.RecordingVideoParticipantsFullRoomSupport);
            AddIfNotBlank(payload, "recordingPreferredOrientation", value.RecordingPreferredOrientation);
            AddIfTrue(payload, "recordingSupportForOtherOrientation", value.RecordingSupportForOtherOrientation);
            AddIfTrue(payload, "recordingMultiFormatsSupport", value.RecordingMultiFormatsSupport);
            AddIfTrue(payload, "recordingHlsSupport", value.RecordingHlsSupport);

            if (value.RecordingAudioPausesCount.HasValue)
            {
                payload["recordingAudioPausesCount"] = value.RecordingAudioPausesCount.Value;
            }

            if (value.RecordingVideoPausesCount.HasValue)
            {
                payload["recordingVideoPausesCount"] = value.RecordingVideoPausesCount.Value;
            }

            return payload;
        }

        private static Dictionary<string, object> SerializeUserRecordingParams(MediaSfuUserRecordingParams value)
        {
            if (value == null)
            {
                return null;
            }

            var payload = new Dictionary<string, object>();
            var mainSpecs = SerializeRecordingMainSpecs(value.MainSpecs);
            if (mainSpecs != null && mainSpecs.Count > 0)
            {
                payload["mainSpecs"] = mainSpecs;
            }

            var displaySpecs = SerializeRecordingDisplaySpecs(value.DisplaySpecs);
            if (displaySpecs != null && displaySpecs.Count > 0)
            {
                payload["dispSpecs"] = displaySpecs;
            }

            var textSpecs = SerializeRecordingTextSpecs(value.TextSpecs);
            if (textSpecs != null && textSpecs.Count > 0)
            {
                payload["textSpecs"] = textSpecs;
            }

            return payload.Count == 0 ? null : payload;
        }

        private static Dictionary<string, object> SerializeRecordingMainSpecs(MediaSfuRecordingMainSpecs value)
        {
            if (value == null)
            {
                return null;
            }

            return new Dictionary<string, object>
            {
                ["mediaOptions"] = value.MediaOptions ?? string.Empty,
                ["audioOptions"] = value.AudioOptions ?? string.Empty,
                ["videoOptions"] = value.VideoOptions ?? string.Empty,
                ["videoType"] = value.VideoType ?? string.Empty,
                ["videoOptimized"] = value.VideoOptimized,
                ["recordingDisplayType"] = value.RecordingDisplayType ?? string.Empty,
                ["addHls"] = value.AddHls
            };
        }

        private static Dictionary<string, object> SerializeRecordingDisplaySpecs(MediaSfuRecordingDisplaySpecs value)
        {
            if (value == null)
            {
                return null;
            }

            return new Dictionary<string, object>
            {
                ["nameTags"] = value.NameTags,
                ["backgroundColor"] = value.BackgroundColor ?? string.Empty,
                ["nameTagsColor"] = value.NameTagsColor ?? string.Empty,
                ["orientationVideo"] = value.OrientationVideo ?? string.Empty
            };
        }

        private static Dictionary<string, object> SerializeRecordingTextSpecs(MediaSfuRecordingTextSpecs value)
        {
            if (value == null)
            {
                return null;
            }

            return new Dictionary<string, object>
            {
                ["addText"] = value.AddText,
                ["customText"] = value.CustomText ?? string.Empty,
                ["customTextPosition"] = value.CustomTextPosition ?? string.Empty,
                ["customTextColor"] = value.CustomTextColor ?? string.Empty
            };
        }

        private MediaSfuRoom TryParseRoom(string bodyText, string localUserName, string localIsLevel)
        {
            if (string.IsNullOrWhiteSpace(bodyText))
            {
                return null;
            }

            try
            {
                using var document = JsonDocument.Parse(bodyText);
                var root = document.RootElement;
                if (root.ValueKind != JsonValueKind.Object)
                {
                    return null;
                }

                var roomName = TryGetString(root, "roomName");
                if (string.IsNullOrWhiteSpace(roomName))
                {
                    return null;
                }

                var room = new MediaSfuRoom
                {
                    RoomName = roomName,
                    PublicUrl = TryGetString(root, "publicURL", "publicUrl") ?? string.Empty,
                    Link = TryGetString(root, "link") ?? string.Empty,
                    Secret = TryGetString(root, "secret") ?? string.Empty,
                    SecureCode = TryGetString(root, "secureCode") ?? string.Empty,
                    ApiUserName = Options.Credentials?.ApiUserName ?? string.Empty
                };

                var recordingParametersElement = TryGetNestedElement(root, "recordingParams", "recordParams");
                if (recordingParametersElement.HasValue &&
                    recordingParametersElement.Value.ValueKind == JsonValueKind.Object)
                {
                    ApplyRecordingParameters(room, TryParseRecordingParameters(recordingParametersElement.Value));
                }

                var displayName = TryGetString(root, "userName") ?? localUserName;
                if (!string.IsNullOrWhiteSpace(displayName))
                {
                    room.Participants.Add(new MediaSfuParticipant
                    {
                        ParticipantId = displayName,
                        DisplayName = displayName,
                        Role = ResolveParticipantRole(localIsLevel),
                        IsLocal = true,
                        AudioOn = false,
                        VideoOn = false,
                        ScreenOn = false
                    });
                }

                return room;
            }
            catch
            {
                return null;
            }
        }

        private static (string Error, string Detail) ParseErrorResponse(string bodyText, string fallbackMessage)
        {
            if (string.IsNullOrWhiteSpace(bodyText))
            {
                return (fallbackMessage ?? "Unknown error", string.Empty);
            }

            try
            {
                using var document = JsonDocument.Parse(bodyText);
                var root = document.RootElement;
                var error = TryGetString(root, "error", "message") ?? fallbackMessage ?? "Unknown error";
                return (error, bodyText);
            }
            catch
            {
                return (fallbackMessage ?? bodyText, bodyText);
            }
        }

        private static MediaSfuParticipantRole ResolveParticipantRole(string isLevel)
        {
            return isLevel switch
            {
                "2" => MediaSfuParticipantRole.Host,
                "1" => MediaSfuParticipantRole.CoHost,
                _ => MediaSfuParticipantRole.Participant
            };
        }

        private static string ToEventTypeValue(MediaSfuEventType eventType)
        {
            return eventType switch
            {
                MediaSfuEventType.Broadcast => "broadcast",
                MediaSfuEventType.Chat => "chat",
                MediaSfuEventType.Webinar => "webinar",
                MediaSfuEventType.Conference => "conference",
                _ => null
            };
        }

        private static string TryGetString(JsonElement element, params string[] propertyNames)
        {
            foreach (var propertyName in propertyNames)
            {
                if (element.TryGetProperty(propertyName, out var property) && property.ValueKind == JsonValueKind.String)
                {
                    return property.GetString();
                }
            }

            return null;
        }

        private static int? TryGetInt(JsonElement element, params string[] propertyNames)
        {
            foreach (var propertyName in propertyNames)
            {
                if (!element.TryGetProperty(propertyName, out var property))
                {
                    continue;
                }

                if (property.ValueKind == JsonValueKind.Number && property.TryGetInt32(out var value))
                {
                    return value;
                }

                if (property.ValueKind == JsonValueKind.String && int.TryParse(property.GetString(), out value))
                {
                    return value;
                }
            }

            return null;
        }

        private static float? TryGetFloat(JsonElement element, params string[] propertyNames)
        {
            foreach (var propertyName in propertyNames)
            {
                if (!element.TryGetProperty(propertyName, out var property))
                {
                    continue;
                }

                if (property.ValueKind == JsonValueKind.Number && property.TryGetSingle(out var value))
                {
                    return value;
                }

                if (property.ValueKind == JsonValueKind.String &&
                    float.TryParse(property.GetString(), NumberStyles.Float, CultureInfo.InvariantCulture, out value))
                {
                    return value;
                }
            }

            return null;
        }

        private static bool? TryGetBool(JsonElement element, params string[] propertyNames)
        {
            foreach (var propertyName in propertyNames)
            {
                if (!element.TryGetProperty(propertyName, out var property))
                {
                    continue;
                }

                if (property.ValueKind == JsonValueKind.True)
                {
                    return true;
                }

                if (property.ValueKind == JsonValueKind.False)
                {
                    return false;
                }

                if (property.ValueKind == JsonValueKind.String && bool.TryParse(property.GetString(), out var value))
                {
                    return value;
                }
            }

            return null;
        }

        private static bool ParseFlexibleBool(JsonElement element, bool defaultValue, params string[] propertyNames)
        {
            foreach (var propertyName in propertyNames)
            {
                if (!element.TryGetProperty(propertyName, out var property))
                {
                    continue;
                }

                switch (property.ValueKind)
                {
                    case JsonValueKind.True:
                        return true;
                    case JsonValueKind.False:
                        return false;
                    case JsonValueKind.Number:
                        if (property.TryGetInt32(out var numericValue))
                        {
                            return numericValue != 0;
                        }

                        break;
                    case JsonValueKind.String:
                    {
                        var value = property.GetString();
                        if (bool.TryParse(value, out var boolValue))
                        {
                            return boolValue;
                        }

                        if (string.Equals(value, "1", StringComparison.Ordinal))
                        {
                            return true;
                        }

                        if (string.Equals(value, "0", StringComparison.Ordinal))
                        {
                            return false;
                        }

                        break;
                    }
                }
            }

            return defaultValue;
        }

        private static object DeserializeJsonPayload(string json, string argumentName)
        {
            try
            {
                return JsonSerializer.Deserialize<JsonElement>(json);
            }
            catch (JsonException error)
            {
                throw new InvalidOperationException(
                    $"{argumentName} must be valid JSON.",
                    error);
            }
        }

        private static JsonElement? TryGetNestedElement(JsonElement element, params string[] propertyNames)
        {
            foreach (var propertyName in propertyNames)
            {
                if (element.TryGetProperty(propertyName, out var property))
                {
                    return property;
                }
            }

            return null;
        }

        private static JsonElement? TryGetCoHostResponsibilitiesElement(JsonElement element)
        {
            var candidate = TryGetNestedElement(
                element,
                "coHostResponsibilities",
                "coHostResponsibility",
                "coHostRes");
            return candidate.HasValue && candidate.Value.ValueKind == JsonValueKind.Array
                ? candidate
                : null;
        }

        private static void AddIfNotBlank(IDictionary<string, object> dictionary, string key, string value)
        {
            if (!string.IsNullOrWhiteSpace(value))
            {
                dictionary[key] = value;
            }
        }

        private static void AddIfHasValue(IDictionary<string, object> dictionary, string key, float? value)
        {
            if (value.HasValue)
            {
                dictionary[key] = value.Value;
            }
        }

        private static void AddIfTrue(IDictionary<string, object> dictionary, string key, bool value)
        {
            if (value)
            {
                dictionary[key] = true;
            }
        }

        private static void AddIfGreaterThanZero(IDictionary<string, object> dictionary, string key, int value)
        {
            if (value > 0)
            {
                dictionary[key] = value;
            }
        }

        private void SetConnectionState(MediaSfuConnectionState nextState)
        {
            if (ConnectionState == nextState)
            {
                return;
            }

            var previousState = ConnectionState;
            ConnectionState = nextState;

            ConnectionStateChanged?.Invoke(
                new MediaSfuConnectionStateChangedEvent
                {
                    PreviousState = previousState,
                    CurrentState = nextState
                }
            );
        }

        private void SetCurrentRoom(MediaSfuRoom nextRoom)
        {
            CloseSocketTransportSynchronously(markDisconnected: false);
            var previousRoom = CurrentRoom;
            CurrentRoom = nextRoom;
            LastSocketConnectionPlan = null;
            LastSocketHandshake = null;
            LastRoomValidation = null;

            RoomChanged?.Invoke(
                new MediaSfuRoomChangedEvent
                {
                    PreviousRoom = previousRoom,
                    CurrentRoom = nextRoom
                }
            );
        }

        private void PublishError(string operation, string message, string detail = "")
        {
            ErrorOccurred?.Invoke(
                new MediaSfuErrorEvent
                {
                    Operation = operation,
                    Message = message,
                    Detail = detail
                }
            );
        }

        private sealed class SocketConnectionTarget
        {
            public string BaseUrl { get; set; } = string.Empty;

            public string Namespace { get; set; } = "/";

            public string Query { get; set; } = string.Empty;

            public bool UsesSecureTransport { get; set; }
        }

        private sealed class SocketTransportSession
        {
            public ClientWebSocket Socket { get; set; }

            public string TransportUrl { get; set; } = string.Empty;

            public string Namespace { get; set; } = "/";

            public string NamespaceConnectPacket { get; set; } = string.Empty;

            public CancellationTokenSource ReceiveLoopCancellation { get; set; }

            public Task ReceiveLoopTask { get; set; }
        }

        private sealed class SocketPacketMetadata
        {
            public string Namespace { get; set; } = "/";

            public int? AckId { get; set; }

            public string Payload { get; set; } = string.Empty;
        }

        private sealed class SocketEventPacket
        {
            public string Namespace { get; set; } = "/";

            public int? AckId { get; set; }

            public string EventName { get; set; } = string.Empty;

            public string PayloadJson { get; set; } = string.Empty;

            public string RawPayload { get; set; } = string.Empty;
        }

        private sealed class RecordingControlAck
        {
            public bool Success { get; set; }

            public string Reason { get; set; } = string.Empty;

            public string RecordState { get; set; } = string.Empty;

            public int? PauseCount { get; set; }

            public string RawPayload { get; set; } = string.Empty;
        }

        private sealed class BooleanSocketAck
        {
            public bool Success { get; set; }

            public string Reason { get; set; } = string.Empty;

            public string RawPayload { get; set; } = string.Empty;
        }
    }
}