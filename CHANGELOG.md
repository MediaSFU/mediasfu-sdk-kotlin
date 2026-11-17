# Changelog

## 1.0.11

- Add a TURN relay fallback for networks that block direct media ports. When the media server offers a relay, a transport that stays unconnected for the server-chosen wait restarts ICE once with relay servers, and reports the outcome to the server.
- In the server's `always` relay mode, create send and receive transports with the offered relay servers.
- If no path connects, tell the user once per media connection: "Your network is blocking the call connection. Try another network (a phone hotspot works) or turn off your VPN, then rejoin."
- Servers that do not offer a relay behave as before, apart from that message.
- Require `com.mediasfu:mediasoup-client` 1.0.9, which accepts TURN credentials when ICE servers are updated on a live transport.

## 1.0.10

- Allow an explicit microphone retry to recreate a missing producer or terminal send transport instead of resuming stale paused state. Preserve normal mute/resume behavior, transient disconnections and host restrictions.
- Discover stock translation voices from the joined room's configured provider and output language, with bounded requests and safe empty/error handling.
- Select returned voices in the Advanced picker, including neutral voices. Room, language, connection and configuration changes clear stale selections; saving submits the selected voice ID and provider.
- Add the optional voice-aware callback to classic event settings while retaining the existing translation callback.
- Fix the room-join coroutine's access to the owning state options when applying simulcast and capacity settings.
- Add catalogue, socket, cancellation and Compose context-change regression coverage, plus Kotlin usage and Apple validation guides.

## 1.0.7

- Prevent concurrent `connectIps` calls from creating duplicate consume sockets by serializing the endpoint check, connection, room join, and state update sequence.
- Updated the Android, Kotlin Multiplatform, and usage examples to reference `1.0.7`.

## 1.0.6

- Previous release.
