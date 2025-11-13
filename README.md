# Edge Detection R&D

## Overview
Edge Detection R&D is an experimental Android application that explores real-time edge detection directly on-device using the Camera2 API, a native OpenCV processing pipeline, and an OpenGL ES renderer. A complementary TypeScript web viewer allows quick verification of processed frames in the browser. The project demonstrates a full capture→process→render workflow, highlighting how modern Android, NDK, and web technologies can collaborate in a research environment.

## Tech Stack
- **Android (Kotlin)** – Application scaffolding, Camera2 integration, and UI layer.
- **Android NDK (CMake)** – Native interoperability layer that drives OpenCV and exposes JNI bindings.
- **OpenCV** – Performs real-time edge detection (grayscale, Gaussian blur, Canny).
- **OpenGL ES 2.0** – Renders processed frames via a GLSurfaceView-backed shader pipeline.
- **TypeScript** – Lightweight web viewer for inspecting processed output samples.

## Folder Structure
```
.
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/minimalnativeapp/
│   │   │   │   ├── Camera2Manager.kt
│   │   │   │   ├── CameraGLView.kt
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── NativeBridge.kt
│   │   │   │   └── SimpleRenderer.kt
│   │   │   ├── cpp/
│   │   │   │   ├── CMakeLists.txt
│   │   │   │   └── native-lib.cpp
│   │   │   └── res/
│   │   └── ...
├── gradle/
├── web/
│   │   │   │   │   │   ├── camera/Camera2Manager.kt
│   │   │   │   │   │   ├── gl/CameraGLView.kt
│   │   │   │   │   │   ├── ui/MainActivity.kt
│   │   │   │   │   │   ├── nativebridge/NativeBridge.kt
│   │   │   │   │   │   └── SimpleRenderer.kt
│   ├── package.json
│   └── tsconfig.json
└── README.md
```

## Build Instructions (Android CLI)
1. Open a terminal at the project root.
2. Ensure the environment variable `ANDROID_SDK_ROOT` points to a valid Android SDK.
3. Configure `local.properties` with the SDK path (e.g., `sdk.dir=C:\\Android\\SDK`).
4. Place the OpenCV Android SDK as described below.
5. Run a debug build:  
   ```powershell
   .\gradlew.bat assembleDebug
   ```
6. The APK is generated under `app/build/outputs/apk/debug/`.

## OpenCV Android SDK Placement
1. Download the OpenCV Android SDK (version aligned with your needs) from https://opencv.org/releases/.
2. Extract the archive.
3. Copy the `OpenCV-android-sdk/sdk/native` directory into the project so it matches:
   ```
   app/src/main/cpp/opencv/sdk/native/jni/OpenCVConfig.cmake
   ```
4. Confirm `CMakeLists.txt` references `OpenCV_DIR="${CMAKE_SOURCE_DIR}/opencv/sdk/native/jni"`.

## Running the Android App
1. Connect an Android device (API level ≥ 24) or launch an emulator with camera support.
2. Install the debug APK using Gradle:
   ```powershell
   .\gradlew.bat installDebug
   ```
3. Launch “MinimalNativeApp” on the device.
4. Grant camera and storage permissions when prompted.
5. The app begins live preview processing on resume; a `TextureView` drives the capture surface while the processed overlay renders via OpenGL. Leaving the app pauses the camera.

## Capturing a Processed Frame
1. With the app running, observe the processed edge-detected preview.
2. Tap **“Capture Frame”**.
3. Toggle **Show Raw** / **Show Edges** to compare the live feed with the processed overlay.
4. The latest processed RGBA frame is saved as `processed.png` in the device’s `Download/` directory, and a toast confirms the save path once the media scanner indexes it for gallery access.

## Running the Web Viewer
1. Navigate to the `web/` directory:  
   ```powershell
   cd web
   ```
2. Install dependencies:  
   ```powershell
   npm install
   ```
3. Build TypeScript sources:  
   ```powershell
   npm run build
   ```
4. Open `web/src/index.html` in a browser. The viewer loads `static/sample_processed.txt`, displays the embedded base64 PNG, and surfaces frame statistics (resolution plus load timestamp).

## Camera → JNI → GL Pipeline
1. **Capture (Camera2Manager)** – Couples a `TextureView`-supplied `SurfaceTexture` with an `ImageReader`, streams `YUV_420_888`, converts frames to RGBA, and emits them via `FrameListener`.
2. **Processing (NativeBridge + native-lib.cpp)** – Kotlin hands frames to JNI; C++ uses OpenCV to grayscale, blur, and run Canny edge detection, returning an RGBA buffer.
3. **Rendering (CameraGLView + SimpleRenderer)** – Raw and processed buffers update OpenGL textures on a dedicated thread, with a UI toggle switching between feeds.
4. **Capture Button** – The processed frame cache converts to a bitmap on demand and saves to storage in PNG format.

## Setup Screenshots
_Add screenshots that illustrate each stage of the pipeline (camera preview, processed output, saved image in gallery, and web viewer) once available. Suggested filenames:_
- `docs/screenshots/device-preview.png`
- `docs/screenshots/device-saved.png`
- `docs/screenshots/web-viewer.png`

## Commit Strategy
- **Feature branches** per major addition (e.g., `feature/camera-pipeline`, `feature/web-viewer`).
- **Small, focused commits** with descriptive messages (e.g., “Add Camera2 manager with YUV→RGBA conversion”).
- **Code review cadence** via pull requests before merging into `main`.
- **Version tags** for significant milestones or demo-ready states.
- **Continuous integration checks** (future enhancement) for Gradle builds and TypeScript compilation.

---
For questions or contributions, please open an issue or submit a pull request.
