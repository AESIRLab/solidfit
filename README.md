# SolidFit

An Android app for logging workouts, with the data stored in the user's own [Solid](https://solidproject.org/) pod instead of a vendor's server.

## Features

- Sign in with a Solid WebID through the Solid Credential Manager app, with a browser OIDC flow as fallback
- Log workouts, attach photos or videos, and track active sessions
- Read and write exercise, heart rate, steps, calories and weight through Health Connect
- Live heart rate from Bluetooth LE monitors
- Heart-rate monitoring screen, plus a summary view
- Workout reminders and push sync through UnifiedPush

Workouts are cached locally in Room and synced to the pod.

## Requirements

- Android Studio with JDK 17
- A device or emulator on Android 11 (API 30) or later, with Health Connect available
- A Solid pod and WebID
- A GitHub personal access token with `read:packages`, to pull the AESIR Lab libraries (`authlib`, `sksolidannotations`) from GitHub Packages

## Building

Add your GitHub Packages credentials to `local.properties`:

```properties
gpr.user=your-github-username
gpr.key=your-token
```

Then build and install:

```sh
./gradlew installDebug
```

Run the unit tests with:

```sh
./gradlew test
```
