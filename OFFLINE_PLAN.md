# Offline-First Conversion Plan — Wooma Business

## Context

The app currently fetches all data fresh from the API on every screen load, with zero local persistence beyond auth tokens. Users lose all progress if connectivity drops mid-inspection. This plan converts the entire app to **offline-first**: all reads serve from a local Room database, all writes are queued locally and synced to the server via WorkManager when internet is available. Images (room items, meters, keys, detectors, inspection photos, cover photos) are saved to internal storage immediately and uploaded in the background when online.

Operations that remain **online-only** (require internet, show a toast if offline):
- OTP authentication
- `completeReport` / `sendReportForApproval` / `cancelSignatureRequest`
- PDF download
- Postal code lookup
- Account deletion / restore
- Contact support (Crisp chat)
- Creating a new property
- Creating a new report

### Complete Report — Sync Gate

The **Complete Report** button is disabled unless **all** local data is fully synced (i.e. `sync_queue` has zero PENDING/IN_PROGRESS/FAILED rows **and** `pending_uploads` has zero PENDING rows for this report).

- `ConfigureReportActivity` and `InventoryListingActivity` observe `SyncQueueDao.countPending()` + `PendingUploadDao` count; the button is greyed out with label "Sync pending…" while any unsynced data exists.
- Once the queue is empty (all DONE) **and** the device is online, the button becomes active.
- If the device is offline, the button stays disabled regardless of queue state (can't complete without internet even if data is synced).

Implementation note for Phase 5:
```kotlin
// In ConfigureReportActivity / InventoryListingActivity
lifecycleScope.launch {
    combine(
        db.syncQueueDao().countPending(),           // Flow<Int>
        connectivityObserver.observeConnectivity()  // Flow<Boolean>
    ) { pendingCount, isOnline ->
        pendingCount == 0 && isOnline
    }.collect { canComplete ->
        binding.btnCompleteReport.isEnabled = canComplete
        binding.btnCompleteReport.alpha = if (canComplete) 1f else 0.5f
    }
}
```

### Report List — Offline Cache Only

Only reports that have been previously fetched and stored in Room will appear when offline. No "empty state due to no internet" workaround — the list simply shows whatever is in the local DB. Reports never seen on this device (i.e. not yet in Room) will not appear until the device goes online and a background refresh runs.

- `ReportListingActivity` observes `ReportDao.observeByProperty(propertyId)` — the Flow emits whatever is in Room regardless of connectivity.
- Background `refreshReports(propertyId)` is attempted silently on resume; if it fails (offline), the cached list remains unchanged — no error shown to the user.
- Same rule applies to `ArchivePropertiesActivity` (archived reports) and `PropertiesFragment` (property list).

### Add Property / Add Report — Online-Only Guard

Both are fully online-only actions:
- **Add Property** (`AddPropertyActivity`) — show toast and return if `ConnectivityObserver.isConnected() == false`
- **Add Report** (`ConfigureReportActivity` / `SelectReportTypeActivity`) — same guard; button/FAB disabled when offline

```kotlin
binding.btnAddProperty.setOnClickListener {
    if (!ConnectivityObserver(this).isConnected()) {
        showToast("Internet connection required to add a property")
        return@setOnClickListener
    }
    // existing flow…
}
```

### Contact Support — Online-Only Guard

Crisp chat (`Crisp.chat(…)`) requires an internet connection. Guard every entry point that opens Crisp:
```kotlin
if (!ConnectivityObserver(this).isConnected()) {
    showToast("Internet connection required for support")
    return
}
```

---

## Architecture Overview

```
Activity / Fragment
       │  observes Flow<T>        │  calls suspend fun
       ▼                          ▼
  Repository ◄──────────── Room DAO (local DB)
       │
       ├── on READ:  return Flow from DAO, background-refresh from API → save to DB
       └── on WRITE: write to DB (syncStatus=PENDING_*), enqueue to SyncQueue
                                   │
                              WorkManager
                      ┌────────────┴────────────┐
                  SyncWorker               ImageUploadWorker
            (processes SyncQueue)       (processes PendingUploads)
            runs after network           runs after SyncWorker
              reconnect
```

**ID Strategy** — offline-created entities use `local_XXXXXXXXXX` as their stable `id` (PK in Room). A separate nullable `serverId` column is populated when the server ACKs the CREATE. All child entities keep their FK pointing to the stable `local_` PK — no cascade updates needed. API calls in workers always use `entity.serverId` (throws → retry if still null).

---

## Phase 1 — Foundation: Room Database (Week 1–2)

**Goal:** App compiles with full Room schema, no user-facing behavior change.

### 1.1 Dependencies — `gradle/libs.versions.toml`

```toml
[versions]
room          = "2.7.1"
workManager   = "2.9.1"
coroutines    = "1.9.0"
lifecycleKtx  = "2.8.7"

[libraries]
room-runtime          = { module = "androidx.room:room-runtime",          version.ref = "room" }
room-ktx              = { module = "androidx.room:room-ktx",              version.ref = "room" }
room-compiler         = { module = "androidx.room:room-compiler",         version.ref = "room" }
work-runtime-ktx      = { module = "androidx.work:work-runtime-ktx",      version.ref = "workManager" }
coroutines-core       = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core",    version.ref = "coroutines" }
coroutines-android    = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx",         version.ref = "lifecycleKtx" }
```

### 1.2 `app/build.gradle`

```groovy
plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("org.jetbrains.kotlin.kapt")           // ADD
}

dependencies {
    implementation libs.room.runtime
    implementation libs.room.ktx
    kapt          libs.room.compiler
    implementation libs.work.runtime.ktx
    implementation libs.coroutines.core
    implementation libs.coroutines.android
    implementation libs.lifecycle.runtime.ktx
}
```

### 1.3 Room Entities — `data/local/entity/`

Create one file per entity. All mutable entities have a `syncStatus: SyncStatus` field.

**SyncStatus.kt**
```kotlin
enum class SyncStatus { SYNCED, PENDING_CREATE, PENDING_UPDATE, PENDING_DELETE }
```

**Converters.kt** — `@TypeConverter` for `SyncStatus` (String ↔ enum)

| Entity File | Table | PK | Key FKs | Notes |
|---|---|---|---|---|
| `PropertyEntity` | `properties` | `id` (local_ or server) | — | + `serverId`, `syncStatus` |
| `ReportEntity` | `reports` | `id` | `propertyId` → properties | denorm counts, `serverId`, `syncStatus` |
| `ReportTypeEntity` | `report_types` | `id` | — | read-only, no syncStatus |
| `RoomEntity` | `rooms` | `id` | `reportId` → reports | `serverId`, `syncStatus` |
| `RoomItemEntity` | `room_items` | `id` | `roomId` → rooms | `serverId`, `syncStatus` |
| `RoomInspectionEntity` | `room_inspections` | `id` | `roomId` → rooms | `serverId`, `syncStatus` |
| `MeterEntity` | `meters` | `id` | `reportId` → reports | `serverId`, `syncStatus` |
| `KeyEntity` | `keys` | `id` | `reportId` → reports | `serverId`, `syncStatus` |
| `DetectorEntity` | `detectors` | `id` | `reportId` → reports | `serverId`, `syncStatus` |
| `AttachmentEntity` | `attachments` | `id` | `entityId` (logical, no FK) | `localUri`, `storageKey`, `isUploaded`, `entityType` |
| `ChecklistEntity` | `checklists` | `id` (report_checklist_id) | `reportId` → reports | `checklistTemplateId`, `syncStatus` |
| `ChecklistQuestionEntity` | `checklist_questions` | `localId` (autoincrement) | `reportChecklistId` → checklists | `answerId`, `originalNote`, `syncStatus` |
| `ChecklistInfoFieldEntity` | `checklist_info_fields` | `localId` (autoincrement) | `reportChecklistId` → checklists | `answerId`, `originalAnswerText`, `syncStatus` |
| `TenantReviewEntity` | `tenant_reviews` | `id` | `reportId` → reports | no syncStatus (server-managed) |
| `AssessorEntity` | `assessors` | `id` | — | cache only |
| `TemplateEntity` | `templates` | `id` | — | cache only |
| `TemplateRoomEntity` | `template_rooms` | `id` | `templateId` → templates | — |
| `TemplateItemEntity` | `template_items` | `id` | `templateRoomId` → template_rooms | — |
| `SyncQueueEntity` | `sync_queue` | `id` (autoincrement) | `parentSyncId` (self-ref) | `status` PENDING/IN_PROGRESS/DONE/FAILED, `payload` (JSON), `retryCount` |
| `PendingUploadEntity` | `pending_uploads` | `id` (autoincrement) | — | `localUri`, `entityLocalId`, `entityServerId`, `status`, `storageKey` |

**ReportEntity key extra fields:**
```kotlin
val countMeters: Int, val countKeys: Int, val countDetectors: Int,
val countRooms: Int, val countChecklists: Int  // updated locally on each mutation
```

### 1.4 DAOs — `data/local/dao/`

Key signatures per DAO (all are `interface`, `@Dao`):

**PropertyDao**
```kotlin
fun observeActiveProperties(): Flow<List<PropertyEntity>>
fun observeArchivedProperties(): Flow<List<PropertyEntity>>
suspend fun getById(id: String): PropertyEntity?
suspend fun upsert(p: PropertyEntity)
suspend fun upsertAll(list: List<PropertyEntity>)
suspend fun updateSyncStatus(id: String, status: SyncStatus)
suspend fun promoteLocalId(oldId: String, newId: String)  // update id col, set SYNCED
```

**ReportDao**
```kotlin
fun observeByProperty(propertyId: String): Flow<List<ReportEntity>>
fun observeById(id: String): Flow<ReportEntity?>
suspend fun upsert(r: ReportEntity); upsertAll(...)
suspend fun promoteLocalId(oldId: String, newId: String)
suspend fun incrementRoomCount(reportId: String, delta: Int)
suspend fun incrementMeterCount / incrementKeyCount / incrementDetectorCount
suspend fun updateStatus(reportId: String, status: String)
suspend fun updateCoverImage(reportId: String, key: String?)
```

**RoomDao / RoomItemDao / MeterDao / KeyDao / DetectorDao** — same pattern:
```kotlin
fun observeByReport(reportId: String): Flow<List<XxxEntity>>   // rooms, meters, etc.
suspend fun getById(id: String): XxxEntity?
suspend fun upsert(x: XxxEntity); upsertAll(...)
suspend fun promoteLocalId(localId: String, serverId: String)  // sets serverId, syncStatus=SYNCED
suspend fun softDelete(id: String)                              // sets isDeleted=true, PENDING_DELETE
suspend fun updateSyncStatus(id: String, status: SyncStatus)
```

**AttachmentDao**
```kotlin
fun observeByEntity(entityId: String, entityType: String): Flow<List<AttachmentEntity>>
suspend fun getByEntity(entityId: String, entityType: String): List<AttachmentEntity>
suspend fun upsert(a: AttachmentEntity); upsertAll(...)
suspend fun markUploaded(localId: String, serverId: String, storageKey: String)
suspend fun reattachToNewEntityId(oldEntityId: String, newEntityId: String)
suspend fun deleteById(id: String)
```

**SyncQueueDao**
```kotlin
// Ordered: no-dependency entries first, then by createdAt
@Query("SELECT * FROM sync_queue WHERE status='PENDING' ORDER BY parentSyncId ASC NULLS FIRST, createdAt ASC")
suspend fun getPendingInOrder(): List<SyncQueueEntity>
suspend fun enqueue(entry: SyncQueueEntity): Long    // returns inserted id
suspend fun updateStatus(id: Long, status: String, msg: String? = null)
suspend fun requeueForRetry(id: Long)                // status=PENDING, retryCount++
suspend fun updateServerEntityId(localId: String, type: String, serverId: String)
suspend fun purgeDone()
```

**PendingUploadDao**
```kotlin
suspend fun getPending(): List<PendingUploadEntity>
suspend fun enqueue(entry: PendingUploadEntity): Long
suspend fun updateStatus(id: Long, status: String)
suspend fun updateEntityServerId(localId: String, type: String, serverId: String)
suspend fun markDone(id: Long, key: String, attachmentId: String)
suspend fun requeueForRetry(id: Long)
suspend fun purgeDone()
```

### 1.5 Database Class — `data/local/WoomaDatabase.kt`

```kotlin
@Database(entities = [PropertyEntity::class, ReportEntity::class, ReportTypeEntity::class,
    RoomEntity::class, RoomItemEntity::class, RoomInspectionEntity::class,
    MeterEntity::class, KeyEntity::class, DetectorEntity::class, AttachmentEntity::class,
    ChecklistEntity::class, ChecklistQuestionEntity::class, ChecklistInfoFieldEntity::class,
    TenantReviewEntity::class, AssessorEntity::class, TemplateEntity::class,
    TemplateRoomEntity::class, TemplateItemEntity::class,
    SyncQueueEntity::class, PendingUploadEntity::class], version = 1)
@TypeConverters(Converters::class)
abstract class WoomaDatabase : RoomDatabase() {
    // abstract fun xxxDao(): XxxDao   (one per entity)
    companion object {
        @Volatile private var INSTANCE: WoomaDatabase? = null
        fun getInstance(ctx: Context): WoomaDatabase = INSTANCE ?: synchronized(this) {
            Room.databaseBuilder(ctx.applicationContext, WoomaDatabase::class.java, "wooma_db").build().also { INSTANCE = it }
        }
    }
}
```

---

## Phase 2 — Read Path: Properties + Reports (Week 3–4)

**Goal:** List screens read from Room (instant, offline-capable). Mutations still use existing `makeApiRequest()` but call `repository.refresh*()` in `onSuccess` to update the cache.

### New files
- `data/local/mapper/EntityMappers.kt` — `Property.toEntity()`, `PropertyEntity.toProperty()`, `RoomsResponse.toEntity(reportId)`, `RoomEntity.toRoomsResponse()`, etc. (one `toEntity` and one reverse per type — reuse existing API model classes so adapter code is unchanged)
- `data/repository/PropertyRepository.kt`
- `data/repository/ReportRepository.kt`

### Repository read pattern
```kotlin
// In PropertyRepository:
fun observeActiveProperties(): Flow<List<PropertyEntity>> = dao.observeActiveProperties()

suspend fun refreshProperties() {
    val response = api.getPropertiesList(mapOf("page" to 1, "limit" to 200, "is_active" to true)).execute()
    if (response.isSuccessful) {
        response.body()?.data?.data?.map { it.toEntity() }?.let { dao.upsertAll(it) }
    }
}
```

### Activity migration pattern (read-only list)
```kotlin
// Replace onResume() API call with:
override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    viewLifecycleOwner.lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.STARTED) {
            repo.observeActiveProperties().collect { entities ->
                adapter.updateList(entities.map { it.toProperty() })
            }
        }
    }
    // Silent background refresh — updates cache, Flow re-emits automatically
    viewLifecycleOwner.lifecycleScope.launch {
        try { repo.refreshProperties() } catch (_: Exception) {}
    }
}
```

### Files to migrate in Phase 2
- `fragment/PropertiesFragment.kt`
- `activities/report/ReportListingActivity.kt`
- `activities/property/ArchivePropertiesActivity.kt`

---

## Phase 3 — Write Path: Sync Queue + OtherItems + Rooms (Week 5–6)

**Goal:** Meters, Keys, Detectors, and Room mutations are fully offline. SyncWorker + ImageUploadWorker operational.

### 3.1 `sync/ConnectivityObserver.kt`
```kotlin
class ConnectivityObserver(ctx: Context) {
    fun isConnected(): Boolean { /* ConnectivityManager.activeNetwork + NET_CAPABILITY_VALIDATED */ }
    fun observeConnectivity(): Flow<Boolean>  // callbackFlow via NetworkCallback
}
```

### 3.2 `sync/SyncWorker.kt` — `CoroutineWorker`

Processing loop:
```
1. db.syncQueueDao().getPendingInOrder()
2. For each entry:
   a. If entry.parentSyncId != null → look up parent row status
      - If not DONE → skip (will be retried next run)
   b. db.syncQueueDao().updateStatus(id, "IN_PROGRESS")
   c. Call processXxx(entry) based on entityType
   d. On success → updateStatus(DONE)
   e. On exception → if retryCount >= 4: FAILED, else requeueForRetry
3. db.syncQueueDao().purgeDone()
4. Return Result.success()
```

**For each entity type, processXxx handles CREATE / UPDATE / DELETE / REORDER:**
- Calls `resolveServerIdFromLocal(entityType, localId)` to swap `local_XXXX` → real server ID before making API call
- On CREATE success: calls `dao.promoteLocalId(localId, serverId)` + updates `sync_queue.serverEntityId` + `pending_uploads.entityServerId` + `attachments.entityId` via `reattachToNewEntityId`

```kotlin
private suspend fun resolveServerIdFromLocal(type: String, localOrServerId: String): String? {
    if (!localOrServerId.startsWith("local_")) return localOrServerId
    return when (type) {
        "PROPERTY" -> db.propertyDao().getById(localOrServerId)?.serverId
        "REPORT"   -> db.reportDao().getById(localOrServerId)?.serverId
        "ROOM"     -> db.roomDao().getById(localOrServerId)?.serverId
        "METER"    -> db.meterDao().getById(localOrServerId)?.serverId
        "KEY"      -> db.keyDao().getById(localOrServerId)?.serverId
        "DETECTOR" -> db.detectorDao().getById(localOrServerId)?.serverId
        else -> null
    }
}
```

### 3.3 `sync/ImageUploadWorker.kt` — `CoroutineWorker`

For each `PendingUploadEntity` with status=PENDING:
1. Resolve `entityServerId` via `resolveEntityServerId()` — if still null (entity not yet synced), skip for this run
2. `GET /api/v1/attachments/presigned-url` (via `api.getPresignedUrl(fileName, mimeType).execute()`)
3. `PUT` file bytes to presigned S3 URL using bare `OkHttpClient` (no auth header)
4. `POST /api/v1/attachments` (`api.createAttachment(...)`) with `entityId = entityServerId`
5. `db.pendingUploadDao().markDone(...)` + `db.attachmentDao().markUploaded(...)`

File reading: `File(upload.localUri).readBytes()` (file was copied to `context.filesDir/attachments/` by `AttachmentRepository.saveLocalAttachment`)

### 3.4 `sync/SyncScheduler.kt`
```kotlin
object SyncScheduler {
    fun scheduleImmediateSync(context: Context) {
        // Chain: SyncWorker → ImageUploadWorker (uploads after entity IDs are resolved)
        WorkManager.getInstance(context).beginUniqueWork(
            "wooma_sync", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(CONNECTED).build()
        ).then(OneTimeWorkRequestBuilder<ImageUploadWorker>().setConstraints(CONNECTED).build())
         .enqueue()
    }

    fun schedulePeriodicSync(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "wooma_periodic", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(CONNECTED).build()
        )
    }
}
```

### 3.5 `WoomaApplication.kt` additions
```kotlin
override fun onCreate() {
    super.onCreate()
    Crisp.configure(...)  // existing
    WorkManager.initialize(this, Configuration.Builder().build())
    SyncScheduler.schedulePeriodicSync(this)
    applicationScope.launch {
        ConnectivityObserver(this@WoomaApplication).observeConnectivity()
            .filter { it }.collect { SyncScheduler.scheduleImmediateSync(this@WoomaApplication) }
    }
}
val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
```

### 3.6 Repository write pattern (`OtherItemsRepository`)
```kotlin
suspend fun addMeter(reportId: String, request: AddMeterRequest): MeterEntity {
    val localId = "local_${UUID.randomUUID().toString().replace("-", "")}"
    val entity = MeterEntity(id = localId, serverId = null, reportId = reportId,
        name = request.name, ..., syncStatus = SyncStatus.PENDING_CREATE)
    db.meterDao().upsert(entity)
    db.reportDao().incrementMeterCount(reportId, 1)
    db.syncQueueDao().enqueue(SyncQueueEntity(entityType="METER", operationType="CREATE",
        localEntityId = localId, payload = Gson().toJson(request)))
    return entity
}
```

For UPDATE: if `existing.syncStatus == PENDING_CREATE`, only update the local entity (the CREATE payload will be sent later — no second SyncQueue entry). If `SYNCED`, update entity + enqueue UPDATE.

For DELETE of a `PENDING_CREATE` entity: just soft-delete locally, no SyncQueue entry needed.

### 3.7 `AttachmentRepository.saveLocalAttachment()`
```kotlin
suspend fun saveLocalAttachment(ctx: Context, uri: Uri, entityLocalId: String,
    entityServerId: String?, entityType: String): AttachmentEntity {
    val localId = "local_${UUID.randomUUID()...}"
    val internalPath = copyToInternalStorage(ctx, uri, localId)  // copy to filesDir/attachments/
    val entity = AttachmentEntity(id=localId, serverId=null, entityId=entityLocalId,
        entityType=entityType, localUri=internalPath, isUploaded=false, ...)
    db.attachmentDao().upsert(entity)
    db.pendingUploadDao().enqueue(PendingUploadEntity(localUri=internalPath,
        entityLocalId=entityLocalId, entityServerId=entityServerId, ...))
    return entity
}
```

### 3.8 Files to migrate in Phase 3
- `WoomaApplication.kt`
- `activities/report/otherItems/AddEditMeterActivity.kt`
- `activities/report/otherItems/MeterListingActivity.kt`
- `activities/report/otherItems/AddEditKeysActivity.kt`
- `activities/report/otherItems/KeysListingActivity.kt`
- `activities/report/otherItems/AddEditDetectorActivity.kt`
- `activities/report/otherItems/DetectorListingActivity.kt`
- `activities/report/InventoryListingActivity.kt` (room add/delete/rename/reorder + cover image)

---

## Phase 4 — Room Items + Checklists + Inspections (Week 7–8)

**Goal:** Full inventory inspection workflow offline.

### New repositories
- `data/repository/RoomRepository.kt` — room items, room inspections
- `data/repository/ChecklistRepository.kt` — checklist questions, info fields, answer attachments

### Checklist auto-save pattern (no loading state shown):
```kotlin
onAnswerSelected = { question, answerOption ->
    lifecycleScope.launch {
        checklistRepo.upsertQuestionAnswer(checklistId, question.checklistQuestionId, answerOption, question.note)
        SyncScheduler.scheduleImmediateSync(this@CheckListDetailActivity)
    }
}
```

### SyncWorker additions in Phase 4
- `ROOM_ITEM` CREATE/UPDATE/DELETE
- `ROOM_INSPECTION` UPSERT
- `CHECKLIST_QUESTION` CREATE/UPDATE
- `CHECKLIST_INFO_FIELD` CREATE/UPDATE
- `CHECKLIST_STATUS` (toggle active/inactive)
- `CHECKLIST_ANSWER_ATTACHMENT` (findOrCreateAnswerAttachment flow)

### Files to migrate in Phase 4
- `activities/report/InventoryRoomItemsListActivity.kt`
- `activities/report/InventoryRoomItemActivity.kt`
- `activities/report/InspectionRoomActivity.kt`
- `activities/report/otherItems/CheckListListingActivity.kt`
- `activities/report/otherItems/CheckListDetailActivity.kt`

---

## Phase 5 — Property/Report CRUD + Hardening (Week 9–10)

**Goal:** Full offline support including creating properties and reports. Sync resilience.

### New repositories
- `data/repository/ConfigRepository.kt` — templates, report types, assessors (seeded at login)

### SyncWorker additions in Phase 5
- `PROPERTY` CREATE/UPDATE/ARCHIVE/RESTORE
- `REPORT` CREATE (with `parentSyncId` → property's CREATE sync entry)
- `TENANT_REVIEW` ADD/UPDATE/DELETE

### Connectivity guard for online-only operations
```kotlin
private fun completeReportAction() {
    if (!ConnectivityObserver(this).isConnected()) {
        showToast("Internet connection required to complete a report")
        return
    }
    // existing makeApiRequest call — unchanged
}
```

Apply this guard to: `CompleteReportActivity`, `ExtendTimerActivity`, `InventoryListingActivity.cancelSignatureRequestApi()`.

**Complete Report sync gate** — apply to `InventoryListingActivity` and `ConfigureReportActivity`: button disabled unless `countPending() == 0` AND device is online (see sync gate pattern above).

**Add Property / Add Report online guard** — apply to `AddPropertyActivity`, `SelectPropertyForReportActivity`, `SelectReportTypeActivity`: block action immediately if offline.

**Contact Support online guard** — find every Crisp entry point (search `Crisp.` in codebase) and wrap with connectivity check.

### Seed reference data on login
In `OTPActivity.onSuccess` (or `ActivateAccountActivity`), before navigating to MainActivity:
```kotlin
// Fetch and cache: getReportTypes, getReportTemplates, getAssessors
// These are needed offline when creating new reports
```

### Sync status indicator (BaseActivity)
Add a small icon/dot in `BaseActivity` toolbar bound to:
```kotlin
lifecycleScope.launch {
    db.syncQueueDao().countPending().collect { count ->
        binding.ivSyncStatus.visibility = if (count > 0) View.VISIBLE else View.GONE
    }
}
```
(`countPending()` = `@Query("SELECT COUNT(*) FROM sync_queue WHERE status='PENDING'") fun countPending(): Flow<Int>`)

### Files to migrate in Phase 5
- `activities/property/AddPropertyActivity.kt` — add online-only guard at entry
- `activities/property/EditPropertyActivity.kt`
- `activities/property/ArchivePropertiesActivity.kt` (already read-migrated in Phase 2)
- `activities/report/ConfigureReportActivity.kt` — sync gate on Complete Report button
- `activities/report/InventoryListingActivity.kt` — sync gate on Complete Report button
- `activities/report/complete/CompleteReportActivity.kt` — connectivity guard only
- `activities/report/SelectPropertyForReportActivity.kt` — add online-only guard at entry
- `activities/report/SelectReportTypeActivity.kt` — add online-only guard at entry
- `activities/report/inventorysettings/ChangeAssessorActivity.kt`
- `activities/report/inventorysettings/ChangeReportDateActivity.kt`
- `activities/report/inventorysettings/ChangeReportTypeActivity.kt`
- `activities/report/inventorysettings/DuplicateReportActivity.kt`
- Any activity/fragment that opens Crisp chat — wrap with connectivity check

---

## local_UUID → serverId Promotion: Exact Mechanism

**Rule:** `id` column (Room PK) = stable for the entity's lifetime. `serverId` = null until synced.

When `SyncWorker` completes a CREATE:
1. `dao.promoteLocalId(localId, serverAssignedId)` → sets `serverId = serverAssignedId`, `syncStatus = SYNCED`
2. **Children do NOT need FK updates** — they already point to `localId` which remains the PK
3. `db.syncQueueDao().updateServerEntityId(localId, entityType, serverAssignedId)` — so child SyncQueue entries can resolve the real server ID
4. `db.pendingUploadDao().updateEntityServerId(localId, entityType, serverAssignedId)` — so ImageUploadWorker uses real ID in `createAttachment`
5. `db.attachmentDao().reattachToNewEntityId(localId, serverAssignedId)` — updates `entityId` field in AttachmentEntity so the UI resolves correct image URLs

**Dependency graph example** (all offline, then reconnects):
```
Property CREATE (parentSyncId=null) → syncs first
  └─ Report CREATE (parentSyncId=property's syncId) → syncs after property
       ├─ Room CREATE (parentSyncId=report's syncId) → syncs after report
       │    └─ RoomItem CREATE (parentSyncId=room's syncId)
       └─ Meter CREATE (parentSyncId=report's syncId) → syncs after report
            └─ ImageUploadWorker: resolves meter.serverId → uploads photo
```

---

## Conflict Resolution

On every `refreshXxx()` call (background, triggered on network available):
```kotlin
suspend fun upsertFromServer(serverEntity: PropertyEntity) {
    val local = dao.getById(serverEntity.id)
    // Only overwrite if no pending local changes
    if (local == null || local.syncStatus == SyncStatus.SYNCED) {
        dao.upsert(serverEntity.copy(syncStatus = SyncStatus.SYNCED))
    }
    // If local.syncStatus != SYNCED: local changes win until sync completes
}
```

---

## Mapping Extensions — `data/local/mapper/EntityMappers.kt`

One `toEntity()` (API model → Room entity, `syncStatus = SYNCED`) and one reverse `toXxx()` (Room entity → API model, for passing to existing Adapters unchanged) per type.

Key non-obvious mappings:
- `RoomEntity.toRoomsResponse()`: `id = serverId ?: localId` (UI always gets a non-null ID; if `serverId` is null, the local ID is passed — adapters won't care since they just pass it back to repository calls)
- `ReportEntity`: denormalized count fields map to `Counts(meters, keys, detectors, rooms, activeChecklists)`

---

## Files NOT to Modify

| File | Reason |
|---|---|
| `data/network/RetrofitClient.kt` | OkHttp interceptor handles token refresh for SyncWorker automatically |
| `customs/AttachmentUploadHelper.kt` | Keep for cover image (online-only path) |
| `activities/auth/` (all 5) | OTP flow is online-only |
| `activities/report/CameraActivity.kt` | Still used as camera capture; only post-capture saving changes |
| `activities/report/PdfDownloadActivity.kt` | Online-only |

---

## Verification

### Phase 1
- `./gradlew assembleDebug` passes with no Room/kapt errors
- Room database file appears in device `/data/data/com.wooma/databases/wooma_db`

### Phase 2
- Kill internet → open app → PropertiesFragment and ReportListingActivity show cached data
- Restore internet → data refreshes without user action

### Phase 3–4 (end-to-end offline test)
1. Enable airplane mode
2. Open a report → navigate to Meters → add a new meter with 2 photos
3. Force-close and reopen → meter and photos are still visible (from Room)
4. Navigate to a room item → update condition → close
5. Re-enable internet → within 30 seconds, verify in server API that meter and room item update arrived
6. Verify photos uploaded to S3 and attached to meter

### Phase 5 (full offline session)
1. Airplane mode → Create new property → Create report → Add rooms → Add items → Take photos
2. Restore internet → all data syncs in order (property first, report second, rooms/items after)
3. Verify `sync_queue` table is empty (all DONE) after sync completes

### Sync failure test
- Intercept API with a 500 response for 5 retries → verify entry moves to FAILED status
- Verify app remains usable; failed sync does not crash or block UI
