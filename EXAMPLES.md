# Example Applications

This repository includes several example applications demonstrating different usage patterns of the MediaSFU SDK.

> **Note:** These examples are provided as reference implementations and may require additional configuration to run in your environment.

## Sample Apps

### 1. sample-app
**Full-Featured Sample**

A comprehensive example showing all SDK capabilities:
- Create/Join rooms with full UI
- Video conferencing with grid layouts
- Screen sharing
- Chat and messaging
- Recording controls

```
./sample-app/
```

### 2. demo-app
**Minimal Integration Demo**

A lightweight example showing basic SDK integration:
- Simple room creation flow
- Minimal UI components
- Quick start template

```
./demo-app/
```

### 3. spacestek-app
**Audio Rooms (Twitter Spaces-like)**

Audio-only conferencing example:
- Audio-focused room experience
- Speaker/listener roles
- Raise hand functionality
- Minimalist audio UI

```
./spacestek-app/
```

## Configuration

Before running any example app:

1. **Network Security Config**: Update `src/main/res/xml/network_security_config.xml` with your server IP if using Community Edition
2. **Credentials**: Enter your MediaSFU API credentials in the app
3. **Local Development**: For local CE servers, add your IP to the network security config

## Running

```bash
# Build and run sample-app
./gradlew :sample-app:installDebug

# Build and run demo-app
./gradlew :demo-app:installDebug

# Build and run spacestek-app
./gradlew :spacestek-app:installDebug
```

## Using the SDK in Your App

These examples use the `shared` module directly. In your own app, add the SDK dependency:

```kotlin
dependencies {
    implementation("com.mediasfu:mediasfu-sdk-android:1.0.0")
}
```

See the main [README.md](README.md) for complete API documentation.
