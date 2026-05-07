package com.wooma.activities.report

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import androidx.annotation.RequiresApi
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.Gson
import com.wooma.activities.BaseActivity
import com.wooma.activities.report.complete.CompleteReportActivity
import com.wooma.activities.report.complete.ExtendTimerActivity
import com.wooma.activities.report.inventorysettings.InventoryReportSettingActivity
import com.wooma.adapter.InventoryOtherItemsAdapter
import com.wooma.adapter.InventoryRoomsAdapter
import com.wooma.adapter.ReportTenantsAdapter
import com.wooma.R
import com.wooma.customs.AttachmentUploadHelper
import com.wooma.customs.GridSpacingItemDecoration
import com.wooma.customs.Utils
import com.wooma.data.local.WoomaDatabase
import com.wooma.data.local.entity.SyncStatus
import com.wooma.data.local.entity.TenantReviewEntity
import com.wooma.data.network.ApiClient
import com.wooma.data.network.ApiResponseListener
import com.wooma.data.network.MyApi
import com.wooma.data.network.makeApiRequest
import com.wooma.data.network.showToast
import com.wooma.data.repository.AttachmentRepository
import com.wooma.data.repository.RoomRepository
import com.wooma.databinding.ActivityInventoryListingBinding
import com.wooma.databinding.PopupCoverImageMenuBinding
import com.wooma.model.ApiResponse
import com.wooma.model.CompleteReportRequest
import com.wooma.model.CountItem
import com.wooma.model.ErrorResponse
import com.wooma.model.PropertyReportType
import com.wooma.model.ReportData
import com.wooma.model.RoomsResponse
import com.wooma.model.TenantReview
import com.wooma.model.enums.ReportTypes
import com.wooma.model.enums.TenantReportStatus
import com.wooma.model.ImageItem
import com.wooma.sync.SyncScheduler
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Locale

class InventoryListingActivity : BaseActivity() {
    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )

    private data class Quintuple<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
    )

    private data class NineTuple<A, B, C, D, E, F, G, H, I>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E,
        val sixth: F,
        val seventh: G,
        val eighth: H,
        val ninth: I
    )

    private data class ChecklistPendingCounts(
        val questions: Int,
        val fields: Int,
        val queueOps: Int,
        val uploads: Int
    )

    private lateinit var adapter: InventoryRoomsAdapter
    private val roomsList = mutableListOf<RoomsResponse>()
    private lateinit var binding: ActivityInventoryListingBinding
    var reportId = ""
    var propertyId = ""
    var reportStatus = ""
    var reportType: PropertyReportType? = null
    var reportData: ReportData? = null
    private var coverImageStorageKey: String? = null
    private var pendingCoverLocalUri: String? = null
    private var isSyncingPendingCover = false
    private var pdfUrl: String? = null
    private var isNetworkAvailable = false
    private val CAMERA_REQUEST = 1001

    private val db by lazy { WoomaDatabase.getInstance(this) }
    private val roomRepo by lazy { RoomRepository(this) }
    private val attachmentRepo by lazy { AttachmentRepository(this) }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == TENANT_REQUEST_CODE && resultCode == RESULT_OK) {
            getTenantReviewsApi()
        }
        if (requestCode == CAMERA_REQUEST && resultCode == RESULT_OK) {
            val uri = CameraActivity.pendingUris.firstOrNull() ?: return
            handleCoverImagePicked(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityInventoryListingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        reportId = intent.getStringExtra("reportId") ?: ""
        reportStatus = intent.getStringExtra("reportStatus") ?: ""
        reportType = intent.getParcelableExtra("reportType")
        propertyId = intent.getStringExtra("propertyId") ?: ""

        binding.tvReportType.text = reportType?.type_code
            ?.replace("_", " ")
            ?.split(" ")
            ?.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            ?: ""

        adapter = InventoryRoomsAdapter(
            context = this,
            originalList = roomsList,
            reportId = reportId,
            reportStatus = reportStatus,
            reportType = reportType,
            showTimestamp = reportData?.showTimestamp ?: true,
            onDeleteRoom = { roomId ->
                Utils.showDialogBox(
                    this,
                    "Delete Room",
                    "Are you sure you want to remove this room?"
                ) {
                    lifecycleScope.launch {
                        roomRepo.deleteRoom(roomId ?: "")
                        if (Utils.isOnline(this@InventoryListingActivity))
                            SyncScheduler.scheduleImmediateSync(this@InventoryListingActivity)
                    }
                }
            },
            onReorder = { roomId, prevRank, nextRank ->
                lifecycleScope.launch {
                    roomRepo.reorderRoom(roomId, prevRank, nextRank)
                    if (Utils.isOnline(this@InventoryListingActivity))
                        SyncScheduler.scheduleImmediateSync(this@InventoryListingActivity)
                }
            },
            onUpdateRoom = { roomId, newName ->
                lifecycleScope.launch {
                    roomRepo.updateRoomName(roomId, newName)
                    if (Utils.isOnline(this@InventoryListingActivity))
                        SyncScheduler.scheduleImmediateSync(this@InventoryListingActivity)
                }
            }
        )
        binding.rvRooms.adapter = adapter

        setupSyncGate()
        observeData()

        val touchCallback = object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder) =
                if (adapter.isEditMode) makeMovementFlags(
                    ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                    0
                )
                else makeMovementFlags(0, 0)

            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                adapter.onItemMove(vh.adapterPosition, target.adapterPosition)
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled() = false
            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                adapter.onDropCompleted(vh.adapterPosition)
            }
        }
        val itemTouchHelper = ItemTouchHelper(touchCallback)
        itemTouchHelper.attachToRecyclerView(binding.rvRooms)
        adapter.itemTouchHelper = itemTouchHelper

        updateViewAccToStatus()
        binding.ivBack.setOnClickListener { navigateToReportListing() }

        binding.tvEditRooms.setOnClickListener {
            val editMode = !adapter.isEditMode
            adapter.setEditMode(editMode, canReorder = editMode && Utils.isOnline(this))
            binding.tvEditRooms.text = if (editMode) "Done" else "Edit"
        }

        binding.tvExtendTime.setOnClickListener {
            startActivity(Intent(this, ExtendTimerActivity::class.java).apply {
                putExtra("reportId", reportId)
                putExtra(
                    "expiryDate",
                    reportData?.extendReviewExpiry ?: reportData?.tenantReviewExpiry
                )
            })
        }

        binding.rvOtherItems.addItemDecoration(
            GridSpacingItemDecoration(
                2,
                (16 * resources.displayMetrics.density).toInt(),
                true
            )
        )

        binding.ivSettings.setOnClickListener {
            startActivity(Intent(this, InventoryReportSettingActivity::class.java).apply {
                putExtra("reportStatus", reportStatus)
                putExtra("reportId", reportId)
                putExtra("reportType", reportType)
                putExtra("assessor", reportData?.assessor)
                putExtra("completionDate", reportData?.completionDate)
                putExtra("propertyId", propertyId)
            })
        }

        binding.coverImageSection.setOnClickListener {
            if (reportStatus == TenantReportStatus.TENANT_REVIEW.value || reportStatus == TenantReportStatus.COMPLETED.value) {
                if (!coverImageStorageKey.isNullOrEmpty()) viewCoverImage()
                return@setOnClickListener
            }
            if (reportStatus != TenantReportStatus.IN_PROGRESS.value) return@setOnClickListener
            val hasImage = !coverImageStorageKey.isNullOrEmpty() || !pendingCoverLocalUri.isNullOrEmpty()
            if (!hasImage) {
                CameraActivity.pendingUris.clear()
                startActivityForResult(Intent(this, CameraActivity::class.java).apply {
                    putExtra("isCoverImage", true)
                    putExtra("showTimestamp", false)
                }, CAMERA_REQUEST)
            } else {
                showCoverImagePopup(it)
            }
        }

        binding.btnCompleteReport.setOnClickListener {
            when (reportType?.type_code) {
                ReportTypes.INSPECTION.value, ReportTypes.CHECK_OUT.value -> showCompleteInspectionBottomSheet()
                else -> startActivity(
                    Intent(
                        this,
                        CompleteReportActivity::class.java
                    ).putExtra("reportId", reportId)
                )
            }
        }

        binding.addAnotherTenantLayout.setOnClickListener {
            startActivityForResult(Intent(this, EditTenantActivity::class.java).apply {
                putExtra("isEditMode", false)
                putExtra("reportId", reportId)
            }, TENANT_REQUEST_CODE)
        }

        binding.ivAddRoom.setOnClickListener {
            startActivity(
                Intent(this, SelectRoomActivity::class.java).putExtra(
                    "reportId",
                    reportId
                )
            )
        }

        supportFragmentManager.setFragmentResultListener("sheet_key", this) { _, bundle ->
            val value = bundle.getString("added_room") ?: return@setFragmentResultListener
            lifecycleScope.launch { roomRepo.addRoom(reportId, value) }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateToReportListing()
            }
        })
    }

    private fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    db.roomDao().observeByReport(reportId),
                    db.roomItemDao().observePendingRoomIdsByReport(reportId),
                    db.roomInspectionDao().observePendingRoomIdsByReport(reportId)
                ) { roomEntities, pendingItemRoomIds, pendingInspectionRoomIds ->
                    val pendingItemIdsSet = pendingItemRoomIds.toSet()
                    val pendingInspectionIdsSet = pendingInspectionRoomIds.toSet()
                    adapter.updatePendingItemRoomIds(pendingItemIdsSet)
                    roomEntities
                        .filter { it.syncStatus != SyncStatus.PENDING_DELETE }
                        .map { roomEntity ->
                            RoomsResponse(
                                id = roomEntity.id,
                                templateId = roomEntity.templateId,
                                name = roomEntity.name,
                                displayOrder = roomEntity.displayOrder,
                                isSelected = true,
                                isSyncing = roomEntity.syncStatus != SyncStatus.SYNCED ||
                                    pendingItemIdsSet.contains(roomEntity.id) ||
                                    pendingInspectionIdsSet.contains(roomEntity.id),
                                items = ArrayList(),
                                inspection = ArrayList(),
                                attachments = ArrayList()
                            )
                        }
                }.collectLatest { rooms ->
                    roomsList.clear()
                    roomsList.addAll(rooms)
                    adapter.updateList(roomsList)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    db.reportDao().observeById(reportId),
                    db.tenantReviewDao().observeByReport(reportId)
                ) { entity, tenants -> entity to tenants }
                .collectLatest { (entity, tenants) ->
                    entity ?: return@collectLatest
                    if (entity.status == TenantReportStatus.TENANT_REVIEW.value && tenants.isNotEmpty()) {
                        val expiryDate = entity.extendReviewExpiry ?: entity.tenantReviewExpiry
                        val tenantReviews = ArrayList(tenants.map { t ->
                            TenantReview(
                                id = t.id, report_id = reportId,
                                first_name = t.firstName, last_name = t.lastName,
                                email_address = t.emailAddress, mobile_number = t.mobileNumber,
                                is_submitted = t.isSubmitted, submitted_at = t.submittedAt,
                                is_active = t.isActive, is_deleted = t.isDeleted,
                                created_at = t.createdAt, updated_at = t.updatedAt,
                                token = "", lookup_key = "", is_used = false,
                                metadata = null, full_name = null,
                                room_answers = emptyList(), keys_answers = emptyList(),
                                detecter_answers = emptyList(), checklist_answers = emptyList(),
                                meter_answers = emptyList()
                            )
                        })
                        @Suppress("NewApi")
                        updateViewForTenantReview(tenantReviews, expiryDate)
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                db.attachmentDao().observeByEntity(reportId, "REPORT_COVER").collectLatest { attachments ->
                    pendingCoverLocalUri = attachments.firstOrNull { !it.isUploaded }?.localUri
                    updateCoverImageView()
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val coreItemsFlow = combine(
                    db.reportDao().observeById(reportId),
                    db.meterDao().observeByReport(reportId),
                    db.keyDao().observeByReport(reportId),
                    db.detectorDao().observeByReport(reportId),
                    db.checklistDao().observeByReport(reportId),
                ) { entity, meters, keys, detectors, checklists ->
                    Quintuple(entity, meters, keys, detectors, checklists)
                }
                val checklistPendingFlow = combine(
                    db.checklistQuestionDao().observePendingCountByReport(reportId),
                    db.checklistInfoFieldDao().observePendingCountByReport(reportId),
                    db.syncQueueDao().observePendingChecklistOpsCountByReport(reportId),
                    db.pendingUploadDao().observePendingChecklistAttachmentUploadsCountByReport(reportId)
                ) { pendingChecklistQuestions, pendingChecklistFields, pendingChecklistQueueOps, pendingChecklistUploads ->
                    ChecklistPendingCounts(
                        questions = pendingChecklistQuestions,
                        fields = pendingChecklistFields,
                        queueOps = pendingChecklistQueueOps,
                        uploads = pendingChecklistUploads
                    )
                }

                combine(coreItemsFlow, checklistPendingFlow) { core, pending -> core to pending }
                    .collectLatest { (core, pending) ->
                    val (entity, meters, keys, detectors, checklists) = core
                    entity ?: return@collectLatest
                    val hasPendingMeterSync = meters.any { it.syncStatus != SyncStatus.SYNCED }
                    val hasPendingKeySync = keys.any { it.syncStatus != SyncStatus.SYNCED }
                    val hasPendingDetectorSync = detectors.any { it.syncStatus != SyncStatus.SYNCED }
                    val hasPendingChecklistSync =
                        checklists.any { it.syncStatus != SyncStatus.SYNCED } ||
                            pending.questions > 0 ||
                            pending.fields > 0 ||
                            pending.queueOps > 0 ||
                            pending.uploads > 0
                    coverImageStorageKey = entity.coverImageStorageKey
                    updateCoverImageView()
                    if (entity.completionDate.isNotEmpty()) {
                        try {
                            val inSdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                            val outSdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                            inSdf.parse(entity.completionDate)?.let { binding.tvDate.text = outSdf.format(it) }
                        } catch (_: Exception) {}
                    }
                    val checklistCount = if (checklists.isNotEmpty()) {
                        checklists.size
                    } else {
                        entity.countChecklists
                    }
                    val allItems = arrayListOf(
                        CountItem("Meters", entity.countMeters, hasPendingMeterSync),
                        CountItem("Keys", entity.countKeys, hasPendingKeySync),
                        CountItem("Detectors", entity.countDetectors, hasPendingDetectorSync),
                        CountItem("Checklist", checklistCount, hasPendingChecklistSync)
                    )
                    val isInspection = entity.reportTypeCode == ReportTypes.INSPECTION.value
                    val otherItems =
                        if (isInspection) allItems.filter { it.label == "Checklist" } else allItems
                    (binding.rvOtherItems.layoutManager as? GridLayoutManager)?.spanCount =
                        if (otherItems.size == 1) 1 else 2
                    binding.rvOtherItems.adapter = InventoryOtherItemsAdapter(
                        this@InventoryListingActivity,
                        ArrayList(otherItems),
                        reportId,
                        entity.status,
                        true
                    )

                    if (entity.status == TenantReportStatus.COMPLETED.value) {
                        pdfUrl = entity.pdfUrl?.let { "${ApiClient.IMAGE_BASE_URL}$it" }
                        updateViewForCompletedReport()
                        val blankCount = entity.blankSpacesCount
                        if (blankCount != 0 && entity.reportTypeCode != ReportTypes.CHECK_OUT.value) {
                            binding.tvBlankSpaces.visibility = View.VISIBLE
                            binding.tvBlankSpaces.text = "Completed with $blankCount blank signature spaces"
                        }
                    }
                    }
            }
        }
    }

    private fun setupSyncGate() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    db.syncQueueDao().countPending(),
                    com.wooma.sync.ConnectivityObserver(this@InventoryListingActivity)
                        .observeConnectivity()
                ) { pendingCount, isConnected ->
                    pendingCount == 0 && isConnected
                }.collectLatest { canComplete ->
                    binding.btnCompleteReport.isEnabled = canComplete
                    binding.btnCompleteReport.alpha = if (canComplete) 1.0f else 0.5f
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                db.syncQueueDao().countPending().collect { count ->
                    binding.ivSyncStatus.setImageResource(if (count > 0) R.drawable.svg_syncing else R.drawable.svg_synced)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                com.wooma.sync.ConnectivityObserver(this@InventoryListingActivity)
                    .observeConnectivity()
                    .collect { isConnected ->
                        isNetworkAvailable = isConnected
                        if (isConnected) syncPendingCoverChangesIfOnline()
                        if (adapter.isEditMode) {
                            adapter.setEditMode(true, canReorder = isConnected)
                        }
                        updateOnlineOnlyButtons(isConnected)
                    }
            }
        }
    }

    private fun updateOnlineOnlyButtons(isConnected: Boolean) {
        val alpha = if (isConnected) 1f else 0.4f
        binding.tvExtendTime.isEnabled = isConnected
        binding.tvExtendTime.alpha = alpha
        binding.tvCancelSignatureRequest.isEnabled = isConnected
        binding.tvCancelSignatureRequest.alpha = alpha
        binding.addAnotherTenantLayout.isEnabled = isConnected
        binding.addAnotherTenantLayout.alpha = alpha
    }

    private fun handleCoverImagePicked(uri: Uri) {
        if (Utils.isOnline(this)) {
            lifecycleScope.launch {
                try {
                    clearLocalCoverRows()
                    val report = db.reportDao().getById(reportId)
                    val serverId = report?.serverId ?: report?.id?.takeIf { !it.startsWith("local_") }
                    val localPreview = attachmentRepo.saveLocalAttachment(uri, reportId, serverId, "REPORT_COVER")
                    // Online add goes through patchCoverImageApi; keep local file as offline fallback, no queued upload needed.
                    localPreview.localUri?.let { db.pendingUploadDao().deleteByLocalUri(it) }
                    pendingCoverLocalUri = localPreview.localUri
                    updateCoverImageView()
                    AttachmentUploadHelper.uploadForStorageKey(
                        activity = this@InventoryListingActivity,
                        uri = uri,
                        onSuccess = { storageKey ->
                            patchCoverImageApi(storageKey, clearLocalRows = false)
                        },
                        onError = { message ->
                            showToast(message)
                        }
                    )
                } catch (e: Exception) {
                    showToast("Failed to save cover preview: ${e.message}")
                }
            }
            return
        }
        handleCoverImageOffline(uri)
    }

    private fun handleCoverImageOffline(uri: Uri) {
        lifecycleScope.launch {
            try {
                val report = db.reportDao().getById(reportId)
                val serverId = report?.serverId ?: report?.id?.takeIf { !it.startsWith("local_") }
                attachmentRepo.saveLocalAttachment(uri, reportId, serverId, "REPORT_COVER")
                SyncScheduler.scheduleImmediateSync(this@InventoryListingActivity)
            } catch (e: Exception) {
                showToast("Failed to save cover image: ${e.message}")
            }
        }
    }

    private fun navigateToReportListing() {
        startActivity(Intent(this, ReportListingActivity::class.java).apply {
            putExtra("propertyId", propertyId)
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        })
        finish()
    }

    private fun updateViewAccToStatus() {
        if (reportStatus == TenantReportStatus.IN_PROGRESS.value) {
            binding.ivAddRoom.visibility = View.VISIBLE
            binding.btnCompleteReport.visibility = View.VISIBLE
            binding.tvEditRooms.visibility = View.VISIBLE
            binding.tenantReviewLayout.visibility = View.GONE
            binding.completedReportLayout.visibility = View.GONE
            binding.completedTenantSection.visibility = View.GONE
        } else {
            binding.ivAddRoom.visibility = View.GONE
            binding.btnCompleteReport.visibility = View.GONE
            binding.tvEditRooms.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        if (Utils.isOnline(this)) {
            getReportByIdApi()
            lifecycleScope.launch {
                try { roomRepo.refreshRooms(reportId) } catch (_: Exception) {}
            }
            syncPendingCoverChangesIfOnline()
        } else {
            lifecycleScope.launch {
                val hasRooms = db.roomDao().getByReport(reportId).isNotEmpty()
                if (!hasRooms) {
                    showToast("Please connect to internet and then come back")
                    finish()
                }
            }
        }
    }

    private fun syncPendingCoverChangesIfOnline() {
        if (!Utils.isOnline(this) || isSyncingPendingCover) return
        lifecycleScope.launch {
            if (db.syncQueueDao().hasPendingReportCoverDelete(reportId)) {
                isSyncingPendingCover = true
                patchCoverImageApi(
                    storageKey = null,
                    clearLocalRows = true,
                    onPatched = {
                        lifecycleScope.launch {
                            db.syncQueueDao().deletePendingByEntityOperation(
                                entityType = "REPORT_COVER",
                                operationType = "DELETE",
                                localEntityId = reportId
                            )
                            isSyncingPendingCover = false
                        }
                    },
                    onPatchFailed = { isSyncingPendingCover = false }
                )
                return@launch
            }

            val pendingCoverUpload = db.pendingUploadDao().getPendingReportCoverUpload(reportId)
                ?: return@launch
            val localPath = pendingCoverUpload.localUri
            val localUri = Uri.fromFile(java.io.File(localPath))
            isSyncingPendingCover = true
            AttachmentUploadHelper.uploadForStorageKey(
                activity = this@InventoryListingActivity,
                uri = localUri,
                onSuccess = { storageKey ->
                    patchCoverImageApi(
                        storageKey = storageKey,
                        clearLocalRows = false,
                        onPatched = {
                            lifecycleScope.launch {
                                db.pendingUploadDao().deleteByLocalUri(localPath)
                                isSyncingPendingCover = false
                            }
                        },
                        onPatchFailed = { isSyncingPendingCover = false }
                    )
                },
                onError = { message ->
                    isSyncingPendingCover = false
                    showToast(message)
                }
            )
        }
    }

    private fun getReportByIdApi(showLoading: Boolean = false) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = showLoading,
            requestAction = { apiService -> apiService.getReportById(reportId, true, true, true) },
            listener = object : ApiResponseListener<ApiResponse<ReportData>> {
                override fun onSuccess(response: ApiResponse<ReportData>) {
                    if (response.success) {
                        reportData = response.data
                        lifecycleScope.launch {
                            try {
                                roomRepo.hydrateRoomsAndItemsFromReport(response.data, reportId)
                            } catch (_: Exception) {
                            }
                        }
                        lifecycleScope.launch {
                            db.reportDao().updateCounts(
                                reportId,
                                response.data.counts.meters,
                                response.data.counts.keys,
                                response.data.counts.detectors,
                                response.data.counts.activeChecklists
                            )
                        }
                        reportStatus = response.data.status
                        updateViewAccToStatus()

                        val apiReportType = response.data.reportType
                        if (reportType == null) {
                            reportType = PropertyReportType(
                                apiReportType.id,
                                apiReportType.display_name,
                                apiReportType.type_code
                            )
                            adapter.reportType = reportType
                            binding.tvReportType.text = apiReportType.display_name
                        }

                        coverImageStorageKey = response.data.coverImageStorageKey
                        updateCoverImageView()

                        lifecycleScope.launch {
                            db.reportDao().updateCoverImage(
                                reportId,
                                response.data.coverImageStorageKey
                            )
                            db.reportDao().updateCompletedInfo(
                                reportId,
                                response.data.pdfUrl,
                                response.data.status,
                                response.data.blankSpacesCount
                            )
                            db.reportDao().updateExpiryDates(
                                reportId,
                                response.data.tenantReviewExpiry,
                                response.data.extendReviewExpiry,
                                response.data.status
                            )
                        }

                        adapter.showTimestamp = response.data.showTimestamp

                        val rawDate = response.data.completionDate
                        if (rawDate.isNotEmpty()) {
                            try {
                                val inSdf =
                                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                                val outSdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                                inSdf.parse(rawDate)
                                    ?.let { binding.tvDate.text = outSdf.format(it) }
                            } catch (_: Exception) {
                            }
                        }

                        if (response.data.status == TenantReportStatus.TENANT_REVIEW.value) {
                            getTenantReviewsApi()
                        } else if (response.data.status == TenantReportStatus.COMPLETED.value) {
                            val blankCount = response.data.blankSpacesCount
                            if (blankCount != 0 && reportType?.type_code != ReportTypes.CHECK_OUT.value) {
                                binding.tvBlankSpaces.visibility = View.VISIBLE
                                binding.tvBlankSpaces.text =
                                    "Completed with $blankCount blank signature spaces"
                            }
                            pdfUrl = response.data.pdfUrl?.let { "${ApiClient.IMAGE_BASE_URL}$it" }
                            updateViewForCompletedReport()
                            getTenantReviewsApi()
                        }
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    showToast(errorMessage?.error?.message ?: "")
                }

                override fun onError(throwable: Throwable) {
                    showToast("Error: ${throwable.message}")
                }
            }
        )
    }

    private fun getTenantReviewsApi() {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.getTenantsForReportReview(reportId) },
            listener = object : ApiResponseListener<ApiResponse<ArrayList<TenantReview>>> {
                @RequiresApi(Build.VERSION_CODES.O)
                override fun onSuccess(response: ApiResponse<ArrayList<TenantReview>>) {
                    if (response.success) {
                        lifecycleScope.launch {
                            db.tenantReviewDao().upsertAll(response.data.map { t ->
                                TenantReviewEntity(
                                    id = t.id, reportId = reportId,
                                    firstName = t.first_name, lastName = t.last_name,
                                    emailAddress = t.email_address, mobileNumber = t.mobile_number,
                                    isSubmitted = t.is_submitted, submittedAt = t.submitted_at,
                                    isActive = t.is_active, isDeleted = t.is_deleted,
                                    createdAt = t.created_at, updatedAt = t.updated_at
                                )
                            })
                        }
                        if (reportStatus == TenantReportStatus.COMPLETED.value) updateCompletedWithTenants(
                            response.data
                        )
                        else updateViewForTenantReview(response.data)
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    showToast(errorMessage?.error?.message ?: "")
                }

                override fun onError(throwable: Throwable) {
                    showToast("Error: ${throwable.message}")
                }
            }
        )
    }

    private fun updateViewForCompletedReport() {
        binding.completedReportLayout.visibility = View.VISIBLE
        if (pdfUrl.isNullOrEmpty()) {
            binding.pdfPreparingLayout.visibility = View.VISIBLE
            binding.viewFinalReport.visibility = View.GONE
        } else {
            binding.pdfPreparingLayout.visibility = View.GONE
            binding.viewFinalReport.visibility = View.VISIBLE
            binding.viewFinalReport.setOnClickListener {
                startActivity(Intent(this, PdfDownloadActivity::class.java).apply {
                    putExtra(PdfDownloadActivity.EXTRA_PDF_URL, pdfUrl)
                    putExtra(PdfDownloadActivity.EXTRA_REPORT_ID, reportId)
                })
            }
        }
    }

    private fun updateCompletedWithTenants(data: ArrayList<TenantReview>) {
        if (data.isEmpty()) return
        val count = data.count { it.is_submitted }
        binding.completedTenantSection.visibility = View.VISIBLE
        binding.tvReviewPeriodEnded.text =
            "Review period ended with $count of ${data.size} signatures"
        binding.tvCompletedTenantSignatures.text = "Tenant signatures ($count of ${data.size})"
        binding.rvCompletedTenants.adapter =
            ReportTenantsAdapter(this, data, reportStatus = TenantReportStatus.COMPLETED.value)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateViewForTenantReview(data: ArrayList<TenantReview>, expiryDate: String? = null) {
        binding.tenantReviewLayout.visibility = View.VISIBLE
        binding.rvTenants.adapter = ReportTenantsAdapter(
            this,
            data,
            reportId = reportId,
            reportStatus = TenantReportStatus.TENANT_REVIEW.value,
            onTenantClick = { tenant ->
                if (isNetworkAvailable) startActivityForResult(Intent(this, EditTenantActivity::class.java).apply {
                    putExtra("isEditMode", true)
                    putExtra("reportId", reportId)
                    putExtra("tenantReviewId", tenant.id)
                    putExtra("firstName", tenant.first_name)
                    putExtra("lastName", tenant.last_name)
                    putExtra("email", tenant.email_address)
                    putExtra("mobileNumber", tenant.mobile_number)
                    putExtra("tenantCount", data.size)
                }, TENANT_REQUEST_CODE)
            })
        val resolvedExpiry = expiryDate ?: (reportData?.extendReviewExpiry ?: reportData?.tenantReviewExpiry)
        binding.daysRemaining.text = "${Utils.getDaysDifference(resolvedExpiry ?: "")} days remaining"
        val count = data.count { it.is_submitted }
        binding.signProgress.max = data.size
        binding.signProgress.progress = count
        binding.tvReceivedSigns.text = "${count}/${data.size}"
        binding.tvTotalTenants.text = "Tenant (${data.size})"
        binding.tvCancelSignatureRequest.setOnClickListener {
            Utils.showDialogBox(
                this,
                "Cancel Signature Request",
                "Are you sure you want to cancel?"
            ) { cancelSignatureRequestApi() }
        }
        updateOnlineOnlyButtons(isNetworkAvailable)
    }

    private fun cancelSignatureRequestApi() {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { api -> api.cancelSignatureRequest(reportId) },
            listener = object : ApiResponseListener<ApiResponse<Any>> {
                override fun onSuccess(response: ApiResponse<Any>) {
                    showToast("Cancelled"); getReportByIdApi()
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    showToast(errorMessage?.error?.message ?: "Failed")
                }

                override fun onError(throwable: Throwable) {
                    showToast("Error: ${throwable.message}")
                }
            }
        )
    }

    private fun showCompleteInspectionBottomSheet() {
        val bottomSheet = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_complete_inspection, null)
        bottomSheet.setContentView(sheetView)
        val cbConfirm = sheetView.findViewById<ImageView>(R.id.cbConfirm)
        val btnComplete = sheetView.findViewById<TextView>(R.id.btnCompleteReport)
        val ivClose = sheetView.findViewById<ImageView>(R.id.ivClose)
        ivClose.setOnClickListener { bottomSheet.dismiss() }
        cbConfirm.setOnClickListener {
            val checked = !cbConfirm.isSelected
            cbConfirm.isSelected = checked
            btnComplete.isEnabled = checked
            btnComplete.setBackgroundResource(if (checked) R.drawable.bg_button_green else R.drawable.bg_button_green_disabled)
        }
        btnComplete.setOnClickListener {
            makeApiRequest(
                apiServiceClass = MyApi::class.java,
                context = this,
                showLoading = true,
                requestAction = { api -> api.completeReport(reportId, CompleteReportRequest(0)) },
                listener = object : ApiResponseListener<ApiResponse<ReportData>> {
                    override fun onSuccess(response: ApiResponse<ReportData>) {
                        bottomSheet.dismiss(); showReportCompletedDialog()
                    }

                    override fun onFailure(errorMessage: ErrorResponse?) {
                        showToast(errorMessage?.error?.message ?: "Failed")
                    }

                    override fun onError(throwable: Throwable) {
                        showToast("Error: ${throwable.message}")
                    }
                }
            )
        }
        bottomSheet.show()
    }

    private fun updateCoverImageView() {
        val isReviewOrComplete =
            reportStatus == TenantReportStatus.TENANT_REVIEW.value || reportStatus == TenantReportStatus.COMPLETED.value
        val hasImage = !coverImageStorageKey.isNullOrEmpty() || !pendingCoverLocalUri.isNullOrEmpty()
        if (isReviewOrComplete && !hasImage) {
            binding.coverImageSection.visibility = View.GONE
            return
        }
        binding.coverImageSection.visibility = View.VISIBLE
        when {
            !coverImageStorageKey.isNullOrEmpty() -> {
                Glide.with(this).load("${ApiClient.IMAGE_BASE_URL}$coverImageStorageKey").centerCrop()
                    .into(binding.ivCoverImage)
            }
            !pendingCoverLocalUri.isNullOrEmpty() -> {
                Glide.with(this).load(java.io.File(pendingCoverLocalUri!!)).centerCrop()
                    .into(binding.ivCoverImage)
            }
            else -> {
                binding.ivCoverImage.setImageDrawable(
                    ContextCompat.getDrawable(this, R.drawable.svg_img_placeholder)
                )
            }
        }
    }

    private fun showReportCompletedDialog() {
        Utils.showReportCompletedDialog(this) { getReportByIdApi() }
    }

    private fun showCoverImagePopup(anchor: View) {
        val popupBinding = PopupCoverImageMenuBinding.inflate(layoutInflater)
        val popup = PopupWindow(
            popupBinding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        popup.elevation = resources.getDimension(com.intuit.sdp.R.dimen._4sdp)
        popupBinding.tvView.setOnClickListener { popup.dismiss(); viewCoverImage() }
        popupBinding.tvTakeNewPhoto.setOnClickListener {
            popup.dismiss()
            CameraActivity.pendingUris.clear()
            startActivityForResult(Intent(this, CameraActivity::class.java).apply {
                putExtra("isCoverImage", true)
                putExtra("showTimestamp", false)
            }, CAMERA_REQUEST)
        }
        popupBinding.tvDelete.setOnClickListener {
            popup.dismiss()
            val hasImage = !coverImageStorageKey.isNullOrEmpty() || !pendingCoverLocalUri.isNullOrEmpty()
            if (!hasImage) return@setOnClickListener
            Utils.showDialogBox(this, "Delete Cover Image", "Are you sure?") {
                if (Utils.isOnline(this@InventoryListingActivity)) {
                    patchCoverImageApi(null, clearLocalRows = true)
                } else {
                    deleteCoverImageOffline()
                }
            }
        }
        popup.showAsDropDown(
            anchor,
            resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp),
            0
        )
    }

    private fun viewCoverImage() {
        val image: ImageItem = when {
            !coverImageStorageKey.isNullOrEmpty() ->
                ImageItem.Remote("", "${ApiClient.IMAGE_BASE_URL}$coverImageStorageKey")
            !pendingCoverLocalUri.isNullOrEmpty() ->
                ImageItem.Local(android.net.Uri.fromFile(java.io.File(pendingCoverLocalUri!!)))
            else -> return
        }
        Utils.showFullScreenImage(this, listOf(image), 0, "", null)
    }

    private fun patchCoverImageApi(
        storageKey: String?,
        clearLocalRows: Boolean = true,
        onPatched: (() -> Unit)? = null,
        onPatchFailed: (() -> Unit)? = null
    ) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { api ->
                val json =
                    if (storageKey != null) """{"cover_image_storage_key":"$storageKey"}""" else """{"cover_image_storage_key":null}"""
                api.updateReport(reportId, json.toRequestBody("application/json".toMediaType()))
            },
            listener = object : ApiResponseListener<ApiResponse<ReportData>> {
                override fun onSuccess(response: ApiResponse<ReportData>) {
                    lifecycleScope.launch {
                        if (clearLocalRows) clearLocalCoverRows()
                        db.reportDao().updateCoverImage(reportId, storageKey)
                        coverImageStorageKey = storageKey
                        pendingCoverLocalUri = if (clearLocalRows) null else pendingCoverLocalUri
                        updateCoverImageView()
                        onPatched?.invoke()
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    onPatchFailed?.invoke()
                    showToast(errorMessage?.error?.message ?: "Failed")
                }

                override fun onError(throwable: Throwable) {
                    onPatchFailed?.invoke()
                    showToast("Error: ${throwable.message}")
                }
            }
        )
    }

    private suspend fun clearLocalCoverRows() {
        val attachments = db.attachmentDao().getByEntity(reportId, "REPORT_COVER")
        for (att in attachments) {
            att.localUri?.let { db.pendingUploadDao().deleteByLocalUri(it) }
            att.localUri?.let { java.io.File(it).delete() }
            db.attachmentDao().deleteById(att.id)
        }
    }

    private fun deleteCoverImageOffline() {
        lifecycleScope.launch {
            try {
                clearLocalCoverRows()
                // Only schedule a server DELETE if the image was already uploaded (storageKey exists).
                // If still pending, cancelling the pending upload above is sufficient.
                if (!coverImageStorageKey.isNullOrEmpty()) {
                    val serverId = db.reportDao().getById(reportId)?.serverId
                    if (serverId != null) {
                        db.syncQueueDao().enqueue(
                            com.wooma.data.local.entity.SyncQueueEntity(
                                entityType = "REPORT_COVER",
                                operationType = "DELETE",
                                localEntityId = reportId
                            )
                        )
                        SyncScheduler.scheduleImmediateSync(this@InventoryListingActivity)
                    }
                }
                db.reportDao().updateCoverImage(reportId, null)
            } catch (e: Exception) {
                showToast("Failed to delete cover image: ${e.message}")
            }
        }
    }

    companion object {
        private const val TENANT_REQUEST_CODE = 2001
    }
}
