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
import com.wooma.customs.GridSpacingItemDecoration
import com.wooma.customs.Utils
import com.wooma.data.local.WoomaDatabase
import com.wooma.data.local.entity.SyncStatus
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
import com.wooma.model.toCountItemList
import com.wooma.model.ImageItem
import com.wooma.sync.SyncScheduler
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Locale

class InventoryListingActivity : BaseActivity() {
    private lateinit var adapter: InventoryRoomsAdapter
    private val roomsList = mutableListOf<RoomsResponse>()
    private lateinit var binding: ActivityInventoryListingBinding
    var reportId = ""
    var propertyId = ""
    var reportStatus = ""
    var reportType: PropertyReportType? = null
    var reportData: ReportData? = null
    private var coverImageStorageKey: String? = null
    private var pdfUrl: String? = null
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
            handleCoverImageOffline(uri)
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
                Utils.showDialogBox(this, "Delete Room", "Are you sure you want to remove this room?") {
                    lifecycleScope.launch { roomRepo.deleteRoom(roomId ?: "") }
                }
            },
            onReorder = { roomId, prevRank, nextRank ->
                lifecycleScope.launch { roomRepo.reorderRoom(roomId, prevRank, nextRank) }
            },
            onUpdateRoom = { roomId, newName ->
                lifecycleScope.launch { roomRepo.updateRoomName(roomId, newName) }
            }
        )
        binding.rvRooms.adapter = adapter

        setupSyncGate()
        observeData()

        val touchCallback = object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder) =
                if (adapter.isEditMode) makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
                else makeMovementFlags(0, 0)

            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
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
            adapter.setEditMode(editMode)
            binding.tvEditRooms.text = if (editMode) "Done" else "Edit"
        }

        binding.tvExtendTime.setOnClickListener {
            startActivity(Intent(this, ExtendTimerActivity::class.java).apply {
                putExtra("reportId", reportId)
                putExtra("expiryDate", reportData?.extendReviewExpiry ?: reportData?.tenantReviewExpiry)
            })
        }

        binding.rvOtherItems.addItemDecoration(GridSpacingItemDecoration(2, (16 * resources.displayMetrics.density).toInt(), true))

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
            if (coverImageStorageKey.isNullOrEmpty()) {
                CameraActivity.pendingUris.clear()
                startActivityForResult(Intent(this, CameraActivity::class.java).apply {
                    putExtra("isCoverImage", true)
                    putExtra("showTimestamp", reportData?.showTimestamp ?: true)
                }, CAMERA_REQUEST)
            } else {
                showCoverImagePopup(it)
            }
        }

        binding.btnCompleteReport.setOnClickListener {
            when (reportType?.type_code) {
                ReportTypes.INSPECTION.value, ReportTypes.CHECK_OUT.value -> showCompleteInspectionBottomSheet()
                else -> startActivity(Intent(this, CompleteReportActivity::class.java).putExtra("reportId", reportId))
            }
        }

        binding.addAnotherTenantLayout.setOnClickListener {
            startActivityForResult(Intent(this, EditTenantActivity::class.java).apply {
                putExtra("isEditMode", false)
                putExtra("reportId", reportId)
            }, TENANT_REQUEST_CODE)
        }

        binding.ivAddRoom.setOnClickListener {
            startActivity(Intent(this, SelectRoomActivity::class.java).putExtra("reportId", reportId))
        }

        supportFragmentManager.setFragmentResultListener("sheet_key", this) { _, bundle ->
            val value = bundle.getString("added_room") ?: return@setFragmentResultListener
            lifecycleScope.launch { roomRepo.addRoom(reportId, value) }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { navigateToReportListing() }
        })
    }

    private fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                roomRepo.observeRooms(reportId).collectLatest { rooms ->
                    roomsList.clear()
                    roomsList.addAll(rooms)
                    adapter.updateList(roomsList)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                db.reportDao().observeById(reportId).collectLatest { entity ->
                    entity?.let {
                        coverImageStorageKey = it.coverImageStorageKey
                        updateCoverImageView()
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
                    com.wooma.sync.ConnectivityObserver(this@InventoryListingActivity).observeConnectivity()
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
    }

    private fun handleCoverImageOffline(uri: Uri) {
        lifecycleScope.launch {
            try {
                val report = db.reportDao().getById(reportId)
                attachmentRepo.saveLocalAttachment(uri, reportId, report?.serverId, "REPORT_COVER")
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
        getReportByIdApi()
        lifecycleScope.launch { try { roomRepo.refreshRooms(reportId) } catch (_: Exception) {} }
    }

    private fun getReportByIdApi(showLoading: Boolean = true) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = showLoading,
            requestAction = { apiService -> apiService.getReportById(reportId, true, true) },
            listener = object : ApiResponseListener<ApiResponse<ReportData>> {
                override fun onSuccess(response: ApiResponse<ReportData>) {
                    if (response.success) {
                        reportData = response.data
                        reportStatus = response.data.status
                        updateViewAccToStatus()

                        val apiReportType = response.data.reportType
                        if (reportType == null) {
                            reportType = PropertyReportType(apiReportType.id, apiReportType.display_name, apiReportType.type_code)
                            adapter.reportType = reportType
                            binding.tvReportType.text = apiReportType.display_name
                        }

                        coverImageStorageKey = response.data.coverImageStorageKey
                        updateCoverImageView()
                        
                        adapter.showTimestamp = response.data.showTimestamp ?: true

                        val allItems = response.data.counts.toCountItemList()
                        val isInspection = reportType?.type_code == ReportTypes.INSPECTION.value
                        val otherItems = if (isInspection) allItems.filter { it.label == "Checklist" } else allItems
                        (binding.rvOtherItems.layoutManager as? GridLayoutManager)?.spanCount = if (otherItems.size == 1) 1 else 2
                        binding.rvOtherItems.adapter = InventoryOtherItemsAdapter(this@InventoryListingActivity, ArrayList(otherItems), reportId, response.data.status, response.data.showTimestamp ?: true)

                        val rawDate = response.data.completionDate
                        if (rawDate.isNotEmpty()) {
                            try {
                                val inSdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                                val outSdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                                inSdf.parse(rawDate)?.let { binding.tvDate.text = outSdf.format(it) }
                            } catch (_: Exception) {}
                        }

                        if (response.data.status == TenantReportStatus.TENANT_REVIEW.value) {
                            getTenantReviewsApi()
                        } else if (response.data.status == TenantReportStatus.COMPLETED.value) {
                            val blankCount = response.data.blankSpacesCount
                            if (blankCount != 0 && reportType?.type_code != ReportTypes.CHECK_OUT.value) {
                                binding.tvBlankSpaces.visibility = View.VISIBLE
                                binding.tvBlankSpaces.text = "Completed with $blankCount blank signature spaces"
                            }
                            pdfUrl = response.data.pdfUrl?.let { "${ApiClient.IMAGE_BASE_URL}$it" }
                            updateViewForCompletedReport()
                            getTenantReviewsApi()
                        }
                    }
                }
                override fun onFailure(errorMessage: ErrorResponse?) { showToast(errorMessage?.error?.message ?: "") }
                override fun onError(throwable: Throwable) { showToast("Error: ${throwable.message}") }
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
                        if (reportStatus == TenantReportStatus.COMPLETED.value) updateCompletedWithTenants(response.data)
                        else updateViewForTenantReview(response.data)
                    }
                }
                override fun onFailure(errorMessage: ErrorResponse?) { showToast(errorMessage?.error?.message ?: "") }
                override fun onError(throwable: Throwable) { showToast("Error: ${throwable.message}") }
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
        binding.tvReviewPeriodEnded.text = "Review period ended with $count of ${data.size} signatures"
        binding.tvCompletedTenantSignatures.text = "Tenant signatures ($count of ${data.size})"
        binding.rvCompletedTenants.adapter = ReportTenantsAdapter(this, data, reportStatus = TenantReportStatus.COMPLETED.value)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateViewForTenantReview(data: ArrayList<TenantReview>) {
        binding.tenantReviewLayout.visibility = View.VISIBLE
        binding.rvTenants.adapter = ReportTenantsAdapter(this, data, reportId = reportId, reportStatus = TenantReportStatus.TENANT_REVIEW.value, onTenantClick = { tenant ->
            startActivityForResult(Intent(this, EditTenantActivity::class.java).apply {
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
        val expiryDate = reportData?.extendReviewExpiry ?: reportData?.tenantReviewExpiry
        binding.daysRemaining.text = "${Utils.getDaysDifference(expiryDate ?: "")} days remaining"
        val count = data.count { it.is_submitted }
        binding.signProgress.max = data.size
        binding.signProgress.progress = count
        binding.tvReceivedSigns.text = "${count}/${data.size}"
        binding.tvTotalTenants.text = "Tenant (${data.size})"
        binding.tvCancelSignatureRequest.setOnClickListener {
            Utils.showDialogBox(this, "Cancel Signature Request", "Are you sure you want to cancel?") { cancelSignatureRequestApi() }
        }
    }

    private fun cancelSignatureRequestApi() {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { api -> api.cancelSignatureRequest(reportId) },
            listener = object : ApiResponseListener<ApiResponse<Any>> {
                override fun onSuccess(response: ApiResponse<Any>) { showToast("Cancelled"); getReportByIdApi() }
                override fun onFailure(errorMessage: ErrorResponse?) { showToast(errorMessage?.error?.message ?: "Failed") }
                override fun onError(throwable: Throwable) { showToast("Error: ${throwable.message}") }
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
                    override fun onSuccess(response: ApiResponse<ReportData>) { bottomSheet.dismiss(); showReportCompletedDialog() }
                    override fun onFailure(errorMessage: ErrorResponse?) { showToast(errorMessage?.error?.message ?: "Failed") }
                    override fun onError(throwable: Throwable) { showToast("Error: ${throwable.message}") }
                }
            )
        }
        bottomSheet.show()
    }

    private fun updateCoverImageView() {
        val isReviewOrComplete = reportStatus == TenantReportStatus.TENANT_REVIEW.value || reportStatus == TenantReportStatus.COMPLETED.value
        if (isReviewOrComplete && coverImageStorageKey.isNullOrEmpty()) {
            binding.coverImageSection.visibility = View.GONE
            return
        }
        binding.coverImageSection.visibility = View.VISIBLE
        if (!coverImageStorageKey.isNullOrEmpty()) {
            Glide.with(this).load("${ApiClient.IMAGE_BASE_URL}$coverImageStorageKey").centerCrop().into(binding.ivCoverImage)
        } else {
            binding.ivCoverImage.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.svg_img_placeholder))
        }
    }

    private fun showReportCompletedDialog() { Utils.showReportCompletedDialog(this) { getReportByIdApi() } }

    private fun showCoverImagePopup(anchor: View) {
        val popupBinding = PopupCoverImageMenuBinding.inflate(layoutInflater)
        val popup = PopupWindow(popupBinding.root, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.elevation = resources.getDimension(com.intuit.sdp.R.dimen._4sdp)
        popupBinding.tvView.setOnClickListener { popup.dismiss(); viewCoverImage() }
        popupBinding.tvTakeNewPhoto.setOnClickListener {
            popup.dismiss()
            CameraActivity.pendingUris.clear()
            startActivityForResult(Intent(this, CameraActivity::class.java).apply {
                putExtra("isCoverImage", true)
                putExtra("showTimestamp", reportData?.showTimestamp ?: true)
            }, CAMERA_REQUEST)
        }
        popupBinding.tvDelete.setOnClickListener {
            popup.dismiss()
            if (coverImageStorageKey.isNullOrEmpty()) return@setOnClickListener
            Utils.showDialogBox(this, "Delete Cover Image", "Are you sure?") { patchCoverImageApi(null) }
        }
        popup.showAsDropDown(anchor, resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp), 0)
    }

    private fun viewCoverImage() {
        val url = "${ApiClient.IMAGE_BASE_URL}$coverImageStorageKey".takeIf { !coverImageStorageKey.isNullOrEmpty() } ?: return
        Utils.showFullScreenImage(this, listOf(ImageItem.Remote("", url)), 0, "", null)
    }

    private fun patchCoverImageApi(storageKey: String?) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { api ->
                val json = if (storageKey != null) """{"cover_image_storage_key":"$storageKey"}""" else """{"cover_image_storage_key":null}"""
                api.updateReport(reportId, json.toRequestBody("application/json".toMediaType()))
            },
            listener = object : ApiResponseListener<ApiResponse<ReportData>> {
                override fun onSuccess(response: ApiResponse<ReportData>) {
                    coverImageStorageKey = storageKey
                    updateCoverImageView()
                }
                override fun onFailure(errorMessage: ErrorResponse?) { showToast(errorMessage?.error?.message ?: "Failed") }
                override fun onError(throwable: Throwable) { showToast("Error: ${throwable.message}") }
            }
        )
    }

    companion object {
        private const val TENANT_REQUEST_CODE = 2001
    }
}
