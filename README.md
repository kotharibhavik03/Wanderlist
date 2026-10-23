# Wanderlist

A travel journal for Android and iOS, built from one Kotlin Multiplatform codebase with Compose Multiplatform. Save the places you've been to or want to visit, group them into collections, attach photos, and share individual places with the people you went with.

> **Status:** work in progress. Sign-in, collections, places, sharing and the Google Map work, with data in Cloud Firestore. The location picker and photo uploads come next.

## Features

| | Status |
|---|---|
| Google sign-in (Android + iOS) | Done |
| Collections with an emoji icon and multi-colour gradient | Done |
| Places: name, notes, coordinates, address, Wishlist / Visited, visit date, 1–5 rating | Done |
| Collection page with places and an "All photos" grid | Done |
| Edit, move between collections, and delete places; delete collections | Done |
| Google Maps on Home with collection-styled markers, filtered by collection | Done |
| Marker bottom sheet; search anywhere (free phone search) and save what you find | Done |
| Location picker: search, then move the map under a centre pin; address looked up for the exact spot | Done |
| Photos per place, with private photos visible only to their uploader | Planned |
| Share a place by email as Collaborator or "Visited with"; "Shared with me"; leave a place | Done |
| Directions via Google Maps / Apple Maps | Done |

## Tech stack

- **Kotlin Multiplatform** + **Compose Multiplatform** (shared UI for Android and iOS)
- **Clean Architecture** (domain / data / views) with **MVVM**: `StateFlow` UI state, `Channel` for one-shot events
- **Navigation 3** with a serializable `Route` back stack
- **Koin** for dependency injection
- **Firebase Auth** via **KMPAuth** (Google sign-in) and **Cloud Firestore** via the GitLive Firebase Kotlin SDK, with security rules in `firestore.rules`
- **Google Maps** on both platforms via [kmp-maps-compose](https://github.com/yankeppey/kmp-maps-compose) (Compose markers on Android and iOS)
- Coroutines and kotlinx.serialization

## Project layout

```
shared/      Shared Kotlin code: domain, data, navigation, and all Compose UI
androidApp/  Android entry point
iosApp/      iOS entry point (Xcode project, Swift Package Manager dependencies)
```

## Running locally

The Firebase config files are not committed. Create your own Firebase project with Google sign-in enabled, then add:

- `androidApp/google-services.json`
- `iosApp/GoogleService-Info.plist`

and replace the Google OAuth client IDs with your project's: the web client ID in `shared/src/commonMain/kotlin/com/rizz/wanderlist/config/AuthConfig.kt`, and `GIDClientID` plus the reversed-client-ID URL scheme in `iosApp/iosApp/Info.plist`.

Create a Firestore database in the same project (production mode), then deploy the security rules in `firestore.rules`, either by pasting them into Firebase console → Firestore → Rules, or with the [Firebase CLI](https://firebase.google.com/docs/cli):

```sh
firebase deploy --only firestore:rules --project <your-project-id>
```

For the map, enable **Maps SDK for Android** and **Maps SDK for iOS** in the same Google Cloud project and create one API key per platform, each restricted to its app (Android: package name + SHA-1; iOS: bundle ID) and its Maps SDK. Keep the keys out of Git:

- Android: `local.properties` → `MAPS_API_KEY=<android key>`
- iOS: create `iosApp/Configuration/Secrets.xcconfig` → `MAPS_API_KEY = <ios key>`

Then:

- **Android:** `./gradlew :androidApp:assembleDebug`, or run `androidApp` from Android Studio.
- **iOS:** open `iosApp/iosApp.xcodeproj` in Xcode and run.

Requirements: JDK 17+, Android Studio, Xcode (iOS 16+ deployment target).
