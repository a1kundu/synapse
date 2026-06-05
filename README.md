# Synapse

A **Kotlin Multiplatform (KMP)** application built with **Compose Multiplatform (CMP)**, targeting **Android** and **Web (WebAssembly)** from a single shared codebase.

## Overview

Synapse is a multiplatform application skeleton demonstrating:
- Shared UI with Compose Multiplatform
- Adaptive theming with Material You / Dynamic Color support
- GitHub Releases-based self-update mechanism for Android
- CI/CD pipeline with automated APK builds and releases
- Platform-specific implementations via expect/actual pattern

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
| Ktor Client | 3.0.3 | HTTP client (update checks) |
| Kotlinx Serialization | 1.7.3 | JSON parsing |

## Project Structure

```
SynapseKT/
├── .github/
│   └── workflows/
│       └── build-apk.yml                  # CI/CD: Build & Release APK
├── build.gradle.kts                        # Root build configuration
├── settings.gradle.kts                     # Project settings & repository config
├── gradle.properties                       # JVM args, Android & Kotlin settings
├── gradle/
│   ├── libs.versions.toml                  # Centralized version catalog
│   └── wrapper/                            # Gradle wrapper
└── composeApp/                             # Main application module
    ├── build.gradle.kts                    # Module build config (Android + WasmJs)
    └── src/
        ├── commonMain/kotlin/in/arijitk/synapse/
        │   ├── App.kt                      # Root composable + navigation graph
        │   ├── Platform.kt                 # expect declarations (platform ID)
        │   ├── Version.kt                  # App version (stamped by CI)
        │   ├── navigation/
        │   │   └── Routes.kt              # Navigation route definitions
        │   ├── theme/
        │   │   ├── SynapseTheme.kt        # Adaptive theme (light/dark/dynamic)
        │   │   └── AppColors.kt           # Color palette definitions
        │   ├── settings/
        │   │   └── SettingsRepository.kt  # Settings persistence (expect/actual)
        │   ├── update/
        │   │   └── UpdateService.kt       # Update models + expect service
        │   └── ui/
        │       ├── home/
        │       │   └── HomeShell.kt       # Bottom nav shell (Teams + Search tabs)
        │       └── settings/
        │           └── SettingsScreen.kt  # Full settings UI
        ├── androidMain/
        │   ├── AndroidManifest.xml         # Permissions + FileProvider
        │   ├── res/xml/file_paths.xml      # FileProvider paths for APK install
        │   └── kotlin/in/arijitk/synapse/
        │       ├── MainActivity.kt         # Android entry point
        │       ├── Platform.android.kt     # actual: platform identification
        │       ├── theme/
        │       │   └── DynamicColor.android.kt  # Material You dynamic colors
        │       ├── settings/
        │       │   └── PlatformPreferences.android.kt  # SharedPreferences
        │       └── update/
        │           └── UpdateService.android.kt  # GitHub download + APK install
        └── wasmJsMain/
            ├── resources/
            │   └── index.html              # HTML shell for the web app
            └── kotlin/in/arijitk/synapse/
                ├── main.kt                 # Web entry point
                ├── Platform.wasmJs.kt      # actual: platform identification
                ├── theme/
                │   └── DynamicColor.wasmJs.kt  # No dynamic color on web
                ├── settings/
                │   └── PlatformPreferences.wasmJs.kt  # localStorage
                └── update/
                    └── UpdateService.wasmJs.kt  # No-op (web always fresh)
```

## Features

### Home Shell
- Bottom navigation bar with swipeable pages (HorizontalPager)
- Two tabs: **Teams** and **Search** (placeholder content for business logic)
- Top app bar with settings navigation

### Settings
- **App Info** -- Version display, build channel (debug/release)
- **Appearance** -- Theme mode (System/Light/Dark), Dynamic color toggle
- **Updates** (Android only) -- Auto-update toggle, manual check, download progress with speed/ETA, install
- **GitHub** -- Links to source code, issues, releases

### Adaptive Theming
- **Material You** -- Uses Android 12+ dynamic color schemes from wallpaper
- **Fallback** -- Deep Purple seed-based Material3 color scheme
- **Dark mode** -- Full dark theme support with system/manual toggle
- **Persistence** -- Theme preferences stored across app restarts

### Update Mechanism (Android)
- Checks GitHub Releases API for newer versions
- Channel-based filtering (debug/release builds check their own channel)
- Version comparison via build number from git commit count
- HTTP Range resume support for interrupted downloads
- APK installation via FileProvider (Android 7+)
- Cancel/retry support with progress reporting

### CI/CD Pipeline
- Triggered on push to `main` or manual dispatch
- Version stamping from git metadata (`{commitCount}.0.0-{shortSha}`)
- Keystore decoding from GitHub secrets for release signing
- Auto-generated changelog from git log
- Creates GitHub Release with tagged APK

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

```bash
./gradlew :composeApp:assembleDebug
./gradlew :composeApp:installDebug
```

### Build & Run Web (Wasm)

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

Opens at `http://localhost:8080`.

### Production Builds

```bash
# Android release APK
./gradlew :composeApp:assembleRelease

# Web production bundle
./gradlew :composeApp:wasmJsBrowserProductionWebpack
```

## CI/CD Setup

To enable the GitHub Actions pipeline, configure these repository secrets:

| Secret | Description |
|---|---|
| `KEYSTORE_BASE64` | Base64-encoded release keystore (.jks) |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Signing key alias |
| `KEY_PASSWORD` | Signing key password |

The pipeline auto-creates GitHub Releases with tagged APKs on every push to `main`.

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

## Extending the Skeleton

To add business logic:
1. Add new screens under `ui/` in `commonMain`
2. Register routes in `navigation/Routes.kt`
3. Add NavHost entries in `App.kt`
4. Use `expect`/`actual` for platform-specific behavior
5. Add dependencies to `gradle/libs.versions.toml`

## License

All rights reserved.

## Author

**ARIJIT KUNDU** -- [arikundu9@gmail.com](mailto:arikundu9@gmail.com)
