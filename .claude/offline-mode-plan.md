# Offline-First Mode Enforcement Plan

## Context

The app needs clearly enforced online/offline boundaries:
- Property and report listings must always work from local DB (cached), with silent background sync when internet is available.
- Mutations that are reversible or incremental (report items: rooms, meters, keys, detectors, checklist, room items) must work offline and sync automatically in the background.
- Mutations that are final or hard to reverse (create property, create report, complete report, duplicate/type/assessor/date changes) must require internet connectivity before proceeding.
- Completing a report must additionally be gated on all report items being fully synced to the server.

## Current State (already implemented — do not re-implement)

| Feature | Status |
|---|---|
| Properties listing (offline, background refresh) | ✅ `PropertiesFragment` + `PropertyRepository` |
| Create property (online-only guard) | ✅ `AddPropertyActivity` checks connectivity |
| Report listing (local DB, background refresh) | ✅ `ReportListingActivity` uses `reportRepo.observeByProperty()` |
| Create report (online-only guard) | ✅ `ConfigureReportActivity` checks connectivity |
| Meters offline (save + sync queue) | ✅ `AddEditMeterActivity` / `MeterListingActivity` |
| Keys / Detectors / Rooms / RoomItems / Checklists offline | ✅ All modified (same pattern as Meters) |
| `SyncScheduler`, `SyncWorker`, `ImageUploadWorker` | ✅ Fully implemented |
| `ConnectivityObserver` | ✅ `isConnected(): Boolean` + `observeConnectivity(): Flow<Boolean>` |
| `SyncQueueDao.countPending(): Flow<Int>` | ✅ Counts `status = 'PENDING'` rows globally |

## What Needs Implementation

### 1. Add `countPending()` to `PendingUploadDao`

**File:** `app/src/main/java/com/wooma/data/local/dao/PendingUploadDao.kt`

Add a Flow-based count method (mirrors SyncQueueDao pattern):

```kotlin
@Query("SELECT COUNT(*) FROM pending_uploads WHERE status = 'PENDING'")
fun countPending(): Flow<Int>
```

---

### 2. Complete Report — Sync Gate

**File:** `app/src/main/java/com/wooma/activities/report/complete/CompleteReportActivity.kt`

**Problem:** The button only checks connectivity at click time. It never checks whether all report items have finished syncing to the server.

**Required behavior:**
- Disable `binding.btnSendReview` when: `pendingSyncCount > 0` OR `pendingUploadCount > 0` OR `!isConnected`
- Show an inline status label (e.g., `tvSyncStatus`) that explains why the button is disabled:
  - "Syncing changes… please wait" when items are pending
  - "Internet connection required" when offline and no pending items
  - Hidden (or gone) when button is enabled

**Implementation:**

```kotlin
// In onCreate(), after binding:
val db = WoomaDatabase.getInstance(this)
val connectivity = ConnectivityObserver(this)

lifecycleScope.launch {
    combine(
        db.syncQueueDao().countPending(),
        db.pendingUploadDao().countPending(),
        connectivity.observeConnectivity()
    ) { pendingSync, pendingUploads, isOnline ->
        Triple(pendingSync, pendingUploads, isOnline)
    }.collect { (pendingSync, pendingUploads, isOnline) ->
        val hasPending = pendingSync > 0 || pendingUploads > 0
        val canComplete = isOnline && !hasPending
        binding.btnSendReview.isEnabled = canComplete
        binding.tvSyncStatus.isVisible = !canComplete
        binding.tvSyncStatus.text = when {
            hasPending -> "Syncing changes… please wait"
            !isOnline  -> "Internet connection required"
            else       -> ""
        }
    }
}
```

- Remove the existing runtime `isConnected()` check inside the button click handler (the gate above replaces it).
- `tvSyncStatus` — add this `TextView` to the activity layout if not already present.

---

### 3. Online-Only Guards for Settings Activities

All four activities need a pre-flight connectivity check **before** their primary action. Pattern is the same in each:

```kotlin
if (!ConnectivityObserver(this).isConnected()) {
    Toast.makeText(this, "Internet connection required", Toast.LENGTH_SHORT).show()
    return
}
```

#### 3a. `ChangeReportTypeActivity`
**File:** `app/src/main/java/com/wooma/activities/report/inventorysettings/ChangeReportTypeActivity.kt`

Add the connectivity guard at the start of the method that calls `reportRepo.updateReportType()`.

#### 3b. `ChangeAssessorActivity`
**File:** `app/src/main/java/com/wooma/activities/report/inventorysettings/ChangeAssessorActivity.kt`

Add the connectivity guard at the start of `changeAssessorApi()`, before calling `reportRepo.updateAssessor()`.

#### 3c. `ChangeReportDateActivity`
**File:** `app/src/main/java/com/wooma/activities/report/inventorysettings/ChangeReportDateActivity.kt`

Add the connectivity guard at the start of `changeDateApi()`, before calling `reportRepo.updateCompletionDate()`.

#### 3d. `DuplicateReportActivity`
**File:** `app/src/main/java/com/wooma/activities/report/inventorysettings/DuplicateReportActivity.kt`

Add the connectivity guard at the start of `duplicateReportApi()` AND before calling `getPropertiesList()` in `onCreate` / `onResume` (since properties must load from API here — if offline, show a toast and finish the activity).

---

### 4. Update Error Message in `ConfigureReportActivity` (minor)

**File:** `app/src/main/java/com/wooma/activities/report/ConfigureReportActivity.kt`

Current message: `"Internet connection required to create a report"`  
Required: `"Please connect to internet to create report"`

---

## Critical Files

| File | Change |
|---|---|
| `data/local/dao/PendingUploadDao.kt` | Add `countPending(): Flow<Int>` |
| `activities/report/complete/CompleteReportActivity.kt` | Add sync gate observing both queues + connectivity |
| `activities/report/inventorysettings/ChangeReportTypeActivity.kt` | Add connectivity pre-flight check |
| `activities/report/inventorysettings/ChangeAssessorActivity.kt` | Add connectivity pre-flight check |
| `activities/report/inventorysettings/ChangeReportDateActivity.kt` | Add connectivity pre-flight check |
| `activities/report/inventorysettings/DuplicateReportActivity.kt` | Add connectivity pre-flight check |
| `activities/report/ConfigureReportActivity.kt` | Update offline error message string |

## Utilities to Reuse

- `ConnectivityObserver(context).isConnected()` — sync check at `com/wooma/sync/ConnectivityObserver.kt`
- `ConnectivityObserver(context).observeConnectivity()` — Flow for reactive gate
- `WoomaDatabase.getInstance(context).syncQueueDao().countPending()` — Flow<Int>
- `WoomaDatabase.getInstance(context).pendingUploadDao().countPending()` — Flow<Int> (after Step 1)
- `kotlinx.coroutines.flow.combine` — merge three flows into one
- `SyncScheduler.scheduleImmediateSync(context)` — already called in ChangeReportType/Assessor/Date after update

## Verification

1. **Properties offline:** Kill network → open app → properties load from cache ✓
2. **Create property offline:** Kill network → tap Add Property → connectivity toast appears, no navigation ✓
3. **Report listing offline:** Kill network → open a property → reports show from cache ✓
4. **Create report offline:** Kill network → tap Create Report → "Please connect to internet to create report" toast ✓
5. **Edit meter/key/detector offline:** Kill network → open a report → add/edit meter → saved locally, sync icon shows ✓
6. **Complete report with pending sync:** Add meter offline → immediately tap Complete Report → button is disabled, "Syncing changes…" message visible ✓
7. **Complete report online, all synced:** Re-enable network → wait for sync → Complete button becomes enabled ✓
8. **Change report type offline:** Kill network → tap Change Type → connectivity toast, no change ✓
9. **Change assessor offline:** Same as above ✓
10. **Change date offline:** Same as above ✓
11. **Duplicate report offline:** Same as above ✓
