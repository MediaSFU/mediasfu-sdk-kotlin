# Changelog

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
