# Synapse

A **Kotlin Multiplatform (KMP)** application built with **Compose Multiplatform (CMP)**, targeting **Android** and **Web (WebAssembly)** from a single shared codebase.

## Overview

Synapse demonstrates a modern multiplatform architecture where UI and business logic are shared across platforms using Jetpack Compose. The project leverages Kotlin Multiplatform to maximize code reuse while allowing platform-specific implementations where needed.

## Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| Kotlin | 2.1.0 | Language & multiplatform framework |
| Compose Multiplatform | 1.7.3 | Shared declarative UI toolkit |
| Gradle | 8.14 | Build system |
| Android Gradle Plugin | 8.7.3 | Android build tooling |
| Material3 | (via CMP) | Design system |
| Navigation Compose | 2.8.0-alpha11 | Multiplatform navigation |
| Lifecycle ViewModel | 2.8.4 | Multiplatform lifecycle-aware components |

## Project Structure

```
SynapseKT/
├── build.gradle.kts                        # Root build configuration
├── settings.gradle.kts                     # Project settings & repository config
├── gradle.properties                       # JVM args, Android & Kotlin settings
├── gradle/
│   ├── libs.versions.toml                  # Centralized version catalog
│   └── wrapper/                            # Gradle wrapper
└── composeApp/                             # Main application module
    ├── build.gradle.kts                    # Module build config (Android + WasmJs)
    └── src/
        ├── commonMain/                     # Shared code (all platforms)
        │   └── kotlin/in/arijitk/synapse/
        │       ├── App.kt                  # Root composable with shared UI
        │       └── Theme.kt               # Material3 theme (light & dark)
        ├── androidMain/                    # Android-specific code
        │   ├── AndroidManifest.xml
        │   └── kotlin/in/arijitk/synapse/
        │       ├── MainActivity.kt         # Android entry point
        │       └── Platform.android.kt     # Platform name provider
        └── wasmJsMain/                     # Web (Wasm) specific code
            ├── resources/
            │   └── index.html              # HTML shell for the web app
            └── kotlin/in/arijitk/synapse/
                ├── main.kt                 # Web entry point
                └── Platform.wasmJs.kt      # Platform name provider
```

### Source Sets

- **`commonMain`** -- Contains all shared UI components, themes, and business logic. This is where the majority of the application code lives. Uses `expect`/`actual` declarations for platform-specific functionality.
- **`androidMain`** -- Android-specific implementations including `MainActivity`, `AndroidManifest.xml`, and the `actual` platform declarations.
- **`wasmJsMain`** -- Web (WebAssembly) specific entry point and platform declarations. Uses Kotlin/Wasm to compile Compose UI to run natively in the browser.

## Prerequisites

- **JDK 17+** (JDK 21 recommended)
- **Android SDK** with:
  - Platform SDK 35
  - Build Tools 35.0.0+
- **Gradle 8.14** (included via wrapper)

## Getting Started

### Clone the repository

```bash
git clone <repository-url>
cd SynapseKT
```

### Build & Run Android

Build the debug APK:

```bash
./gradlew :composeApp:assembleDebug
```

The APK will be at `composeApp/build/outputs/apk/debug/composeApp-debug.apk`.

Install on a connected device/emulator:

```bash
./gradlew :composeApp:installDebug
```

### Build & Run Web (Wasm)

Start the development server with hot reload:

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

This opens the app in your default browser at `http://localhost:8080`.

For a production build:

```bash
./gradlew :composeApp:wasmJsBrowserProductionWebpack
```

Output is generated in `composeApp/build/dist/wasmJs/productionExecutable/`.

## Architecture

The project follows Compose Multiplatform conventions:

1. **Shared UI Layer** (`commonMain`) -- All composables, themes, and navigation are defined once and shared across all targets.
2. **Platform Binding** (`androidMain`, `wasmJsMain`) -- Each platform provides an entry point that bootstraps the shared `App()` composable:
   - Android: `MainActivity` uses `setContent { App() }`
   - Web: `ComposeViewport(document.body!!) { App() }`
3. **Expect/Actual** -- Platform-specific behavior (e.g., `getPlatformName()`) is declared with `expect` in common code and implemented with `actual` in each platform source set.

## Configuration

### Android

| Property | Value |
|---|---|
| `applicationId` | `in.arijitk.synapse` |
| `namespace` | `in.arijitk.synapse` |
| `minSdk` | 26 (Android 8.0) |
| `targetSdk` | 35 (Android 15) |
| `compileSdk` | 35 |
| `Java target` | 17 |

### Web (WasmJs)

| Property | Value |
|---|---|
| Module name | `synapse` |
| Output file | `synapse.js` |
| Target | Kotlin/Wasm (browser) |

## Useful Gradle Tasks

| Task | Description |
|---|---|
| `./gradlew :composeApp:assembleDebug` | Build Android debug APK |
| `./gradlew :composeApp:assembleRelease` | Build Android release APK |
| `./gradlew :composeApp:installDebug` | Install debug APK on device |
| `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` | Run web app (dev server) |
| `./gradlew :composeApp:wasmJsBrowserProductionWebpack` | Production web build |
| `./gradlew :composeApp:compileKotlinWasmJs` | Compile Wasm target only |
| `./gradlew :composeApp:compileDebugKotlinAndroid` | Compile Android target only |
| `./gradlew clean` | Clean all build outputs |

## License

All rights reserved.

## Author

**ARIJIT KUNDU** -- [arikundu9@gmail.com](mailto:arikundu9@gmail.com)
