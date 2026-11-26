# Timesyn War Engine

Android application project (v4.1).

## Project setup

- Android Gradle Plugin: 8.4.2
- Kotlin: 1.9.24
- Compile SDK: 34
- Minimum SDK: 26
- Target SDK: 34
- JDK: 17
- Gradle: 8.7

Open the repository in Android Studio with Android SDK 34 installed. The GitHub Actions workflow installs Gradle 8.7 and JDK 17 automatically.

## Run unit tests

```bash
gradle testDebugUnitTest
```

## Build debug APK

```bash
gradle assembleDebug
```

## Unit-test coverage

Eight local JVM tests cover the extracted `TimeSyncMath` helper:

- HTTP Date parsing (valid, missing, invalid)
- Server offset estimation, including negative RTT input
- Median selection for odd, even, and empty samples
- Ping lead clamping

The Android UI, accessibility service, overlay interactions, and live network requests are not covered by these JVM tests.

## Continuous integration

GitHub Actions runs `testDebugUnitTest` first, then `assembleDebug`, on pushes to `main`, `master`, or `setup/**`, and on pull requests targeting `main` or `master`. The generated debug APK is uploaded as the `TimesynWarEngine-Debug-APK` workflow artifact.
