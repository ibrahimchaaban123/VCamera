# VCamera — Virtual Camera for Android 16

Replace your device camera with any image, video, or network stream.

## Features

- 📷 **Image Source** — use any photo as your camera
- 🎬 **Video Source** — loop any local video file
- 🌐 **Network Stream** — RTMP, HLS, HTTP streams
- 🔍 **Zoom** — pinch/slider zoom in real-time
- 🔄 **Rotate** — 90° increments
- ↔️ **Flip** — horizontal and vertical
- 🛡️ **Privacy** — block apps from accessing real camera
- 🔔 **Background Service** — works during calls

## Requirements

| Tool | Version |
|------|---------|
| Android Studio | Hedgehog 2023.1.1+ |
| JDK | 17 |
| compileSdk | 36 (Android 16) |
| minSdk | 26 (Android 8) |
| Gradle | 8.4 |

## Setup

```bash
# 1. Clone this repo
git clone https://github.com/YOUR_USERNAME/VCamera.git
cd VCamera

# 2. Open in Android Studio
# File → Open → select VCamera folder

# 3. Let Gradle sync complete

# 4. Build → Make Project

# 5. Run on device (Android 8+)
```

## Architecture

```
VCamera/
├── app/src/main/java/com/vcamera/app/
│   ├── ui/
│   │   ├── MainActivity.kt          # Main screen + controls
│   │   ├── MainViewModel.kt         # State management
│   │   ├── PreviewActivity.kt       # Live camera preview
│   │   └── SourcePickerActivity.kt  # Media picker
│   ├── service/
│   │   └── VirtualCameraService.kt  # Foreground service
│   ├── camera/
│   │   ├── CameraStreamManager.kt   # Source → frames pipeline
│   │   └── FrameProcessor.kt        # OpenGL ES transforms
│   └── model/
│       ├── VideoSource.kt           # Source types (Image/Video/Stream)
│       └── CameraTransform.kt       # Zoom/Rotate/Flip params
```

## How It Works

1. **VirtualCameraService** runs in the foreground
2. **CameraStreamManager** reads frames from the chosen source (image/video/stream)
3. **FrameProcessor** (OpenGL ES 2.0) applies zoom, rotation, and flip in real-time
4. The processed surface is exposed as the virtual camera output

## To Extend

- Add **face detection**: integrate ML Kit in `FrameProcessor`
- Add **beauty filter**: add GLSL shader effects
- Add **green screen**: chroma key in the fragment shader
- Add **virtual backgrounds**: blend two textures in GLSL

## License

AGPL-3.0 — see LICENSE
