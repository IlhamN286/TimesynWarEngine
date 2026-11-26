# Timesyn War Engine

Android project setup and unit-test groundwork.

## Current branch status

The `setup/unit-tests-ci` branch contains the Gradle configuration, extracted time-sync calculation helper, eight JUnit test cases, Android manifest/resources, and a manual GitHub Actions workflow.

**Important:** the original app source files `MainActivity.kt` and `AutoClickAccessibilityService.kt` are not yet present in this branch. The project is therefore not yet a complete buildable app, and the tests/build have not been run. The workflow is manual-only until those source files are added, to avoid triggering a known-incomplete build.

## Requirements

- Android Studio with Android SDK 34
- JDK 17
- Gradle 8.7

## Run unit tests

```bash
gradle testDebugUnitTest
```

## Build debug APK

```bash
gradle assembleDebug
```

## Added test coverage

`TimeSyncMathTest` includes eight test methods covering HTTP Date parsing, invalid/missing headers, offset estimation, negative RTT handling, median selection (odd/even/empty), and ping-lead clamping.

Network calls and Android accessibility-service behavior are not covered by these local JVM tests.
