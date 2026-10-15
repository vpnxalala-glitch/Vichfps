# Vich Optimizer

A native Android device-monitoring and maintenance utility built with Kotlin and Jetpack Compose.

## Current MVP
- Dark animated dashboard.
- Internal storage usage and available space.
- System RAM usage and available RAM.
- Battery percentage and battery temperature when exposed by the device.
- Shortcuts to Android storage, app management, and battery-saver settings.
- Manual refresh; no files are deleted automatically.

## Important Android limitations
Regular apps cannot freely clear another app's private cache or force-stop arbitrary background apps. This app uses supported system screens rather than claiming to perform privileged actions. Future cleanup tools should request user selection/confirmation and only remove files the user explicitly chooses.

## Build
GitHub Actions workflow: `.github/workflows/build-apk.yml`
The workflow builds a debug APK and uploads it as an artifact named `vich-optimizer-debug`.

- JDK 17
- Gradle 8.9
- Android SDK 35
- Minimum Android version: 8.0 (API 26)

## Project status
Initial functional scaffold. A successful APK build has not yet been confirmed; check the Actions tab before installing.
