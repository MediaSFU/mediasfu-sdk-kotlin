using System;
using System.Collections.Generic;
using System.Net.Security;
using System.Security.Cryptography.X509Certificates;

namespace MediaSFU.Unity
{
    public delegate bool MediaSfuServerCertificateValidationCallback(
        Uri requestUri,
        X509Certificate certificate,
        X509Chain chain,
        SslPolicyErrors sslPolicyErrors);

    public enum MediaSfuConnectionMode
    {
        Cloud,
        CommunityEdition,
        Hybrid
    }

    public enum MediaSfuSessionAction
    {
        Create,
        Join
    }

    public enum MediaSfuEventType
    {
        Conference,
        Webinar,
        Chat,
        Broadcast,
        None
    }

    public enum MediaSfuConnectionState
    {
        Idle,
        CreatingRoom,
        JoiningRoom,
        ValidatingRoom,
        ConnectingSocket,
        Connected,
        InRoom,
        Reconnecting,
        Disconnected,
        Failed
    }

    public enum MediaSfuParticipantRole
    {
        Host,
        CoHost,
        Participant,
        Viewer
    }

    public enum MediaSfuTrackKind
    {
        Audio,
        Video,
        Screen,
        Whiteboard
    }

    public enum MediaSfuHostControlType
    {
        Audio,
        Video,
        ScreenShare,
        Chat,
        All
    }

    public enum MediaSfuSafeRoomAction
    {
        Warn,
        Kick,
        Hold
    }

    public enum MediaSfuBufferType
    {
        All,
        Audio,
        Video
    }

    public enum MediaSfuOperationStatus
    {
        Success,
        Failure,
        Deferred
    }

    [Serializable]
    public sealed class MediaSfuClientOptions
    {
        public MediaSfuConnectionMode ConnectionMode { get; set; } = MediaSfuConnectionMode.Cloud;

        public MediaSfuCredentials Credentials { get; set; } = new MediaSfuCredentials();

        public string BaseUrl { get; set; } = string.Empty;

        public string LocalLink { get; set; } = string.Empty;

        public bool ConnectMediaSfu { get; set; } = true;

        public bool AutoReconnect { get; set; } = true;

        public int SocketConnectTimeoutMs { get; set; } = 15000;

        public int SocketAckTimeoutMs { get; set; } = 10000;

        public MediaSfuServerCertificateValidationCallback ServerCertificateValidationCallback { get; set; }
    }

    [Serializable]
    public sealed class MediaSfuCredentials
    {
        public string ApiUserName { get; set; } = string.Empty;

        public string ApiKey { get; set; } = string.Empty;
    }

    [Serializable]
    public sealed class MediaSfuCreateRoomRequest
    {
        public string UserName { get; set; } = "tester";

        public int DurationMinutes { get; set; } = 60;

        public int Capacity { get; set; } = 100;

        public long? ScheduledDateEpochMillis { get; set; }

        public string SecureCode { get; set; } = string.Empty;

        public MediaSfuEventType EventType { get; set; } = MediaSfuEventType.Conference;

        public string RoomName { get; set; } = string.Empty;

        public string AdminPasscode { get; set; } = string.Empty;

        public string IsLevel { get; set; } = string.Empty;

        public MediaSfuMeetingRoomParameters MeetingRoomParameters { get; set; }

        public MediaSfuRecordingParameters RecordingParameters { get; set; }

        public bool RecordOnly { get; set; }

        public bool SafeRoom { get; set; }

        public bool AutoStartSafeRoom { get; set; }

        public MediaSfuSafeRoomAction SafeRoomAction { get; set; } = MediaSfuSafeRoomAction.Kick;

        public bool DataBuffer { get; set; }

        public MediaSfuBufferType BufferType { get; set; } = MediaSfuBufferType.All;

        public bool SupportSip { get; set; }

        public string DirectionSip { get; set; } = string.Empty;

        public bool PreferPcma { get; set; }

        public bool SupportTranslation { get; set; }

        public string TranslationConfigNickName { get; set; } = string.Empty;

        public bool SupportFlexRoom { get; set; }

        public bool SupportMaxRoom { get; set; }
    }

    [Serializable]
    public sealed class MediaSfuJoinRoomRequest
    {
        public string MeetingId { get; set; } = string.Empty;

        public string UserName { get; set; } = "tester";

        public string AdminPasscode { get; set; } = string.Empty;

        public string IsLevel { get; set; } = "0";
    }

    [Serializable]
    public sealed class MediaSfuMeetingRoomParameters
    {
        public int ItemPageLimit { get; set; }

        public string MediaType { get; set; } = string.Empty;

        public bool AddCoHost { get; set; }

        public string TargetOrientation { get; set; } = string.Empty;

        public string TargetOrientationHost { get; set; } = string.Empty;

        public string TargetResolution { get; set; } = string.Empty;

        public string TargetResolutionHost { get; set; } = string.Empty;

        public string Type { get; set; } = string.Empty;

        public string AudioSetting { get; set; } = string.Empty;

        public string VideoSetting { get; set; } = string.Empty;

        public string ScreenshareSetting { get; set; } = string.Empty;

        public string ChatSetting { get; set; } = string.Empty;
    }

    [Serializable]
    public sealed class MediaSfuRecordingParameters
    {
        public int RecordingAudioPausesLimit { get; set; }

        public bool RecordingAudioSupport { get; set; }

        public int RecordingAudioPeopleLimit { get; set; }

        public int RecordingAudioParticipantsTimeLimit { get; set; }

        public int RecordingVideoPausesLimit { get; set; }

        public bool RecordingVideoSupport { get; set; }

        public int RecordingVideoPeopleLimit { get; set; }

        public int RecordingVideoParticipantsTimeLimit { get; set; }

        public bool RecordingAllParticipantsSupport { get; set; }

        public bool RecordingVideoParticipantsSupport { get; set; }

        public bool RecordingAllParticipantsFullRoomSupport { get; set; }

        public bool RecordingVideoParticipantsFullRoomSupport { get; set; }

        public string RecordingPreferredOrientation { get; set; } = string.Empty;

        public bool RecordingSupportForOtherOrientation { get; set; }

        public bool RecordingMultiFormatsSupport { get; set; }

        public bool RecordingHlsSupport { get; set; }

        public int? RecordingAudioPausesCount { get; set; }

        public int? RecordingVideoPausesCount { get; set; }
    }

    [Serializable]
    public sealed class MediaSfuPoll
    {
        public string PollId { get; set; } = string.Empty;

        public string Question { get; set; } = string.Empty;

        public string Type { get; set; } = string.Empty;

        public List<string> Options { get; set; } = new List<string>();

        public List<int> Votes { get; set; } = new List<int>();

        public string Status { get; set; } = string.Empty;

        public Dictionary<string, int> Voters { get; set; } = new Dictionary<string, int>();
    }

    [Serializable]
    public sealed class MediaSfuPollCreateRequest
    {
        public string Question { get; set; } = string.Empty;

        public string Type { get; set; } = "singleChoice";

        public List<string> Options { get; set; } = new List<string>();
    }

    [Serializable]
    public sealed class MediaSfuPollVoteRequest
    {
        public string PollId { get; set; } = string.Empty;

        public int OptionIndex { get; set; }
    }

    [Serializable]
    public sealed class MediaSfuBreakoutParticipant
    {
        public string DisplayName { get; set; } = string.Empty;

        public int? BreakRoom { get; set; }
    }

    [Serializable]
    public sealed class MediaSfuBreakoutRoomsRequest
    {
        public List<List<MediaSfuBreakoutParticipant>> Rooms { get; set; } = new List<List<MediaSfuBreakoutParticipant>>();

        public string NewParticipantAction { get; set; } = "autoAssignNewRoom";
    }

    [Serializable]
    public sealed class MediaSfuBreakoutState
    {
        public List<List<MediaSfuBreakoutParticipant>> Rooms { get; set; } = new List<List<MediaSfuBreakoutParticipant>>();

        public bool Started { get; set; }

        public bool Ended { get; set; }

        public int HostNewRoom { get; set; } = -1;

        public string Status { get; set; } = string.Empty;
    }

    [Serializable]
    public sealed class MediaSfuConsumingDomainsState
    {
        public List<string> Domains { get; set; } = new List<string>();

        public bool HasAltDomains { get; set; }

        public string AltDomainsJson { get; set; } = string.Empty;
    }

    [Serializable]
    public sealed class MediaSfuWhiteboardPoint
    {
        public float X { get; set; }

        public float Y { get; set; }
    }

    [Serializable]
    public sealed class MediaSfuWhiteboardShape
    {
        public string Type { get; set; } = string.Empty;

        public float? X { get; set; }

        public float? Y { get; set; }

        public float? X1 { get; set; }

        public float? Y1 { get; set; }

        public float? X2 { get; set; }

        public float? Y2 { get; set; }

        public List<MediaSfuWhiteboardPoint> Points { get; set; } = new List<MediaSfuWhiteboardPoint>();

        public string Color { get; set; } = string.Empty;

        public float Thickness { get; set; } = 6f;

        public string LineType { get; set; } = string.Empty;

        public string Text { get; set; } = string.Empty;

        public string FontFamily { get; set; } = string.Empty;

        public float FontSize { get; set; } = 20f;

        public string ImageSrc { get; set; } = string.Empty;

        public string Signature { get; set; } = string.Empty;
    }

    [Serializable]
    public sealed class MediaSfuWhiteboardUser
    {
        public string Name { get; set; } = string.Empty;

        public bool UseBoard { get; set; }
    }

    [Serializable]
    public sealed class MediaSfuWhiteboardSessionRequest
    {
        public List<MediaSfuWhiteboardUser> Users { get; set; } = new List<MediaSfuWhiteboardUser>();
    }

    [Serializable]
    public sealed class MediaSfuWhiteboardState
    {
        public string LastAction { get; set; } = string.Empty;

        public bool Started { get; set; }

        public bool Ended { get; set; }

        public bool CanStart { get; set; }

        public bool UseImageBackground { get; set; }

        public List<MediaSfuWhiteboardUser> Users { get; set; } = new List<MediaSfuWhiteboardUser>();

        public List<MediaSfuWhiteboardShape> Shapes { get; set; } = new List<MediaSfuWhiteboardShape>();

        public List<List<MediaSfuWhiteboardShape>> RedoStack { get; set; } = new List<List<MediaSfuWhiteboardShape>>();

        public List<List<MediaSfuWhiteboardShape>> UndoStack { get; set; } = new List<List<MediaSfuWhiteboardShape>>();
    }

    [Serializable]
    public sealed class MediaSfuRoom
    {
        public string RoomName { get; set; } = string.Empty;

        public string PublicUrl { get; set; } = string.Empty;

        public string Link { get; set; } = string.Empty;

        public string Secret { get; set; } = string.Empty;

        public string SecureCode { get; set; } = string.Empty;

        public string ApiUserName { get; set; } = string.Empty;

        public string CoHost { get; set; } = string.Empty;

        public List<MediaSfuCoHostResponsibility> CoHostResponsibilities { get; set; } = new List<MediaSfuCoHostResponsibility>();

        public List<MediaSfuRoomRequest> PendingRequests { get; set; } = new List<MediaSfuRoomRequest>();

        public List<MediaSfuWaitingRoomParticipant> WaitingRoomParticipants { get; set; } = new List<MediaSfuWaitingRoomParticipant>();

        public int PendingModerationCount { get; set; }

        public string LastWaitingParticipantName { get; set; } = string.Empty;

        public string AudioSetting { get; set; } = string.Empty;

        public string VideoSetting { get; set; } = string.Empty;

        public string ScreenshareSetting { get; set; } = string.Empty;

        public string ChatSetting { get; set; } = string.Empty;

        public bool HostRestrictedAudio { get; set; }

        public bool HostRestrictedVideo { get; set; }

        public bool HostRestrictedScreenshare { get; set; }

        public bool HostRestrictedChat { get; set; }

        public bool ConfirmHereRequested { get; set; }

        public int? MeetingTimeRemainingMs { get; set; }

        public bool AdminRestrictSetting { get; set; }

        public MediaSfuRecordingParameters RecordingParameters { get; set; } = new MediaSfuRecordingParameters();

        public MediaSfuRecordingState Recording { get; set; } = new MediaSfuRecordingState();

        public MediaSfuLocalRequestState LocalRequests { get; set; } = new MediaSfuLocalRequestState();

        public MediaSfuRequestResponse LastRequestResponse { get; set; }

        public List<MediaSfuPoll> Polls { get; set; } = new List<MediaSfuPoll>();

        public MediaSfuPoll ActivePoll { get; set; }

        public bool PollModalVisible { get; set; }

        public string LastPollStatus { get; set; } = string.Empty;

        public MediaSfuBreakoutState Breakout { get; set; } = new MediaSfuBreakoutState();

        public bool MembersReceived { get; set; }

        public string ScreenProducerId { get; set; } = string.Empty;

        public bool ShareScreenStarted { get; set; }

        public bool DeferScreenReceived { get; set; }

        public MediaSfuConsumingDomainsState ConsumingDomains { get; set; } = new MediaSfuConsumingDomainsState();

        public MediaSfuWhiteboardState Whiteboard { get; set; } = new MediaSfuWhiteboardState();

        public List<MediaSfuParticipant> Participants { get; set; } = new List<MediaSfuParticipant>();
    }

    [Serializable]
    public sealed class MediaSfuSocketConnectionPlan
    {
        public string BaseUrl { get; set; } = string.Empty;

        public string Namespace { get; set; } = "/";

        public string RoomName { get; set; } = string.Empty;

        public int ConnectTimeoutMs { get; set; }

        public int AckTimeoutMs { get; set; }

        public bool UsesSecureTransport { get; set; }
    }

    [Serializable]
    public sealed class MediaSfuSocketHandshake
    {
        public string TransportUrl { get; set; } = string.Empty;

        public string SessionId { get; set; } = string.Empty;

        public List<string> Upgrades { get; set; } = new List<string>();

        public int PingIntervalMs { get; set; }

        public int PingTimeoutMs { get; set; }

        public int MaxPayload { get; set; }
    }

    [Serializable]
    public sealed class MediaSfuRoomValidation
    {
        public bool HasRtpCapabilities { get; set; }

        public string RtpCapabilitiesJson { get; set; } = string.Empty;

        public List<string> RoomRecvIps { get; set; } = new List<string>();

        public string SecureCode { get; set; } = string.Empty;

        public bool RecordOnly { get; set; }

        public bool IsHost { get; set; }

        public bool SafeRoom { get; set; }

        public bool AutoStartSafeRoom { get; set; }

        public bool SafeRoomStarted { get; set; }

        public bool SafeRoomEnded { get; set; }

        public string Reason { get; set; } = string.Empty;

        public bool Banned { get; set; }

        public bool Suspended { get; set; }

        public bool NoAdmin { get; set; }

        public string RawPayload { get; set; } = string.Empty;
    }

    [Serializable]
    public sealed class MediaSfuSocketAck
    {
        public string Namespace { get; set; } = "/";

        public int AckId { get; set; }

        public string PayloadJson { get; set; } = string.Empty;

        public string RawPayload { get; set; } = string.Empty;
    }

    [Serializable]
    public sealed class MediaSfuSocketEvent
    {
        public string Namespace { get; set; } = "/";

        public string EventName { get; set; } = string.Empty;

        public int? AckId { get; set; }

        public string PayloadJson { get; set; } = string.Empty;

        public string RawPayload { get; set; } = string.Empty;
    }

    [Serializable]
    public sealed class MediaSfuChatMessage
    {
        public string Sender { get; set; } = string.Empty;

        public List<string> Receivers { get; set; } = new List<string>();

        public string Message { get; set; } = string.Empty;

        public string Timestamp { get; set; } = string.Empty;

        public bool Group { get; set; }

        public string RawPayload { get; set; } = string.Empty;
    }

    [Serializable]
    public sealed class MediaSfuRemoteProducerEvent
    {
        public string ProducerId { get; set; } = string.Empty;

        public string IsLevel { get; set; } = string.Empty;

        public bool IsPipeProducer { get; set; }

        public string TranslationMetaJson { get; set; } = string.Empty;

        public string RawPayload { get; set; } = string.Empty;
    }

    [Serializable]
    public sealed class MediaSfuRemoteProducerClosedEvent
    {
        public string RemoteProducerId { get; set; } = string.Empty;

        public string RawPayload { get; set; } = string.Empty;
    }

    [Serializable]
    public sealed class MediaSfuParticipant
    {
        public string ParticipantId { get; set; } = string.Empty;

        public string DisplayName { get; set; } = string.Empty;

        public MediaSfuParticipantRole Role { get; set; } = MediaSfuParticipantRole.Participant;

        public bool IsLocal { get; set; }

        public bool AudioOn { get; set; }

        public bool VideoOn { get; set; }

        public bool ScreenOn { get; set; }

        public string AudioTrackId { get; set; } = string.Empty;

        public string VideoTrackId { get; set; } = string.Empty;

        public string ScreenTrackId { get; set; } = string.Empty;
    }

    [Serializable]
    public sealed class MediaSfuRoomRequest
    {
        public string RequestId { get; set; } = string.Empty;

        public string Icon { get; set; } = string.Empty;

        public string DisplayName { get; set; } = string.Empty;

        public string UserName { get; set; } = string.Empty;

        public string RawPayload { get; set; } = string.Empty;
    }

    [Serializable]
    public sealed class MediaSfuWaitingRoomParticipant
    {
        public string ParticipantId { get; set; } = string.Empty;

        public string DisplayName { get; set; } = string.Empty;

        public string RawPayload { get; set; } = string.Empty;
    }

    [Serializable]
    public sealed class MediaSfuRequestResponse
    {
        public string RequestId { get; set; } = string.Empty;

        public string Icon { get; set; } = string.Empty;

        public string DisplayName { get; set; } = string.Empty;

        public string UserName { get; set; } = string.Empty;

        public string Action { get; set; } = string.Empty;

        public string Type { get; set; } = string.Empty;

        public string RawPayload { get; set; } = string.Empty;
    }

    [Serializable]
    public sealed class MediaSfuLocalRequestState
    {
        public string AudioRequestState { get; set; } = "none";

        public string VideoRequestState { get; set; } = "none";

        public string ScreenshareRequestState { get; set; } = "none";

        public string ChatRequestState { get; set; } = "none";

        public long? AudioRequestRetryAtEpochMs { get; set; }

        public long? VideoRequestRetryAtEpochMs { get; set; }

        public long? ScreenshareRequestRetryAtEpochMs { get; set; }

        public long? ChatRequestRetryAtEpochMs { get; set; }

        public bool AudioActionGranted { get; set; }

        public bool VideoActionGranted { get; set; }

        public bool ScreenshareActionGranted { get; set; }

        public bool ChatActionGranted { get; set; }
    }

    [Serializable]
    public sealed class MediaSfuRecordingMainSpecs
    {
        public string MediaOptions { get; set; } = "video";

        public string AudioOptions { get; set; } = "all";

        public string VideoOptions { get; set; } = "all";

        public string VideoType { get; set; } = "fullDisplay";

        public bool VideoOptimized { get; set; }

        public string RecordingDisplayType { get; set; } = "media";

        public bool AddHls { get; set; }
    }

    [Serializable]
    public sealed class MediaSfuRecordingDisplaySpecs
    {
        public bool NameTags { get; set; } = true;

        public string BackgroundColor { get; set; } = "#000000";

        public string NameTagsColor { get; set; } = "#ffffff";

        public string OrientationVideo { get; set; } = "landscape";
    }

    [Serializable]
    public sealed class MediaSfuRecordingTextSpecs
    {
        public bool AddText { get; set; }

        public string CustomText { get; set; } = string.Empty;

        public string CustomTextPosition { get; set; } = string.Empty;

        public string CustomTextColor { get; set; } = string.Empty;
    }

    [Serializable]
    public sealed class MediaSfuUserRecordingParams
    {
        public MediaSfuRecordingMainSpecs MainSpecs { get; set; } = new MediaSfuRecordingMainSpecs();

        public MediaSfuRecordingDisplaySpecs DisplaySpecs { get; set; } = new MediaSfuRecordingDisplaySpecs();

        public MediaSfuRecordingTextSpecs TextSpecs { get; set; } = new MediaSfuRecordingTextSpecs();

        public string RawPayload { get; set; } = string.Empty;
    }

    [Serializable]
    public sealed class MediaSfuRecordingState
    {
        public string State { get; set; } = "green";

        public string LastNoticeState { get; set; } = string.Empty;

        public string LastStopReason { get; set; } = string.Empty;

        public string ProgressTime { get; set; } = "00:00:00";

        public int RecordElapsedTimeSeconds { get; set; }

        public long? RecordStartTimeEpochMs { get; set; }

        public int PauseCount { get; set; }

        public int? TimeLeftSeconds { get; set; }

        public bool RecordStarted { get; set; }

        public bool RecordPaused { get; set; }

        public bool RecordStopped { get; set; }

        public bool CanLaunchRecord { get; set; }

        public bool CanPauseResume { get; set; }

        public bool ShowRecordButtons { get; set; }

        public bool IsTimerRunning { get; set; }

        public MediaSfuUserRecordingParams UserRecordingParams { get; set; } = new MediaSfuUserRecordingParams();
    }

    [Serializable]
    public sealed class MediaSfuCoHostResponsibility
    {
        public string Name { get; set; } = string.Empty;

        public bool Value { get; set; }

        public bool Dedicated { get; set; }
    }

    [Serializable]
    public sealed class MediaSfuTrack
    {
        public string TrackId { get; set; } = string.Empty;

        public string ParticipantId { get; set; } = string.Empty;

        public MediaSfuTrackKind Kind { get; set; }

        public bool IsRemote { get; set; }

        public bool IsMuted { get; set; }
    }

    [Serializable]
    public sealed class MediaSfuOperationResult<T>
    {
        private MediaSfuOperationResult(MediaSfuOperationStatus status, T value, string error, string detail)
        {
            Status = status;
            Value = value;
            Error = error;
            Detail = detail;
        }

        public MediaSfuOperationStatus Status { get; }

        public T Value { get; }

        public string Error { get; }

        public string Detail { get; }

        public bool Success => Status == MediaSfuOperationStatus.Success;

        public static MediaSfuOperationResult<T> FromSuccess(T value)
        {
            return new MediaSfuOperationResult<T>(MediaSfuOperationStatus.Success, value, string.Empty, string.Empty);
        }

        public static MediaSfuOperationResult<T> FromSuccess(T value, string detail)
        {
            return new MediaSfuOperationResult<T>(MediaSfuOperationStatus.Success, value, string.Empty, detail ?? string.Empty);
        }

        public static MediaSfuOperationResult<T> FromFailure(string error, string detail = "")
        {
            return new MediaSfuOperationResult<T>(MediaSfuOperationStatus.Failure, default(T), error ?? string.Empty, detail ?? string.Empty);
        }

        public static MediaSfuOperationResult<T> FromDeferredContract(string detail)
        {
            return new MediaSfuOperationResult<T>(MediaSfuOperationStatus.Deferred, default(T), string.Empty, detail ?? string.Empty);
        }
    }

    [Serializable]
    public sealed class MediaSfuConnectionStateChangedEvent
    {
        public MediaSfuConnectionState PreviousState { get; set; }

        public MediaSfuConnectionState CurrentState { get; set; }
    }

    [Serializable]
    public sealed class MediaSfuRoomChangedEvent
    {
        public MediaSfuRoom PreviousRoom { get; set; }

        public MediaSfuRoom CurrentRoom { get; set; }
    }

    [Serializable]
    public sealed class MediaSfuParticipantEvent
    {
        public string RoomName { get; set; } = string.Empty;

        public MediaSfuParticipant Participant { get; set; }
    }

    [Serializable]
    public sealed class MediaSfuTrackEvent
    {
        public string RoomName { get; set; } = string.Empty;

        public MediaSfuParticipant Participant { get; set; }

        public MediaSfuTrack Track { get; set; }
    }

    [Serializable]
    public sealed class MediaSfuErrorEvent
    {
        public string Operation { get; set; } = string.Empty;

        public string Message { get; set; } = string.Empty;

        public string Detail { get; set; } = string.Empty;
    }
}