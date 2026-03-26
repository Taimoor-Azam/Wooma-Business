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
│   └── MainActivity    # Main dashboard post-login (3 fragments via BottomNavigationView)
├── adapter/            # 23 RecyclerView adapters for all list views
├── fragment/           # PropertiesFragment, SettingsFragment, MessagesFragment
├── model/              # Data classes for API requests/responses; ApiResponse<T> wraps all API calls
├── data/network/       # MyApi.kt (Retrofit interface) + RetrofitClient.kt (Bearer token auto-injected)
├── storage/Prefs.kt    # SharedPreferences wrapper — persists user/auth data
└── customs/            # Utils, GenericPagingSource (Paging 3), custom dialogs
```

### Key Patterns

**Making API calls** — use the `makeApiRequest<T, R>()` Activity extension from `RetrofitClient.kt`:
```kotlin
makeApiRequest(
    apiServiceClass = MyApi::class.java,
    context = this,
    showLoading = true,
    requestAction = { api -> api.someEndpoint(param) },
    listener = object : ApiResponseListener<ApiResponse<SomeModel>> {
        override fun onSuccess(response: ApiResponse<SomeModel>) { /* handle */ }
        override fun onFailure(errorMessage: ErrorResponse?) { /* handle API error */ }
        override fun onError(throwable: Throwable) { /* handle network error */ }
    }
)
```
`showLoading = true` automatically shows/dismisses a `ProgressDialog`. Always use this pattern — never call Retrofit directly.

**RetrofitClient singleton caveat** — `RetrofitClient` lazily creates one Retrofit instance on first call. If the base URL ever needs to change, `retrofit` must be set to `null` first to force re-creation.

**All API responses** use `ApiResponse<T> { success: Boolean, message: String, errors: String, data: T }`.

**Pagination** — `GenericPagingSource<T>` wraps any `suspend (page: Int, limit: Int) -> List<T>` lambda into a Paging 3 source. Use this for any infinite-scroll list.

**View Binding** — enabled globally; use `ActivityXxxBinding` / `ItemXxxBinding` / `FragmentXxxBinding`. Never use `findViewById`.

**Responsive sizing** — use `@dimen/` resources from `sdp` (scalable dp) and `ssp` (scalable sp) libraries, never raw `dp`/`sp` values in XML.

**Image loading** — always use Glide; never `ImageView.setImageBitmap()` directly for remote images.

**BaseActivity** — sets status bar to amber (`#FFC107`) with light icons and handles window insets. Call `applyWindowInsetsToBinding()` in `onCreate` for correct edge-to-edge padding.

### Auth Flow

`SplashActivity` (checks `Prefs` for valid token, 2-second delay) → `GetStartedActivity` → `LoginActivity` → `OTPActivity` → `ActivateAccountActivity` (if needed) → `MainActivity`

### Report Flow

`SelectPropertyForReportActivity` → `SelectReportTypeActivity` → `ConfigureReportActivity` → `CompleteReportActivity`

Key report activities under `activities/report/`:
- `CompleteReportActivity` — main report editing hub; manages rooms, items, meters, detectors, keys, checklists; two submission modes (digital signature via tenant list, or manual signature)
- `CameraActivity` — in-app camera capture for inventory photos (uses CameraX)
- `otherItems/` — dedicated add/edit/list activities for meters, detectors, keys, checklists
- `inventorysettings/` — change report type, dates, assessors, duplicate report

### Storage / Auth Token

`Prefs.kt` serializes the full `Users` object (including `access_token`) to SharedPreferences as JSON. `RetrofitClient` reads `Prefs.getUser(context)?.access_token` on every request via the OkHttp interceptor — no manual header management needed.

### Dependencies Reference

- Networking: `retrofit 2.7.1`, `okhttp3 4.4.0`
- UI: `material 1.13.0`, `glide 4.16.0`, `ccp 2.5.0` (country code picker), `switch-button 0.0.3`
- Paging: `androidx.paging 3.4.1`
- Camera: `androidx.camera 1.5.2`
