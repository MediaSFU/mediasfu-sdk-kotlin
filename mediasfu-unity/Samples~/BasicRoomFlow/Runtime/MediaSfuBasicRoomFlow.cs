using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using UnityEngine;
using UnityEngine.Events;

namespace MediaSFU.Unity.Samples
{
    [AddComponentMenu("MediaSFU/Basic Room Flow")]
    public sealed class MediaSfuBasicRoomFlow : MonoBehaviour
    {
        [Serializable]
        public sealed class StringEvent : UnityEvent<string>
        {
        }

        [Serializable]
        public sealed class BoolEvent : UnityEvent<bool>
        {
        }

        [Serializable]
        public sealed class IntEvent : UnityEvent<int>
        {
        }

        [Header("Connection")]
        [SerializeField] private MediaSfuConnectionMode connectionMode = MediaSfuConnectionMode.Cloud;
        [SerializeField] private bool connectMediaSfu = true;
        [SerializeField] private string baseUrl = string.Empty;
        [SerializeField] private string localLink = string.Empty;
        [SerializeField] private string apiUserName = string.Empty;
        [SerializeField] private string apiKey = string.Empty;

        [Header("Session")]
        [SerializeField] private string roomName = string.Empty;
        [SerializeField] private string displayName = "player-one";
        [SerializeField] private bool joinAsHost;
        [SerializeField] private string adminPasscode = string.Empty;
        [SerializeField] private string chatMessage = "Hello from Unity";
        [SerializeField] private int durationMinutes = 60;
        [SerializeField] private int capacity = 100;
        [SerializeField] private MediaSfuEventType eventType = MediaSfuEventType.Conference;

        [Header("Status")]
        [SerializeField] [TextArea(2, 6)] private string statusText = "Idle";
        [SerializeField] private StringEvent statusChanged = new StringEvent();
        [SerializeField] private StringEvent errorRaised = new StringEvent();

        [Header("Scene Output Events")]
        [SerializeField] private StringEvent roomSnapshotChanged = new StringEvent();
        [SerializeField] private StringEvent participantSnapshotChanged = new StringEvent();
        [SerializeField] private StringEvent pendingRequestSelectionChanged = new StringEvent();
        [SerializeField] private StringEvent waitingParticipantSelectionChanged = new StringEvent();
        [SerializeField] private StringEvent breakoutSnapshotChanged = new StringEvent();
        [SerializeField] private StringEvent consumingDomainsSnapshotChanged = new StringEvent();
        [SerializeField] private StringEvent pollSnapshotChanged = new StringEvent();
        [SerializeField] private StringEvent whiteboardSnapshotChanged = new StringEvent();
        [SerializeField] private IntEvent pendingRequestCountChanged = new IntEvent();
        [SerializeField] private IntEvent waitingParticipantCountChanged = new IntEvent();
        [SerializeField] private IntEvent selectedPendingRequestIndexChanged = new IntEvent();
        [SerializeField] private IntEvent selectedWaitingParticipantIndexChanged = new IntEvent();
        [SerializeField] private BoolEvent confirmPresencePendingChanged = new BoolEvent();
        [SerializeField] private BoolEvent pollPromptVisibilityChanged = new BoolEvent();
        [SerializeField] private BoolEvent whiteboardActiveChanged = new BoolEvent();
        [SerializeField] private BoolEvent recordingActiveChanged = new BoolEvent();
        [SerializeField] private BoolEvent recordingPausedChanged = new BoolEvent();
        [SerializeField] private BoolEvent recordingPauseResumeAvailableChanged = new BoolEvent();

        [Header("Inspector Controls")]
        [SerializeField] private int selectedPendingRequestIndex;
        [SerializeField] private int selectedWaitingParticipantIndex;

        [Header("Scene State")]
        [SerializeField] private int pendingRequestCount;
        [SerializeField] private int waitingParticipantCount;
        [SerializeField] private bool confirmPresencePending;
        [SerializeField] private bool pollPromptVisible;
        [SerializeField] private bool whiteboardActive;
        [SerializeField] private bool recordingActive;
        [SerializeField] private bool recordingPaused;
        [SerializeField] private bool recordingPauseResumeAvailable;

        [Header("Room Snapshot")]
        [SerializeField] [TextArea(3, 10)] private string roomSnapshotText = string.Empty;
        [SerializeField] [TextArea(2, 6)] private string selectedPendingRequestText = string.Empty;
        [SerializeField] [TextArea(2, 6)] private string selectedWaitingParticipantText = string.Empty;
        [SerializeField] [TextArea(2, 6)] private string pollSnapshotText = string.Empty;
        [SerializeField] [TextArea(2, 6)] private string whiteboardSnapshotText = string.Empty;
        [SerializeField] [TextArea(2, 6)] private string participantSnapshotText = string.Empty;
        [SerializeField] [TextArea(2, 6)] private string breakoutSnapshotText = string.Empty;
        [SerializeField] [TextArea(2, 6)] private string consumingDomainSnapshotText = string.Empty;
        [SerializeField] private List<string> participantSummaries = new List<string>();
        [SerializeField] private List<string> pendingRequestSummaries = new List<string>();
        [SerializeField] private List<string> waitingParticipantSummaries = new List<string>();
        [SerializeField] private List<string> breakoutRoomSummaries = new List<string>();
        [SerializeField] private List<string> consumingDomainSummaries = new List<string>();

        private MediaSfuClient client;
        private IMediaSfuWebRtcDevice webRtcDevice;
        private IMediaSfuWebRtcDevice ownedWebRtcDevice;
        private IMediaSfuLocalMediaBackend localMediaBackend;
        private MediaSfuTransportBackedLocalMediaBackend ownedTransportBackedLocalMediaBackend;
        private IMediaSfuRemoteMediaBridge remoteMediaBridge;
        private MediaSfuTransportBackedRemoteMediaBridge ownedTransportBackedRemoteMediaBridge;
        private MediaSfuNativePluginWebRtcEngine nativePluginWebRtcEngine;

        public string StatusText => statusText;

        public string RoomSnapshotText => roomSnapshotText;

        public string ParticipantSnapshotText => participantSnapshotText;

        public string SelectedPendingRequestText => selectedPendingRequestText;

        public string SelectedWaitingParticipantText => selectedWaitingParticipantText;

        public string BreakoutSnapshotText => breakoutSnapshotText;

        public string ConsumingDomainSnapshotText => consumingDomainSnapshotText;

        public MediaSfuNativePluginWebRtcEngine NativePluginWebRtcEngine => nativePluginWebRtcEngine;

        public string PollSnapshotText => pollSnapshotText;

        public string WhiteboardSnapshotText => whiteboardSnapshotText;

        public int SelectedPendingRequestIndex => selectedPendingRequestIndex;

        public int SelectedWaitingParticipantIndex => selectedWaitingParticipantIndex;

        public int PendingRequestCount => pendingRequestCount;

        public int WaitingParticipantCount => waitingParticipantCount;

        public bool ConfirmPresencePending => confirmPresencePending;

        public bool PollPromptVisible => pollPromptVisible;

        public bool WhiteboardActive => whiteboardActive;

        public bool RecordingActive => recordingActive;

        public bool RecordingPaused => recordingPaused;

        public bool RecordingPauseResumeAvailable => recordingPauseResumeAvailable;

        public MediaSfuClient Client => client;

        public void AttachWebRtcDevice(IMediaSfuWebRtcDevice device)
        {
            ReplaceOwnedWebRtcDevice(null);
            AttachWebRtcDeviceCore(device);
        }

        public void AttachLocalMediaBackend(IMediaSfuLocalMediaBackend backend)
        {
            nativePluginWebRtcEngine = null;

            ReplaceOwnedWebRtcDevice(null);

            if (!ReferenceEquals(backend, ownedTransportBackedLocalMediaBackend) &&
                ownedTransportBackedLocalMediaBackend != null)
            {
                ownedTransportBackedLocalMediaBackend.Dispose();
                ownedTransportBackedLocalMediaBackend = null;
            }

            webRtcDevice = null;
            localMediaBackend = backend;

            if (client != null)
            {
                client.AttachLocalMediaBackend(backend);
            }
        }

        public void AttachTransportBackedLocalMediaAdapter(IMediaSfuTransportBackedLocalMediaAdapter adapter)
        {
            ownedTransportBackedLocalMediaBackend?.Dispose();
            ownedTransportBackedLocalMediaBackend = adapter == null
                ? null
                : new MediaSfuTransportBackedLocalMediaBackend(adapter);
            AttachLocalMediaBackend(ownedTransportBackedLocalMediaBackend);
        }

        public void AttachRemoteMediaBridge(IMediaSfuRemoteMediaBridge bridge)
        {
            nativePluginWebRtcEngine = null;

            ReplaceOwnedWebRtcDevice(null);

            if (!ReferenceEquals(bridge, ownedTransportBackedRemoteMediaBridge) &&
                ownedTransportBackedRemoteMediaBridge != null)
            {
                ownedTransportBackedRemoteMediaBridge.Dispose();
                ownedTransportBackedRemoteMediaBridge = null;
            }

            webRtcDevice = null;
            remoteMediaBridge = bridge;

            if (client != null)
            {
                client.AttachRemoteMediaBridge(bridge);
            }
        }

        public void AttachTransportBackedRemoteMediaAdapter(IMediaSfuTransportBackedRemoteMediaAdapter adapter)
        {
            ownedTransportBackedRemoteMediaBridge?.Dispose();
            ownedTransportBackedRemoteMediaBridge = adapter == null
                ? null
                : new MediaSfuTransportBackedRemoteMediaBridge(adapter);
            AttachRemoteMediaBridge(ownedTransportBackedRemoteMediaBridge);
        }

        public void AttachTransportBackedWebRtcDevice(
            IMediaSfuTransportBackedLocalMediaAdapter localAdapter,
            IMediaSfuTransportBackedRemoteMediaAdapter remoteAdapter)
        {
            nativePluginWebRtcEngine = null;

            var device = localAdapter == null || remoteAdapter == null
                ? null
                : new MediaSfuTransportBackedWebRtcDevice(localAdapter, remoteAdapter);
            ReplaceOwnedWebRtcDevice(device);
            AttachWebRtcDeviceCore(device);
        }

        public void AttachTransportBackedWebRtcEngine(IMediaSfuWebRtcEngine engine)
        {
            nativePluginWebRtcEngine = engine as MediaSfuNativePluginWebRtcEngine;

            var device = engine == null
                ? null
                : new MediaSfuWebRtcEngineDevice(engine);
            ReplaceOwnedWebRtcDevice(device);
            AttachWebRtcDeviceCore(device);
        }

        public void AttachNativePluginWebRtcEngine(MediaSfuNativePluginWebRtcEngineOptions options = null)
        {
            var creationResult = MediaSfuNativePluginWebRtcEngine.TryCreateEngine(options);
            if (!creationResult.Success || creationResult.Value == null)
            {
                var message = string.IsNullOrWhiteSpace(creationResult?.Detail)
                    ? creationResult?.Error ?? "AttachNativePluginWebRtcEngine failed."
                    : (creationResult.Error ?? "AttachNativePluginWebRtcEngine failed.") + " " + creationResult.Detail;
                errorRaised.Invoke(message);
                UpdateStatus(message);
                return;
            }

            AttachTransportBackedWebRtcEngine(creationResult.Value);
            UpdateStatus(string.IsNullOrWhiteSpace(creationResult.Detail)
                ? "Native plugin WebRTC engine attached."
                : creationResult.Detail);
        }

        public async void CreateRoomFromInspector()
        {
            await RunCreateRoomAsync();
        }

        public async void JoinRoomFromInspector()
        {
            await RunJoinRoomAsync();
        }

        public async void ConnectMediaFromInspector()
        {
            await RunConnectMediaAsync();
        }

        public async void LeaveRoomFromInspector()
        {
            await RunLeaveRoomAsync();
        }

        public async void MuteMicrophoneFromInspector()
        {
            await RunSetMicrophoneEnabledAsync(false);
        }

        public async void UnmuteMicrophoneFromInspector()
        {
            await RunSetMicrophoneEnabledAsync(true);
        }

        public async void StopCameraFromInspector()
        {
            await RunSetCameraEnabledAsync(false);
        }

        public async void StartCameraFromInspector()
        {
            await RunSetCameraEnabledAsync(true);
        }

        public async void StopScreenShareFromInspector()
        {
            await RunSetScreenShareEnabledAsync(false);
        }

        public async void StartScreenShareFromInspector()
        {
            await RunSetScreenShareEnabledAsync(true);
        }

        public async void StartRecordingFromInspector()
        {
            await RunStartRecordingAsync();
        }

        public async void PauseRecordingFromInspector()
        {
            await RunPauseRecordingAsync();
        }

        public async void ResumeRecordingFromInspector()
        {
            await RunResumeRecordingAsync();
        }

        public async void StopRecordingFromInspector()
        {
            await RunStopRecordingAsync();
        }

        public async void SendChatMessageFromInspector()
        {
            await RunSendChatMessageAsync();
        }

        public async void ApproveFirstPendingRequestFromInspector()
        {
            await RunRespondToPendingRequestByIndexAsync(0, true);
        }

        public async void RejectFirstPendingRequestFromInspector()
        {
            await RunRespondToPendingRequestByIndexAsync(0, false);
        }

        public async void ApproveSelectedPendingRequestFromInspector()
        {
            await RunRespondToSelectedPendingRequestAsync(true);
        }

        public async void RejectSelectedPendingRequestFromInspector()
        {
            await RunRespondToSelectedPendingRequestAsync(false);
        }

        public async void AllowFirstWaitingParticipantFromInspector()
        {
            await RunRespondToWaitingParticipantByIndexAsync(0, true);
        }

        public async void DenyFirstWaitingParticipantFromInspector()
        {
            await RunRespondToWaitingParticipantByIndexAsync(0, false);
        }

        public async void AllowSelectedWaitingParticipantFromInspector()
        {
            await RunRespondToSelectedWaitingParticipantAsync(true);
        }

        public async void DenySelectedWaitingParticipantFromInspector()
        {
            await RunRespondToSelectedWaitingParticipantAsync(false);
        }

        public async void StartOrUpdateBreakoutRoomsFromInspector()
        {
            await RunStartOrUpdateBreakoutRoomsWithCurrentParticipantsAsync();
        }

        public async void StopBreakoutRoomsFromInspector()
        {
            await RunStopBreakoutRoomsAsync();
        }

        public async void StartOrUpdateWhiteboardFromInspector()
        {
            await RunStartOrUpdateWhiteboardWithCurrentParticipantsAsync();
        }

        public async void StopWhiteboardFromInspector()
        {
            await RunStopWhiteboardAsync();
        }

        public async void SendWhiteboardRectangleFromInspector()
        {
            await RunSendWhiteboardShapeAsync(BuildDefaultWhiteboardRectangle());
        }

        public async void ClearWhiteboardFromInspector()
        {
            await RunClearWhiteboardAsync();
        }

        public void SelectPreviousPendingRequestFromInspector()
        {
            SelectPendingRequestIndex(selectedPendingRequestIndex - 1);
        }

        public void SelectNextPendingRequestFromInspector()
        {
            SelectPendingRequestIndex(selectedPendingRequestIndex + 1);
        }

        public void SelectPendingRequestIndexFromInspector(int requestIndex)
        {
            SelectPendingRequestIndex(requestIndex);
        }

        public void SelectPreviousWaitingParticipantFromInspector()
        {
            SelectWaitingParticipantIndex(selectedWaitingParticipantIndex - 1);
        }

        public void SelectNextWaitingParticipantFromInspector()
        {
            SelectWaitingParticipantIndex(selectedWaitingParticipantIndex + 1);
        }

        public void SelectWaitingParticipantIndexFromInspector(int participantIndex)
        {
            SelectWaitingParticipantIndex(participantIndex);
        }

        public void ConfirmPresenceFromInspector()
        {
            RunConfirmPresence(suppressFuturePrompts: false);
        }

        public void ConfirmPresenceAndSuppressFuturePromptsFromInspector()
        {
            RunConfirmPresence(suppressFuturePrompts: true);
        }

        public async Task<MediaSfuOperationResult<MediaSfuRoom>> RunCreateRoomAsync()
        {
            RecreateClient();

            var result = await client.CreateRoomAsync(
                new MediaSfuCreateRoomRequest
                {
                    UserName = displayName,
                    DurationMinutes = durationMinutes,
                    Capacity = capacity,
                    EventType = eventType,
                    RoomName = roomName,
                    AdminPasscode = adminPasscode,
                    IsLevel = joinAsHost ? "2" : string.Empty
                });

            HandleResult("CreateRoom", result);
            return result;
        }

        public async Task<MediaSfuOperationResult<MediaSfuRoom>> RunJoinRoomAsync()
        {
            RecreateClient();

            var result = await client.JoinRoomAsync(
                new MediaSfuJoinRoomRequest
                {
                    MeetingId = roomName,
                    UserName = displayName,
                    AdminPasscode = adminPasscode,
                    IsLevel = joinAsHost ? "2" : "0"
                });

            HandleResult("JoinRoom", result);
            return result;
        }

        public async Task<MediaSfuOperationResult<bool>> RunConnectMediaAsync()
        {
            EnsureClient();
            var result = await client.ConnectMediaAsync();
            HandleBoolResult("ConnectMedia", result);
            return result;
        }

        public async Task<MediaSfuOperationResult<bool>> RunLeaveRoomAsync()
        {
            EnsureClient();
            var result = await client.LeaveRoomAsync();
            HandleBoolResult("LeaveRoom", result);
            return result;
        }

        public async Task<MediaSfuOperationResult<bool>> RunSetMicrophoneEnabledAsync(bool enabled)
        {
            EnsureClient();
            var result = await client.SetMicrophoneEnabledAsync(enabled);
            HandleBoolResult(enabled ? "UnmuteMicrophone" : "MuteMicrophone", result);
            return result;
        }

        public async Task<MediaSfuOperationResult<bool>> RunSetCameraEnabledAsync(bool enabled)
        {
            EnsureClient();
            var result = await client.SetCameraEnabledAsync(enabled);
            HandleBoolResult(enabled ? "StartCamera" : "StopCamera", result);
            return result;
        }

        public async Task<MediaSfuOperationResult<bool>> RunSetScreenShareEnabledAsync(bool enabled)
        {
            EnsureClient();
            var result = await client.SetScreenShareEnabledAsync(enabled);
            HandleBoolResult(enabled ? "StartScreenShare" : "StopScreenShare", result);
            return result;
        }

        public async Task<MediaSfuOperationResult<bool>> RunStartRecordingAsync()
        {
            EnsureClient();
            var result = await client.StartRecordingAsync();
            HandleBoolResult("StartRecording", result);
            return result;
        }

        public async Task<MediaSfuOperationResult<bool>> RunPauseRecordingAsync()
        {
            EnsureClient();
            var result = await client.PauseRecordingAsync();
            HandleBoolResult("PauseRecording", result);
            return result;
        }

        public async Task<MediaSfuOperationResult<bool>> RunResumeRecordingAsync()
        {
            EnsureClient();
            var result = await client.ResumeRecordingAsync();
            HandleBoolResult("ResumeRecording", result);
            return result;
        }

        public async Task<MediaSfuOperationResult<bool>> RunStopRecordingAsync()
        {
            EnsureClient();
            var result = await client.StopRecordingAsync();
            HandleBoolResult("StopRecording", result);
            return result;
        }

        public async Task<MediaSfuOperationResult<bool>> RunSendChatMessageAsync()
        {
            EnsureClient();
            var result = await client.SendChatMessageAsync(chatMessage);
            HandleBoolResult("SendChatMessage", result);
            return result;
        }

        public async Task<MediaSfuOperationResult<bool>> RunRespondToFirstPendingRequestAsync(bool accept)
        {
            return await RunRespondToPendingRequestByIndexAsync(0, accept);
        }

        public async Task<MediaSfuOperationResult<bool>> RunRespondToSelectedPendingRequestAsync(bool accept)
        {
            return await RunRespondToPendingRequestByIndexAsync(selectedPendingRequestIndex, accept);
        }

        public async Task<MediaSfuOperationResult<bool>> RunRespondToPendingRequestByIndexAsync(int requestIndex, bool accept)
        {
            EnsureClient();

            var request = TryGetPendingRequest(client.CurrentRoom, requestIndex);
            var result = request == null
                ? MediaSfuOperationResult<bool>.FromFailure($"No pending room request is available on CurrentRoom at index {requestIndex}.")
                : await client.RespondToRoomRequestAsync(request, accept);

            HandleBoolResult(accept ? "ApprovePendingRequest" : "RejectPendingRequest", result);
            return result;
        }

        public async Task<MediaSfuOperationResult<bool>> RunRespondToFirstWaitingParticipantAsync(bool allow)
        {
            return await RunRespondToWaitingParticipantByIndexAsync(0, allow);
        }

        public async Task<MediaSfuOperationResult<bool>> RunRespondToSelectedWaitingParticipantAsync(bool allow)
        {
            return await RunRespondToWaitingParticipantByIndexAsync(selectedWaitingParticipantIndex, allow);
        }

        public async Task<MediaSfuOperationResult<bool>> RunRespondToWaitingParticipantByIndexAsync(int participantIndex, bool allow)
        {
            EnsureClient();

            var participant = TryGetWaitingParticipant(client.CurrentRoom, participantIndex);
            var result = participant == null
                ? MediaSfuOperationResult<bool>.FromFailure($"No waiting-room participant is available on CurrentRoom at index {participantIndex}.")
                : await client.RespondToWaitingParticipantAsync(participant, allow);

            HandleBoolResult(allow ? "AllowWaitingParticipant" : "DenyWaitingParticipant", result);
            return result;
        }

        public async Task<MediaSfuOperationResult<bool>> RunStartOrUpdateBreakoutRoomsWithCurrentParticipantsAsync()
        {
            return await RunStartOrUpdateBreakoutRoomsAsync(BuildDefaultBreakoutRoomsRequest());
        }

        public async Task<MediaSfuOperationResult<bool>> RunStartOrUpdateBreakoutRoomsAsync(MediaSfuBreakoutRoomsRequest request)
        {
            EnsureClient();
            var result = await client.StartOrUpdateBreakoutRoomsAsync(request);
            HandleBoolResult("StartOrUpdateBreakoutRooms", result);
            return result;
        }

        public async Task<MediaSfuOperationResult<bool>> RunStopBreakoutRoomsAsync()
        {
            EnsureClient();
            var result = await client.StopBreakoutRoomsAsync();
            HandleBoolResult("StopBreakoutRooms", result);
            return result;
        }

        public async Task<MediaSfuOperationResult<bool>> RunStartOrUpdateWhiteboardWithCurrentParticipantsAsync()
        {
            return await RunStartOrUpdateWhiteboardAsync(BuildDefaultWhiteboardSessionRequest());
        }

        public async Task<MediaSfuOperationResult<bool>> RunStartOrUpdateWhiteboardAsync(MediaSfuWhiteboardSessionRequest request)
        {
            EnsureClient();
            var result = await client.StartOrUpdateWhiteboardAsync(request);
            HandleBoolResult("StartOrUpdateWhiteboard", result);
            return result;
        }

        public async Task<MediaSfuOperationResult<bool>> RunStopWhiteboardAsync()
        {
            EnsureClient();
            var result = await client.StopWhiteboardAsync();
            HandleBoolResult("StopWhiteboard", result);
            return result;
        }

        public async Task<MediaSfuOperationResult<bool>> RunSendWhiteboardShapeAsync(MediaSfuWhiteboardShape shape)
        {
            EnsureClient();
            var result = await client.SendWhiteboardActionAsync("shape", shape);
            HandleBoolResult("SendWhiteboardShape", result);
            return result;
        }

        public async Task<MediaSfuOperationResult<bool>> RunUpdateWhiteboardShapesAsync(IReadOnlyList<MediaSfuWhiteboardShape> shapes)
        {
            EnsureClient();
            var result = await client.UpdateWhiteboardShapesAsync(shapes);
            HandleBoolResult("UpdateWhiteboardShapes", result);
            return result;
        }

        public async Task<MediaSfuOperationResult<bool>> RunClearWhiteboardAsync()
        {
            EnsureClient();
            var result = await client.ClearWhiteboardAsync();
            HandleBoolResult("ClearWhiteboard", result);
            return result;
        }

        private MediaSfuBreakoutRoomsRequest BuildDefaultBreakoutRoomsRequest()
        {
            var roomParticipants = new List<MediaSfuBreakoutParticipant>();
            var participants = client?.CurrentRoom?.Participants ?? new List<MediaSfuParticipant>();
            foreach (var participant in participants)
            {
                if (participant == null || participant.Role == MediaSfuParticipantRole.Host || string.IsNullOrWhiteSpace(participant.DisplayName))
                {
                    continue;
                }

                roomParticipants.Add(
                    new MediaSfuBreakoutParticipant
                    {
                        DisplayName = participant.DisplayName,
                        BreakRoom = 0,
                    });
            }

            return new MediaSfuBreakoutRoomsRequest
            {
                Rooms = roomParticipants.Count == 0
                    ? new List<List<MediaSfuBreakoutParticipant>>()
                    : new List<List<MediaSfuBreakoutParticipant>> { roomParticipants },
                NewParticipantAction = "autoAssignNewRoom",
            };
        }

        private MediaSfuWhiteboardSessionRequest BuildDefaultWhiteboardSessionRequest()
        {
            var users = new List<MediaSfuWhiteboardUser>();
            var participants = client?.CurrentRoom?.Participants ?? new List<MediaSfuParticipant>();
            foreach (var participant in participants)
            {
                if (participant == null || string.IsNullOrWhiteSpace(participant.DisplayName))
                {
                    continue;
                }

                users.Add(
                    new MediaSfuWhiteboardUser
                    {
                        Name = participant.DisplayName,
                        UseBoard = true,
                    });
            }

            if (users.Count == 0 && !string.IsNullOrWhiteSpace(displayName))
            {
                users.Add(
                    new MediaSfuWhiteboardUser
                    {
                        Name = displayName,
                        UseBoard = true,
                    });
            }

            return new MediaSfuWhiteboardSessionRequest
            {
                Users = users,
            };
        }

        private static MediaSfuWhiteboardShape BuildDefaultWhiteboardRectangle()
        {
            return new MediaSfuWhiteboardShape
            {
                Type = "rectangle",
                X1 = 10f,
                Y1 = 10f,
                X2 = 160f,
                Y2 = 96f,
                Color = "#1f8cff",
                Thickness = 4f,
                LineType = "solid",
            };
        }

        public void RunConfirmPresence(bool suppressFuturePrompts)
        {
            EnsureClient();
            client.ConfirmPresence(suppressFuturePrompts);
            UpdateStatus(
                suppressFuturePrompts
                    ? "Presence confirmed. Future confirm-here prompts are suppressed for this client instance."
                    : "Presence confirmed.");
        }

        private void OnDestroy()
        {
            if (client != null)
            {
                client.Dispose();
                client = null;
            }

            ReplaceOwnedWebRtcDevice(null);
            webRtcDevice = null;
            localMediaBackend = null;
            remoteMediaBridge = null;

            ownedTransportBackedLocalMediaBackend?.Dispose();
            ownedTransportBackedLocalMediaBackend = null;

            ownedTransportBackedRemoteMediaBridge?.Dispose();
            ownedTransportBackedRemoteMediaBridge = null;
        }

        private void RecreateClient()
        {
            if (client != null)
            {
                client.Dispose();
            }

            ClearInspectorSnapshot();

            client = new MediaSfuClient(
                new MediaSfuClientOptions
                {
                    ConnectionMode = connectionMode,
                    ConnectMediaSfu = connectMediaSfu,
                    BaseUrl = baseUrl,
                    LocalLink = localLink,
                    Credentials = new MediaSfuCredentials
                    {
                        ApiUserName = apiUserName,
                        ApiKey = apiKey
                    }
                });

            if (webRtcDevice != null)
            {
                client.AttachWebRtcDevice(webRtcDevice);
            }

            if (webRtcDevice == null && localMediaBackend != null)
            {
                client.AttachLocalMediaBackend(localMediaBackend);
            }

            if (webRtcDevice == null && remoteMediaBridge != null)
            {
                client.AttachRemoteMediaBridge(remoteMediaBridge);
            }

            client.ConnectionStateChanged += state =>
                UpdateStatus($"Connection state: {state.CurrentState}");

            client.RoomChanged += roomChanged =>
            {
                if (roomChanged.CurrentRoom != null)
                {
                    RefreshInspectorSnapshot(roomChanged.CurrentRoom);
                    UpdateStatus(BuildRoomStatus(roomChanged.CurrentRoom));
                }
                else
                {
                    ClearInspectorSnapshot();
                    UpdateStatus("Active room cleared.");
                }
            };

            client.ErrorOccurred += error =>
            {
                var message = string.IsNullOrWhiteSpace(error.Detail)
                    ? $"{error.Operation}: {error.Message}"
                    : $"{error.Operation}: {error.Message}\n{error.Detail}";
                errorRaised.Invoke(message);
                UpdateStatus(message);
            };

            client.MessageReceived += message =>
            {
                UpdateStatus($"Message from {message.Sender}: {message.Message}");
            };
        }

        private void AttachWebRtcDeviceCore(IMediaSfuWebRtcDevice device)
        {
            ownedTransportBackedLocalMediaBackend?.Dispose();
            ownedTransportBackedLocalMediaBackend = null;

            ownedTransportBackedRemoteMediaBridge?.Dispose();
            ownedTransportBackedRemoteMediaBridge = null;

            webRtcDevice = device;
            localMediaBackend = device;
            remoteMediaBridge = device;

            if (client != null)
            {
                client.AttachWebRtcDevice(device);
            }
        }

        private void ReplaceOwnedWebRtcDevice(IMediaSfuWebRtcDevice nextDevice)
        {
            if (!ReferenceEquals(ownedWebRtcDevice, nextDevice) && ownedWebRtcDevice != null)
            {
                ownedWebRtcDevice.Dispose();
            }

            ownedWebRtcDevice = nextDevice;
        }

        private void EnsureClient()
        {
            if (client == null)
            {
                RecreateClient();
            }
        }

        private void RefreshInspectorSnapshot(MediaSfuRoom room)
        {
            if (room == null)
            {
                ClearInspectorSnapshot();
                return;
            }

            roomSnapshotText = BuildRoomInspectorSnapshot(room);

            participantSummaries.Clear();
            if (room.Participants != null)
            {
                foreach (var participant in room.Participants)
                {
                    participantSummaries.Add(BuildParticipantSummary(participant));
                }
            }

            pendingRequestSummaries.Clear();
            if (room.PendingRequests != null)
            {
                foreach (var request in room.PendingRequests)
                {
                    pendingRequestSummaries.Add(BuildPendingRequestSummary(request));
                }
            }

            waitingParticipantSummaries.Clear();
            if (room.WaitingRoomParticipants != null)
            {
                foreach (var participant in room.WaitingRoomParticipants)
                {
                    waitingParticipantSummaries.Add(BuildWaitingParticipantSummary(participant));
                }
            }

            breakoutRoomSummaries.Clear();
            if (room.Breakout?.Rooms != null)
            {
                for (var roomIndex = 0; roomIndex < room.Breakout.Rooms.Count; roomIndex += 1)
                {
                    breakoutRoomSummaries.Add(BuildBreakoutRoomSummary(roomIndex, room.Breakout.Rooms[roomIndex]));
                }
            }

            consumingDomainSummaries.Clear();
            if (room.ConsumingDomains?.Domains != null)
            {
                foreach (var domain in room.ConsumingDomains.Domains)
                {
                    consumingDomainSummaries.Add(domain);
                }
            }

            selectedPendingRequestIndex = ClampSelectionIndex(selectedPendingRequestIndex, pendingRequestSummaries.Count);
            selectedWaitingParticipantIndex = ClampSelectionIndex(selectedWaitingParticipantIndex, waitingParticipantSummaries.Count);
            pendingRequestCount = pendingRequestSummaries.Count;
            waitingParticipantCount = waitingParticipantSummaries.Count;
            confirmPresencePending = room.ConfirmHereRequested;
            pollPromptVisible = room.PollModalVisible;
            whiteboardActive = room.Whiteboard != null && room.Whiteboard.Started && !room.Whiteboard.Ended;
            recordingActive = room.Recording != null && room.Recording.RecordStarted && !room.Recording.RecordStopped;
            recordingPaused = room.Recording != null && room.Recording.RecordPaused;
            recordingPauseResumeAvailable = room.Recording != null && room.Recording.CanPauseResume && room.Recording.RecordStarted && !room.Recording.RecordStopped;
            selectedPendingRequestText = pendingRequestSummaries.Count == 0
                ? "No pending request selected."
                : pendingRequestSummaries[selectedPendingRequestIndex];
            selectedWaitingParticipantText = waitingParticipantSummaries.Count == 0
                ? "No waiting-room participant selected."
                : waitingParticipantSummaries[selectedWaitingParticipantIndex];
            pollSnapshotText = BuildPollInspectorSnapshot(room.ActivePoll, room.PollModalVisible, room.LastPollStatus);
            whiteboardSnapshotText = BuildWhiteboardInspectorSnapshot(room.Whiteboard);
            participantSnapshotText = JoinSummaryLines(participantSummaries);
            breakoutSnapshotText = JoinSummaryLines(breakoutRoomSummaries);
            consumingDomainSnapshotText = JoinSummaryLines(consumingDomainSummaries);
            PublishSnapshotEvents();
        }

        private void ClearInspectorSnapshot()
        {
            roomSnapshotText = string.Empty;
            selectedPendingRequestText = string.Empty;
            selectedWaitingParticipantText = string.Empty;
            pollSnapshotText = string.Empty;
            whiteboardSnapshotText = string.Empty;
            participantSnapshotText = string.Empty;
            breakoutSnapshotText = string.Empty;
            consumingDomainSnapshotText = string.Empty;
            participantSummaries.Clear();
            pendingRequestSummaries.Clear();
            waitingParticipantSummaries.Clear();
            breakoutRoomSummaries.Clear();
            consumingDomainSummaries.Clear();
            selectedPendingRequestIndex = 0;
            selectedWaitingParticipantIndex = 0;
            pendingRequestCount = 0;
            waitingParticipantCount = 0;
            confirmPresencePending = false;
            pollPromptVisible = false;
            whiteboardActive = false;
            recordingActive = false;
            recordingPaused = false;
            recordingPauseResumeAvailable = false;
            PublishSnapshotEvents();
        }

        private void SelectPendingRequestIndex(int requestIndex)
        {
            selectedPendingRequestIndex = ClampSelectionIndex(requestIndex, pendingRequestSummaries.Count);
            selectedPendingRequestText = pendingRequestSummaries.Count == 0
                ? "No pending request selected."
                : pendingRequestSummaries[selectedPendingRequestIndex];
            pendingRequestSelectionChanged.Invoke(selectedPendingRequestText);
            selectedPendingRequestIndexChanged.Invoke(selectedPendingRequestIndex);
        }

        private void SelectWaitingParticipantIndex(int participantIndex)
        {
            selectedWaitingParticipantIndex = ClampSelectionIndex(participantIndex, waitingParticipantSummaries.Count);
            selectedWaitingParticipantText = waitingParticipantSummaries.Count == 0
                ? "No waiting-room participant selected."
                : waitingParticipantSummaries[selectedWaitingParticipantIndex];
            waitingParticipantSelectionChanged.Invoke(selectedWaitingParticipantText);
            selectedWaitingParticipantIndexChanged.Invoke(selectedWaitingParticipantIndex);
        }

        private void PublishSnapshotEvents()
        {
            roomSnapshotChanged.Invoke(roomSnapshotText);
            participantSnapshotChanged.Invoke(participantSnapshotText);
            pendingRequestSelectionChanged.Invoke(selectedPendingRequestText);
            waitingParticipantSelectionChanged.Invoke(selectedWaitingParticipantText);
            breakoutSnapshotChanged.Invoke(breakoutSnapshotText);
            consumingDomainsSnapshotChanged.Invoke(consumingDomainSnapshotText);
            pollSnapshotChanged.Invoke(pollSnapshotText);
            whiteboardSnapshotChanged.Invoke(whiteboardSnapshotText);
            pendingRequestCountChanged.Invoke(pendingRequestCount);
            waitingParticipantCountChanged.Invoke(waitingParticipantCount);
            selectedPendingRequestIndexChanged.Invoke(selectedPendingRequestIndex);
            selectedWaitingParticipantIndexChanged.Invoke(selectedWaitingParticipantIndex);
            confirmPresencePendingChanged.Invoke(confirmPresencePending);
            pollPromptVisibilityChanged.Invoke(pollPromptVisible);
            whiteboardActiveChanged.Invoke(whiteboardActive);
            recordingActiveChanged.Invoke(recordingActive);
            recordingPausedChanged.Invoke(recordingPaused);
            recordingPauseResumeAvailableChanged.Invoke(recordingPauseResumeAvailable);
        }

        private static string JoinSummaryLines(IReadOnlyList<string> summaries)
        {
            if (summaries == null || summaries.Count == 0)
            {
                return string.Empty;
            }

            return string.Join("\n", summaries);
        }

        private static int ClampSelectionIndex(int selectedIndex, int count)
        {
            if (count <= 0)
            {
                return 0;
            }

            if (selectedIndex < 0)
            {
                return 0;
            }

            if (selectedIndex >= count)
            {
                return count - 1;
            }

            return selectedIndex;
        }

        private static MediaSfuRoomRequest TryGetPendingRequest(MediaSfuRoom room, int requestIndex)
        {
            return room?.PendingRequests != null && requestIndex >= 0 && requestIndex < room.PendingRequests.Count
                ? room.PendingRequests[requestIndex]
                : null;
        }

        private static MediaSfuWaitingRoomParticipant TryGetWaitingParticipant(MediaSfuRoom room, int participantIndex)
        {
            return room?.WaitingRoomParticipants != null && participantIndex >= 0 && participantIndex < room.WaitingRoomParticipants.Count
                ? room.WaitingRoomParticipants[participantIndex]
                : null;
        }

        private void HandleResult(string operation, MediaSfuOperationResult<MediaSfuRoom> result)
        {
            if (result.Success && result.Value != null)
            {
                UpdateStatus($"{operation} succeeded for room {result.Value.RoomName}");
                return;
            }

            if (result.Status == MediaSfuOperationStatus.Deferred)
            {
                UpdateStatus($"{operation} deferred: {result.Detail}");
                return;
            }

            var message = string.IsNullOrWhiteSpace(result.Detail)
                ? $"{operation} failed: {result.Error}"
                : $"{operation} failed: {result.Error}\n{result.Detail}";
            errorRaised.Invoke(message);
            UpdateStatus(message);
        }

        private void HandleBoolResult(string operation, MediaSfuOperationResult<bool> result)
        {
            if (result.Success)
            {
                UpdateStatus($"{operation} succeeded.");
                return;
            }

            if (result.Status == MediaSfuOperationStatus.Deferred)
            {
                UpdateStatus($"{operation} deferred: {result.Detail}");
                return;
            }

            var message = string.IsNullOrWhiteSpace(result.Detail)
                ? $"{operation} failed: {result.Error}"
                : $"{operation} failed: {result.Error}\n{result.Detail}";
            errorRaised.Invoke(message);
            UpdateStatus(message);
        }

        private void UpdateStatus(string nextStatus)
        {
            statusText = nextStatus;
            statusChanged.Invoke(nextStatus);
        }

        private static string BuildRoomStatus(MediaSfuRoom room)
        {
            var confirmHereSuffix = room.ConfirmHereRequested ? " confirm-here=pending" : string.Empty;
            return
                $"Active room: {room.RoomName} participants={room.Participants.Count} requests={room.PendingRequests.Count} waiting={room.WaitingRoomParticipants.Count} moderation-total={room.PendingModerationCount}{confirmHereSuffix}{BuildWaitingSummary(room.LastWaitingParticipantName)}{BuildPollSummary(room.ActivePoll, room.PollModalVisible, room.LastPollStatus)}{BuildBreakoutSummary(room.Breakout)}{BuildScreenSummary(room.ScreenProducerId, room.ShareScreenStarted, room.DeferScreenReceived)}{BuildConsumingDomainsSummary(room.ConsumingDomains)}{BuildWhiteboardSummary(room.Whiteboard)}{BuildRecordingSummary(room.Recording)}{BuildLocalRequestSummary(room.LocalRequests, room.LastRequestResponse)}";
        }

        private static string BuildWaitingSummary(string lastWaitingParticipantName)
        {
            return string.IsNullOrWhiteSpace(lastWaitingParticipantName)
                ? string.Empty
                : $" waiting-notice={lastWaitingParticipantName}";
        }

        private static string BuildRoomInspectorSnapshot(MediaSfuRoom room)
        {
            var parts = new List<string>
            {
                $"room={room.RoomName}",
                $"participants={room.Participants?.Count ?? 0}",
                $"pending-requests={room.PendingRequests?.Count ?? 0}",
                $"waiting={room.WaitingRoomParticipants?.Count ?? 0}",
                $"moderation-total={room.PendingModerationCount}",
                $"confirm-here={(room.ConfirmHereRequested ? "pending" : "clear")}",
                $"host-restricted audio={room.HostRestrictedAudio} video={room.HostRestrictedVideo} screen={room.HostRestrictedScreenshare} chat={room.HostRestrictedChat}"
            };

            if (room.ConsumingDomains != null)
            {
                parts.Add($"consume-domains={room.ConsumingDomains.Domains?.Count ?? 0}");
            }

            if (room.Breakout != null)
            {
                parts.Add($"breakout-rooms={room.Breakout.Rooms?.Count ?? 0}");
            }

            return string.Join("\n", parts);
        }

        private static string BuildParticipantSummary(MediaSfuParticipant participant)
        {
            if (participant == null)
            {
                return string.Empty;
            }

            var localSuffix = participant.IsLocal ? " local" : string.Empty;
            return $"{participant.DisplayName} role={participant.Role}{localSuffix} audio={participant.AudioOn} video={participant.VideoOn} screen={participant.ScreenOn}";
        }

        private static string BuildPendingRequestSummary(MediaSfuRoomRequest request)
        {
            if (request == null)
            {
                return string.Empty;
            }

            return $"{request.DisplayName} type={NormalizeRequestLabel(request.Type)} action={request.Action} user={request.UserName}";
        }

        private static string BuildWaitingParticipantSummary(MediaSfuWaitingRoomParticipant participant)
        {
            if (participant == null)
            {
                return string.Empty;
            }

            return $"{participant.DisplayName} id={participant.ParticipantId}";
        }

        private static string BuildBreakoutRoomSummary(int roomIndex, IReadOnlyList<MediaSfuBreakoutParticipant> participants)
        {
            var names = new List<string>();
            if (participants != null)
            {
                foreach (var participant in participants)
                {
                    if (participant != null && !string.IsNullOrWhiteSpace(participant.DisplayName))
                    {
                        names.Add(participant.DisplayName);
                    }
                }
            }

            return names.Count == 0
                ? $"room {roomIndex}: empty"
                : $"room {roomIndex}: {string.Join(", ", names)}";
        }

        private static string BuildPollInspectorSnapshot(MediaSfuPoll activePoll, bool pollModalVisible, string lastPollStatus)
        {
            if (activePoll == null && !pollModalVisible && string.IsNullOrWhiteSpace(lastPollStatus))
            {
                return string.Empty;
            }

            var lines = new List<string>
            {
                $"status={lastPollStatus ?? activePoll?.Status ?? "updated"}",
                $"prompt-open={pollModalVisible}"
            };

            if (!string.IsNullOrWhiteSpace(activePoll?.Question))
            {
                lines.Add($"question={activePoll.Question}");
            }

            if (activePoll?.Options != null)
            {
                for (var index = 0; index < activePoll.Options.Count; index += 1)
                {
                    var option = activePoll.Options[index];
                    var voteCount = activePoll.Votes != null && index < activePoll.Votes.Count
                        ? activePoll.Votes[index]
                        : 0;
                    lines.Add($"option {index}: {option} votes={voteCount}");
                }
            }

            return string.Join("\n", lines);
        }

        private static string BuildWhiteboardInspectorSnapshot(MediaSfuWhiteboardState whiteboard)
        {
            if (whiteboard == null)
            {
                return string.Empty;
            }

            var lines = new List<string>
            {
                $"last-action={whiteboard.LastAction}",
                $"started={whiteboard.Started}",
                $"ended={whiteboard.Ended}",
                $"can-start={whiteboard.CanStart}",
                $"users={whiteboard.Users?.Count ?? 0}",
                $"shapes={whiteboard.Shapes?.Count ?? 0}",
                $"undo={whiteboard.UndoStack?.Count ?? 0}",
                $"redo={whiteboard.RedoStack?.Count ?? 0}",
                $"image-background={whiteboard.UseImageBackground}"
            };

            if (whiteboard.Users != null)
            {
                var previewUserCount = Math.Min(whiteboard.Users.Count, 3);
                for (var index = 0; index < previewUserCount; index += 1)
                {
                    var user = whiteboard.Users[index];
                    lines.Add($"user {index}: {user?.Name} active={user?.UseBoard}");
                }
            }

            if (whiteboard.Shapes != null)
            {
                var previewCount = Math.Min(whiteboard.Shapes.Count, 3);
                for (var index = 0; index < previewCount; index += 1)
                {
                    var shape = whiteboard.Shapes[index];
                    lines.Add($"shape {index}: {shape?.Type} color={shape?.Color} text={shape?.Text}");
                }
            }

            return string.Join("\n", lines);
        }

        private static string BuildPollSummary(MediaSfuPoll activePoll, bool pollModalVisible, string lastPollStatus)
        {
            if (activePoll == null && !pollModalVisible && string.IsNullOrWhiteSpace(lastPollStatus))
            {
                return string.Empty;
            }

            var status = string.IsNullOrWhiteSpace(lastPollStatus)
                ? activePoll?.Status
                : lastPollStatus;
            var question = activePoll?.Question;
            if (!string.IsNullOrWhiteSpace(question) && question.Length > 32)
            {
                question = question.Substring(0, 29) + "...";
            }

            var questionSuffix = string.IsNullOrWhiteSpace(question)
                ? string.Empty
                : $" question={question}";
            var promptSuffix = pollModalVisible ? " prompt=open" : string.Empty;
            return $" poll={status ?? "updated"}{promptSuffix}{questionSuffix}";
        }

        private static string BuildBreakoutSummary(MediaSfuBreakoutState breakout)
        {
            if (breakout == null)
            {
                return string.Empty;
            }

            var status = string.IsNullOrWhiteSpace(breakout.Status)
                ? breakout.Ended
                    ? "ended"
                    : breakout.Started
                        ? "started"
                        : "idle"
                : breakout.Status;
            var hostRoomSuffix = breakout.HostNewRoom >= 0
                ? $" host-room={breakout.HostNewRoom}"
                : string.Empty;
            return $" breakout={status} rooms={breakout.Rooms.Count}{hostRoomSuffix}";
        }

        private static string BuildScreenSummary(string screenProducerId, bool shareScreenStarted, bool deferScreenReceived)
        {
            if (!shareScreenStarted && !deferScreenReceived && string.IsNullOrWhiteSpace(screenProducerId))
            {
                return string.Empty;
            }

            var state = deferScreenReceived
                ? "deferred"
                : shareScreenStarted
                    ? "active"
                    : "pending";
            var producerSuffix = string.IsNullOrWhiteSpace(screenProducerId)
                ? string.Empty
                : $" producer={screenProducerId}";
            return $" screen-recv={state}{producerSuffix}";
        }

        private static string BuildConsumingDomainsSummary(MediaSfuConsumingDomainsState consumingDomains)
        {
            if (consumingDomains == null ||
                (consumingDomains.Domains == null || consumingDomains.Domains.Count == 0) &&
                !consumingDomains.HasAltDomains)
            {
                return string.Empty;
            }

            var altSuffix = consumingDomains.HasAltDomains ? " alt=true" : string.Empty;
            return $" consume-domains={consumingDomains.Domains.Count}{altSuffix}";
        }

        private static string BuildWhiteboardSummary(MediaSfuWhiteboardState whiteboard)
        {
            if (whiteboard == null && string.IsNullOrWhiteSpace(whiteboard?.LastAction))
            {
                return string.Empty;
            }

            if (whiteboard == null)
            {
                return string.Empty;
            }

            if ((whiteboard.Shapes == null || whiteboard.Shapes.Count == 0) &&
                (whiteboard.Users == null || whiteboard.Users.Count == 0) &&
                (whiteboard.RedoStack == null || whiteboard.RedoStack.Count == 0) &&
                (whiteboard.UndoStack == null || whiteboard.UndoStack.Count == 0) &&
                !whiteboard.Started &&
                !whiteboard.Ended &&
                !whiteboard.CanStart &&
                !whiteboard.UseImageBackground &&
                string.IsNullOrWhiteSpace(whiteboard.LastAction))
            {
                return string.Empty;
            }

            var action = string.IsNullOrWhiteSpace(whiteboard.LastAction)
                ? "updated"
                : whiteboard.LastAction;
            var state = whiteboard.Started && !whiteboard.Ended
                ? "active"
                : whiteboard.Ended
                    ? "ended"
                    : whiteboard.CanStart
                        ? "ready"
                        : "idle";
            var backgroundSuffix = whiteboard.UseImageBackground ? " bg=image" : string.Empty;
            var redoCount = whiteboard.RedoStack?.Count ?? 0;
            var undoCount = whiteboard.UndoStack?.Count ?? 0;
            var shapeCount = whiteboard.Shapes?.Count ?? 0;
            var userSuffix = (whiteboard.Users?.Count ?? 0) > 0 ? $" users={whiteboard.Users.Count}" : string.Empty;
            return $" whiteboard={action}:{state} shapes={shapeCount} undo={undoCount} redo={redoCount}{userSuffix}{backgroundSuffix}";
        }

        private static string BuildRecordingSummary(MediaSfuRecordingState recording)
        {
            if (recording == null)
            {
                return string.Empty;
            }

            if (!recording.RecordStarted &&
                !recording.RecordPaused &&
                !recording.RecordStopped &&
                !recording.TimeLeftSeconds.HasValue &&
                string.IsNullOrWhiteSpace(recording.LastNoticeState))
            {
                return string.Empty;
            }

            var lifecycle = recording.RecordPaused
                ? "paused"
                : recording.RecordStopped
                    ? "stopped"
                    : recording.RecordStarted
                        ? "active"
                        : "idle";
            var elapsedSuffix = recording.RecordElapsedTimeSeconds > 0
                ? $" elapsed={recording.ProgressTime}"
                : string.Empty;
            var timeLeftSuffix = recording.TimeLeftSeconds.HasValue
                ? $" time-left={recording.TimeLeftSeconds.Value}s"
                : string.Empty;
            return $" record={recording.State}:{lifecycle}{elapsedSuffix}{timeLeftSuffix}";
        }

        private static string BuildLocalRequestSummary(
            MediaSfuLocalRequestState localRequests,
            MediaSfuRequestResponse lastRequestResponse)
        {
            if (localRequests == null)
            {
                return string.Empty;
            }

            var summaryParts = new System.Collections.Generic.List<string>();
            AppendRequestSummary(summaryParts, "audio", localRequests.AudioRequestState);
            AppendRequestSummary(summaryParts, "video", localRequests.VideoRequestState);
            AppendRequestSummary(summaryParts, "screen", localRequests.ScreenshareRequestState);
            AppendRequestSummary(summaryParts, "chat", localRequests.ChatRequestState);

            var lastResponseSuffix = lastRequestResponse != null &&
                                     !string.IsNullOrWhiteSpace(lastRequestResponse.Action)
                ? $" last-response={NormalizeRequestLabel(lastRequestResponse.Type)}:{lastRequestResponse.Action}"
                : string.Empty;

            return summaryParts.Count == 0
                ? lastResponseSuffix
                : $" local-requests={string.Join(",", summaryParts)}{lastResponseSuffix}";
        }

        private static void AppendRequestSummary(
            System.Collections.Generic.ICollection<string> summaryParts,
            string label,
            string state)
        {
            if (summaryParts == null || string.IsNullOrWhiteSpace(label) || string.IsNullOrWhiteSpace(state) || state == "none")
            {
                return;
            }

            summaryParts.Add($"{label}:{state}");
        }

        private static string NormalizeRequestLabel(string rawType)
        {
            return rawType?.Trim().ToLowerInvariant() switch
            {
                "fa-microphone" => "audio",
                "fa-video" => "video",
                "fa-desktop" => "screen",
                "fa-comments" => "chat",
                _ => string.IsNullOrWhiteSpace(rawType) ? "request" : rawType.Trim().ToLowerInvariant()
            };
        }
    }
}