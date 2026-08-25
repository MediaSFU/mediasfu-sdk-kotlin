# MediaSFU Unity basic room flow

This sample shows how to create or join a MediaSFU room from a Unity scene,
connect the realtime session, bind a WebRTC media device, observe participants,
send chat, control local media, moderate requests, manage recording, and leave
cleanly.

## Credential safety

A Unity player is a client application and cannot keep an embedded MediaSFU API
key secret. For a distributed application, authenticate the player with your
backend, create or join the room there, and return only the room-scoped response
the client needs.

Direct inspector credentials are an optional local-development shortcut. Use
restricted, revocable development credentials, keep the scene and local config
uncommitted, and remove the values before building or sharing a player.

- [Secure backend proxy](https://mediasfu.com/docs/usage/secure-backend-proxy/)
- [MediaSFU API Sandbox](https://mediasfu.com/sandbox/)
- [MediaSFU Open](https://github.com/MediaSFU/MediaSFUOpen) — a media server you
  deploy and operate yourself

## Add the sample to a scene

1. Install `com.mediasfu.unity` and the companion
   `com.mediasfu.mediasoup-client-unity` package.
2. Add `Runtime/MediaSfuBasicRoomFlow.cs` to a GameObject.
3. Configure Cloud or self-hosted connection settings.
4. Bind UI controls to create or join, connect media, microphone, camera, screen
   share, chat, recording, moderation, and leave actions.
5. Bind status and room events to visible UI so users can distinguish connecting,
   ready, denied, disconnected, and ended states.

An empty `baseUrl` uses MediaSFU Cloud. Set it only when targeting an existing
MediaSFU Open or custom backend that the player can reach.

## Choose a media device

The simplest integration attaches one `IMediaSfuWebRtcDevice` that owns both
publication and playback:

```csharp
roomFlow.AttachTransportBackedWebRtcDevice(localAdapter, remoteAdapter);
```

If your project keeps capture and playback separate, attach an
`IMediaSfuLocalMediaBackend` and `IMediaSfuRemoteMediaBridge`. The native-plugin
path can be attached with `AttachNativePluginWebRtcEngine(...)` when the
matching platform binary is installed.

`MediaSfuNativePluginRemoteVideoView` follows a selected remote camera or screen
track and uploads decoded BGRA frames to a Unity `Texture2D`. Screen content
should use a contain-style presentation; camera tiles may use cover-style
cropping. Never mirror remote video or screen share.

## Room actions exposed by the sample

- Create or join and connect the media session.
- Send chat and receive participant/room updates.
- Enable or disable microphone, camera, and screen share.
- Approve or reject participant media requests.
- Admit or deny waiting-room participants.
- Start, pause, resume, and stop recording.
- Observe polls, breakout rooms, whiteboard state, moderation counts, and
  forced-exit events.
- Leave the room and release app-owned media and listeners.

Use the public events instead of parsing status strings. Keep the scene UI
responsive while commands are pending and surface SDK errors in user-friendly
language.

## Minimal client wiring

```csharp
var client = new MediaSfuClient(optionsFromYourAuthorityBoundary);

client.ConnectionStateChanged += state => UpdateConnectionLabel(state);
client.ErrorOccurred += error => ShowRecoverableError(error);

var join = await client.JoinRoomAsync(new MediaSfuJoinRoomRequest
{
    MeetingId = roomName,
    UserName = displayName,
    IsLevel = "0"
});

if (join.Success)
{
    await client.ConnectMediaAsync();
}
```

`optionsFromYourAuthorityBoundary` should contain the room authority returned
by your backend in a released application. Do not log credentials, room tokens,
or invitation capabilities.

## Observable acceptance

Before distributing a player, verify the intended Unity Editor and player
targets with two independent participants:

- both participants reach a ready room state;
- microphone and camera are produced and consumed in both directions;
- remote audio is audible and survives video-layout changes;
- screen share becomes primary and camera presentation is restored afterward;
- chat, moderation, recording, and forced-exit behavior match your product;
- leave and end release tracks, renderers, listeners, and temporary authority;
  and
- logs, screenshots, builds, and source maps contain no reusable credentials.

The bootstrap scene is intentionally small so a product can supply its own UI.
Use the SDK events and commands to build accessible participant grids,
moderation tools, whiteboard, recording, and recovery experiences.
