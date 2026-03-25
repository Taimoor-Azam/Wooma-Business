# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew assembleDebug        # Build debug APK
./gradlew assembleRelease      # Build release APK
./gradlew build                # Full build (debug + release)
./gradlew clean                # Clean build output
./gradlew test                 # Run unit tests
./gradlew connectedAndroidTest # Run instrumented tests (requires device/emulator)
```

## Project Overview

**Wooma Business** is a native Android app for business property inventory management. Built with Kotlin, targeting Android 7.0+ (minSdk 24, targetSdk 35), using View Binding throughout.

- **Application ID**: `com.wooma.business`
- **Build system**: Gradle 8.10.2 with AGP 8.8.2, Java 11 compatibility

## Architecture

Activity-based architecture (no ViewModel/MVVM pattern). All business logic lives directly in Activities.

```
app/src/main/java/com/wooma/business/
├── activities/         # All UI — grouped by feature (auth/, property/, report/)
│   ├── BaseActivity    # Common window inset handling; all activities extend this
│   └── MainActivity    # Main dashboard post-login
├── adapter/            # 24+ RecyclerView adapters for all list views
├── model/              # Data classes for API requests/responses; ApiResponse<T> wraps all API calls
├── data/network/       # MyApi.kt (Retrofit interface) + RetrofitClient.kt (Bearer token auto-injected)
├── storage/Prefs.kt    # SharedPreferences wrapper — persists user/auth data
└── customs/            # Utils, GenericPagingSource (Paging 3), custom dialogs
```

### Key Patterns

- **API layer**: Retrofit with Gson. All responses use `ApiResponse<T> { success, message, errors, data }`. The access token stored in `Prefs` is automatically injected as a Bearer token via an OkHttp interceptor in `RetrofitClient`.
- **Pagination**: `GenericPagingSource<T>` is a reusable Paging 3 source used across listings (reports, templates, properties).
- **View Binding**: Enabled globally — use `ActivityXxxBinding` / `ItemXxxBinding` generated classes, not `findViewById`.
- **Responsive sizing**: Use `sdp` (scalable dp) and `ssp` (scalable sp) resource dimensions instead of raw dp/sp values for layout sizing.
- **Image loading**: Glide for all image loading/display.

### Auth Flow

`SplashActivity` → `GetStartedActivity` → `LoginActivity` → `OTPActivity` → `ActivateAccountActivity` (if needed) → `MainActivity`

### Report Flow

Reports are central to the app. Key activities under `activities/report/`:
- `ReportListingActivity` — lists reports per property
- `CompleteReportActivity` — main report editing hub; manages rooms, items, meters, detectors, keys, checklists
- `CameraActivity` — in-app camera capture for inventory photos
- `otherItems/` — dedicated activities for meters, detectors, keys, checklists
- `inventorysettings/` — configure report type, dates, assessors, tenant info

### Dependencies Reference

- Networking: `retrofit 2.7.1`, `okhttp3 4.4.0`
- UI: `material 1.13.0`, `glide 4.16.0`, `ccp 2.5.0` (country code picker), `switch-button 0.0.3`
- Paging: `androidx.paging 3.4.1`
- Camera: `androidx.camera 1.5.2`
