# AGENTS.md

## Project Overview
TaskManager is an Android task manager app inspired by GNOME System Monitor. It requires Shizuku or root access to function fully. The app monitors CPU, RAM, network, processes, and provides process management capabilities.

## Technologies
- **Language**: Kotlin 2.3.21, C++20
- **Build**: Gradle 9.4.0 with version catalogs (libs.versions.toml)
- **UI**: Jetpack Compose (Material 3), ViewBinding
- **Architecture**: Multi-module Gradle project
- **Native**: CMake 3.22.1, NDK 28.0.13004108
- **Dependency Injection**: Manual (ViewModel, KSP for Room)
- **Testing**: JUnit 4, Espresso, Baseline Profile benchmarks

## Modules
- `:app` - Application module (com.rk.taskmanager.app)
- `:main` - Main app logic and UI (com.rk.taskmanager)
- `:components` - Reusable Compose components
- `:bridge` - Bridge layer between app and native daemon
- `:taskmanagerd` - Native C++ daemon for process/system monitoring
- `:baselineprofile` - Startup benchmark profiles
- `:taskmanager_pro` - Optional pro module (conditional include)

## Useful Commands
- `./gradlew assembleRelease` - Build signed release APK
- `./gradlew assembleDebug` - Build debug APK
- `./gradlew test` - Run unit tests
- `./gradlew connectedAndroidTest` - Run instrumentation tests
- `./gradlew lint` - Run Android Lint
- `./gradlew :baselineprofile:generateBaselineProfile` - Generate baseline profile
- `python generate_prebuilt_db.py` - Regenerate apps.db from uad_lists.json

## Best Practices
- Use official Kotlin style (kotlin.code.style=official)
- Configuration cache is enabled - avoid task graph modifications
- NDK version is pinned for reproducible builds
- Use `libs.versions.toml` for all dependency declarations
- Follow Material 3 design guidelines for Compose UI