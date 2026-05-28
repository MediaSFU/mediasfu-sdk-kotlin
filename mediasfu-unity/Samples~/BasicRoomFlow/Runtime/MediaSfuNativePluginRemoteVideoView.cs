using System;
using UnityEngine;

namespace MediaSFU.Unity.Samples
{
    [AddComponentMenu("MediaSFU/Native Plugin Remote Video View")]
    public sealed class MediaSfuNativePluginRemoteVideoView : MonoBehaviour
    {
        [SerializeField] private MediaSfuBasicRoomFlow roomFlow;
        [SerializeField] private Renderer targetRenderer;
        [SerializeField] private bool autoFollowRemoteVideoTrack = true;
        [SerializeField] private bool includeScreenShareTracks = true;
        [SerializeField] private string preferredParticipantId = string.Empty;
        [SerializeField] private string remoteProducerId = string.Empty;

        private MediaSfuClient subscribedClient;
        private Texture2D videoTexture;
        private byte[] frameBuffer;
        private long latestFrameSequence = -1;

        public Texture Texture => videoTexture;

        public string RemoteProducerId => remoteProducerId;

        private void Reset()
        {
            roomFlow = GetComponent<MediaSfuBasicRoomFlow>();
            targetRenderer = GetComponent<Renderer>();
        }

        private void OnEnable()
        {
            EnsureClientSubscription();
        }

        private void Update()
        {
            EnsureClientSubscription();
            TryRefreshFrame();
        }

        private void OnDisable()
        {
            ReplaceClientSubscription(null);
        }

        private void OnDestroy()
        {
            ReplaceClientSubscription(null);
            DestroyTexture();
        }

        public void SetRemoteProducerId(string producerId)
        {
            remoteProducerId = producerId ?? string.Empty;
            latestFrameSequence = -1;
        }

        public bool TryRefreshFrame()
        {
            if (roomFlow == null)
            {
                return false;
            }

            var engine = roomFlow.NativePluginWebRtcEngine;
            if (engine == null || string.IsNullOrWhiteSpace(remoteProducerId))
            {
                return false;
            }

            MediaSfuNativePluginRemoteVideoFrameInfo frameInfo;
            try
            {
                frameInfo = engine.GetRemoteVideoFrameInfo(remoteProducerId);
            }
            catch (Exception error)
            {
                return DisableWithWarning($"Failed to query MediaSFU remote video frame metadata: {error.Message}");
            }

            if (frameInfo == null ||
                !frameInfo.Available ||
                frameInfo.ByteCount <= 0 ||
                frameInfo.Width <= 0 ||
                frameInfo.Height <= 0)
            {
                return false;
            }

            if (videoTexture != null && frameInfo.Sequence == latestFrameSequence)
            {
                return false;
            }

            EnsureTexture(frameInfo.Width, frameInfo.Height, frameInfo.ByteCount);

            int copiedByteCount;
            try
            {
                copiedByteCount = engine.CopyRemoteVideoFrame(remoteProducerId, frameBuffer);
            }
            catch (Exception error)
            {
                return DisableWithWarning($"Failed to copy MediaSFU remote video frame bytes: {error.Message}");
            }

            if (copiedByteCount != frameBuffer.Length)
            {
                return false;
            }

            videoTexture.LoadRawTextureData(frameBuffer);
            videoTexture.Apply(updateMipmaps: false, makeNoLongerReadable: false);
            latestFrameSequence = frameInfo.Sequence;

            if (targetRenderer != null)
            {
                targetRenderer.material.mainTexture = videoTexture;
            }

            return true;
        }

        private void EnsureClientSubscription()
        {
            var nextClient = roomFlow == null ? null : roomFlow.Client;
            if (!ReferenceEquals(subscribedClient, nextClient))
            {
                ReplaceClientSubscription(nextClient);
            }
        }

        private void ReplaceClientSubscription(MediaSfuClient nextClient)
        {
            if (subscribedClient != null)
            {
                subscribedClient.TrackAdded -= HandleTrackAdded;
                subscribedClient.TrackRemoved -= HandleTrackRemoved;
            }

            subscribedClient = nextClient;

            if (subscribedClient != null)
            {
                subscribedClient.TrackAdded += HandleTrackAdded;
                subscribedClient.TrackRemoved += HandleTrackRemoved;
            }
        }

        private void HandleTrackAdded(MediaSfuTrackEvent trackEvent)
        {
            if (!autoFollowRemoteVideoTrack || trackEvent?.Track == null)
            {
                return;
            }

            var track = trackEvent.Track;
            if (!track.IsRemote)
            {
                return;
            }

            var isSupportedTrack = track.Kind == MediaSfuTrackKind.Video ||
                (includeScreenShareTracks && track.Kind == MediaSfuTrackKind.Screen);
            if (!isSupportedTrack)
            {
                return;
            }

            if (!string.IsNullOrWhiteSpace(preferredParticipantId) &&
                !string.Equals(track.ParticipantId, preferredParticipantId, StringComparison.Ordinal))
            {
                return;
            }

            remoteProducerId = track.TrackId ?? string.Empty;
            latestFrameSequence = -1;
        }

        private void HandleTrackRemoved(MediaSfuTrackEvent trackEvent)
        {
            if (trackEvent?.Track == null)
            {
                return;
            }

            if (!string.Equals(trackEvent.Track.TrackId, remoteProducerId, StringComparison.Ordinal))
            {
                return;
            }

            remoteProducerId = string.Empty;
            latestFrameSequence = -1;

            if (targetRenderer != null)
            {
                targetRenderer.material.mainTexture = null;
            }
        }

        private void EnsureTexture(int width, int height, int byteCount)
        {
            if (frameBuffer == null || frameBuffer.Length != byteCount)
            {
                frameBuffer = new byte[byteCount];
            }

            if (videoTexture != null && videoTexture.width == width && videoTexture.height == height)
            {
                return;
            }

            DestroyTexture();
            videoTexture = new Texture2D(width, height, TextureFormat.BGRA32, mipChain: false, linear: false)
            {
                wrapMode = TextureWrapMode.Clamp,
                filterMode = FilterMode.Bilinear,
            };
        }

        private void DestroyTexture()
        {
            if (videoTexture == null)
            {
                return;
            }

            if (targetRenderer != null && targetRenderer.material.mainTexture == videoTexture)
            {
                targetRenderer.material.mainTexture = null;
            }

            if (Application.isPlaying)
            {
                Destroy(videoTexture);
            }
            else
            {
                DestroyImmediate(videoTexture);
            }

            videoTexture = null;
        }

        private bool DisableWithWarning(string message)
        {
            Debug.LogWarning(message, this);
            enabled = false;
            return false;
        }
    }
}