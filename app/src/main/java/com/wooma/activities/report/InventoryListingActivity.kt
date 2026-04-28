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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
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
import com.wooma.data.network.ApiClient
import com.wooma.data.network.ApiResponseListener
import com.wooma.data.network.MyApi
import com.wooma.data.network.makeApiRequest
import com.wooma.data.network.showToast
import com.wooma.databinding.ActivityInventoryListingBinding
import com.wooma.databinding.PopupCoverImageMenuBinding
import com.wooma.model.AddNewRoomsRequest
import com.wooma.model.ApiResponse
import com.wooma.model.CompleteReportRequest
import com.wooma.model.CountItem
import com.wooma.model.ErrorResponse
import com.wooma.model.PropertyReportType
import com.wooma.model.ReorderRoomRequest
import com.wooma.model.ReportData
import com.wooma.model.RoomsResponse
import com.wooma.model.TenantReview
import com.wooma.model.UpdateRoomNameRequest
import com.wooma.model.enums.ReportTypes
import com.wooma.model.enums.TenantReportStatus
import com.wooma.model.toCountItemList
import com.wooma.model.ImageItem
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

    companion object {
        private const val TENANT_REQUEST_CODE = 2001
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == TENANT_REQUEST_CODE && resultCode == RESULT_OK) {
            getTenantReviewsApi()
        }
        if (requestCode == CAMERA_REQUEST && resultCode == RESULT_OK) {
            val uri = CameraActivity.pendingUris.firstOrNull() ?: return
            uploadCoverImageApi(uri)
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
            onDeleteRoom = { roomId ->
                Utils.showDialogBox(
                    this,
                    "Delete Room",
                    "Are you sure you want to remove this room from the report?"
                ) {
                    deleteRoomApi(roomId ?: "")
                }
            },
            onReorder = { roomId, prevRank, nextRank ->
                reorderRoomApi(roomId, prevRank, nextRank)
            },
            onUpdateRoom = { roomId, newName ->
                updateRoomNameApi(roomId, newName)
            }
        )
        binding.rvRooms.adapter = adapter

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
            startActivity(
                Intent(this, ExtendTimerActivity::class.java).putExtra(
                    "reportId",
                    reportId
                ).putExtra(
                    "expiryDate",
                    if (reportData?.extendReviewExpiry != null) reportData?.extendReviewExpiry else reportData?.tenantReviewExpiry
                )
            )
        }

        val spacingInDp = 16
        val spacingInPx = (spacingInDp * resources.displayMetrics.density).toInt()

        binding.rvOtherItems.addItemDecoration(
            GridSpacingItemDecoration(
                spanCount = 2,
                spacing = spacingInPx,
                includeEdge = true
            )
        )

        binding.ivSettings.setOnClickListener {
            startActivity(
                Intent(
                    this,
                    InventoryReportSettingActivity::class.java
                ).putExtra("reportStatus", reportStatus)
                    .putExtra("reportId", reportId)
                    .putExtra("reportType", reportType)
                    .putExtra("assessor", reportData?.assessor)
                    .putExtra("completionDate", reportData?.completionDate)
                    .putExtra("propertyId", propertyId)
            )
        }

        binding.coverImageSection.setOnClickListener {
            if (reportStatus == TenantReportStatus.TENANT_REVIEW.value ||
                reportStatus == TenantReportStatus.COMPLETED.value) {
                if (!coverImageStorageKey.isNullOrEmpty()) viewCoverImage()
                return@setOnClickListener
            }
            if (reportStatus != TenantReportStatus.IN_PROGRESS.value) return@setOnClickListener
            if (coverImageStorageKey.isNullOrEmpty()) {
                CameraActivity.pendingUris.clear()
                startActivityForResult(
                    Intent(this, CameraActivity::class.java).putExtra("isCoverImage", true),
                    CAMERA_REQUEST
                )
            } else {
                showCoverImagePopup(it)
            }
        }

        binding.btnCompleteReport.setOnClickListener {
            when (reportType?.type_code) {
                ReportTypes.INSPECTION.value, ReportTypes.CHECK_OUT.value -> showCompleteInspectionBottomSheet()
                else -> startActivity(
                    Intent(this, CompleteReportActivity::class.java).putExtra("reportId", reportId)
                )
            }
        }

        binding.addAnotherTenantLayout.setOnClickListener {
            startActivityForResult(
                Intent(this, EditTenantActivity::class.java)
                    .putExtra("isEditMode", false)
                    .putExtra("reportId", reportId),
                TENANT_REQUEST_CODE
            )
        }

        binding.ivAddRoom.setOnClickListener {
            startActivity(
                Intent(this, SelectRoomActivity::class.java)
                    .putExtra("reportId", reportId)
            )
        }

        supportFragmentManager.setFragmentResultListener(
            "sheet_key",
            this
        ) { requestKey, bundle ->

            val value = bundle.getString("added_room")
            roomsList.add(0, RoomsResponse("", "", value ?: "", "", true, ArrayList()))
            println(value)

            val request = AddNewRoomsRequest(
                rooms = listOf(value ?: "")
            )

            addNewRoomApi(request)
            adapter.updateList(roomsList)
        }

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateToReportListing()
                // If you want default behavior after your logic:
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        onBackPressedDispatcher.addCallback(this, callback)
    }

    private fun navigateToReportListing() {
        val intent = Intent(this, ReportListingActivity::class.java)
        intent.putExtra("propertyId", propertyId)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
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
    }

    private fun updateRoomNameApi(roomId: String, newName: String) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { api -> api.updateRoomName(reportId, roomId,
                UpdateRoomNameRequest(newName)
            ) },
            listener = object : ApiResponseListener<ApiResponse<RoomsResponse>> {
                override fun onSuccess(response: ApiResponse<RoomsResponse>) {
                    if (response.success) {
                        showToast("Room name updated")
                        getReportByIdApi()
                    }
                }
                override fun onFailure(errorMessage: ErrorResponse?) {
                    showToast(errorMessage?.error?.message ?: "Failed to update room name")
                }
                override fun onError(throwable: Throwable) {
                    showToast("Error: ${throwable.message}")
                }
            }
        )
    }

    private fun deleteRoomApi(roomId: String) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.deleteRoom(reportId, roomId) },
            listener = object : ApiResponseListener<ApiResponse<Any>> {
                override fun onSuccess(response: ApiResponse<Any>) {
                    roomsList.removeAll { it.id == roomId }
                    adapter.updateList(roomsList)
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    showToast(errorMessage?.error?.message ?: "Failed to delete room")
                }

                override fun onError(throwable: Throwable) {
                    showToast("Error: ${throwable.message}")
                }
            }
        )
    }

    private fun reorderRoomApi(roomId: String, prevRank: String?, nextRank: String?) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = false,
            requestAction = { api ->
                api.reorderRoom(reportId, roomId,
                    ReorderRoomRequest(prev_rank = prevRank, next_rank = nextRank)
                )
            },
            listener = object : ApiResponseListener<ApiResponse<Any>> {
                override fun onSuccess(response: ApiResponse<Any>) {
                    getReportByIdApi(false)
                }
                override fun onFailure(errorMessage: ErrorResponse?) {
                    showToast(errorMessage?.error?.message ?: "Failed to reorder room")
                }
                override fun onError(throwable: Throwable) {
                    showToast("Error: ${throwable.message}")
                }
            }
        )
    }

    private fun addNewRoomApi(request: AddNewRoomsRequest) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.addRomToReport(reportId, request) },
            listener = object : ApiResponseListener<ApiResponse<ArrayList<ReportData>>> {
                override fun onSuccess(response: ApiResponse<ArrayList<ReportData>>) {
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                }

                override fun onError(throwable: Throwable) {
                }
            }
        )
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
                            reportType = PropertyReportType(
                                id = apiReportType.id,
                                display_name = apiReportType.display_name,
                                type_code = apiReportType.type_code
                            )
                            adapter.reportType = reportType
                            binding.tvReportType.text = apiReportType.display_name
                        }

                        coverImageStorageKey = response.data.coverImageStorageKey
                        updateCoverImageView()
                        roomsList.clear()
                        roomsList.addAll(response.data.rooms ?: ArrayList())
                        roomsList.sortWith(compareBy(nullsLast(naturalOrder())) { it.displayOrder })
                        adapter.updateList(roomsList)

                        val allItems = response.data.counts.toCountItemList()
                        val isInspection = reportType?.type_code == ReportTypes.INSPECTION.value
                        val otherItems: MutableList<CountItem> = if (isInspection)
                            allItems.filter { it.label == "Checklist" }.toMutableList()
                        else
                            allItems
                        (binding.rvOtherItems.layoutManager as? GridLayoutManager)
                            ?.spanCount = if (otherItems.size == 1) 1 else 2
                        binding.rvOtherItems.adapter =
                            InventoryOtherItemsAdapter(
                                this@InventoryListingActivity,
                                otherItems,
                                reportId,
                                response.data.status
                            )

                        val rawDate = response.data.completionDate
                        if (rawDate.isNotEmpty()) {
                            try {
                                val inSdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                                val outSdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                                val parsed = inSdf.parse(rawDate)
                                if (parsed != null) binding.tvDate.text = outSdf.format(parsed)
                            } catch (_: Exception) {}
                        }

                        if (response.data.status == TenantReportStatus.TENANT_REVIEW.value) {
                            getTenantReviewsApi()
                        } else if (response.data.status == TenantReportStatus.COMPLETED.value) {

                            val blankCount = response.data.blankSpacesCount
                            val isCheckout = reportType?.type_code == ReportTypes.CHECK_OUT.value
                            if (blankCount != 0 && !isCheckout) {
                                binding.tvBlankSpaces.visibility = View.VISIBLE
                                binding.tvBlankSpaces.text = "Completed with $blankCount blank signature spaces"
                            }

                            pdfUrl = response.data.pdfUrl?.let { "${ApiClient.IMAGE_BASE_URL}$it" }
                            updateViewForCompletedReport()
                            getTenantReviewsApi()
                        }
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    Log.e("API", errorMessage?.error?.message ?: "")
                    showToast(errorMessage?.error?.message ?: "")
                }

                override fun onError(throwable: Throwable) {
                    Log.e("API", "Error: ${throwable.message}")
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
                        if (reportStatus == TenantReportStatus.COMPLETED.value) {
                            updateCompletedWithTenants(response.data)
                        } else {
                            updateViewForTenantReview(response.data)
                        }
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    Log.e("API", errorMessage?.error?.message ?: "")
                    showToast(errorMessage?.error?.message ?: "")
                }

                override fun onError(throwable: Throwable) {
                    Log.e("API", "Error: ${throwable.message}")
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
                startActivity(
                    Intent(this, PdfDownloadActivity::class.java)
                        .putExtra(PdfDownloadActivity.EXTRA_PDF_URL, pdfUrl)
                        .putExtra(PdfDownloadActivity.EXTRA_REPORT_ID, reportId)
                )
            }
        }
    }

    private fun updateCompletedWithTenants(data: ArrayList<TenantReview>) {
        if (data.isEmpty()) return
        val count = data.count { it.is_submitted }
        binding.completedTenantSection.visibility = View.VISIBLE
        binding.tvReviewPeriodEnded.text = "Review period ended with $count of ${data.size} signatures"
        binding.tvCompletedTenantSignatures.text = "Tenant signatures ($count of ${data.size})"
        binding.rvCompletedTenants.adapter = ReportTenantsAdapter(
            context = this,
            originalList = data,
            reportStatus = TenantReportStatus.COMPLETED.value
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateViewForTenantReview(data: ArrayList<TenantReview>) {
        binding.tenantReviewLayout.visibility = View.VISIBLE
        val adapter = ReportTenantsAdapter(
            context = this,
            originalList = data,
            reportId = reportId,
            reportStatus = TenantReportStatus.TENANT_REVIEW.value,
            onTenantClick = { tenant ->
                startActivityForResult(
                    Intent(this, EditTenantActivity::class.java)
                        .putExtra("isEditMode", true)
                        .putExtra("reportId", reportId)
                        .putExtra("tenantReviewId", tenant.id)
                        .putExtra("firstName", tenant.first_name)
                        .putExtra("lastName", tenant.last_name)
                        .putExtra("email", tenant.email_address)
                        .putExtra("mobileNumber", tenant.mobile_number)
                        .putExtra("tenantCount", data.size),
                    TENANT_REQUEST_CODE
                )
            }
        )
        binding.rvTenants.adapter = adapter

        val expiryDate =
            if (reportData?.extendReviewExpiry != null) reportData?.extendReviewExpiry else reportData?.tenantReviewExpiry
        binding.daysRemaining.text = Utils.getDaysDifference(expiryDate ?: "")
            .toString() + " days remaining"
        val count = data.count { it.is_submitted }

        binding.signProgress.max = data.size
        binding.signProgress.progress = count
        binding.tvReceivedSigns.text = "${count}/${data.size}"
        binding.tvTotalTenants.text = "Tenant (${data.size})"

        binding.tvCancelSignatureRequest.setOnClickListener {
            Utils.showDialogBox(
                this,
                "Cancel Signature Request",
                "Are you sure you want to cancel the tenant review signature request?"
            ) {
                cancelSignatureRequestApi()
            }
        }
    }

    private fun cancelSignatureRequestApi() {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { api -> api.cancelSignatureRequest(reportId) },
            listener = object : ApiResponseListener<ApiResponse<Any>> {
                override fun onSuccess(response: ApiResponse<Any>) {
                    showToast("Signature request cancelled")
                    getReportByIdApi()
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    showToast(errorMessage?.error?.message ?: "Failed to cancel signature request")
                }

                override fun onError(throwable: Throwable) {
                    showToast("Error: ${throwable.message}")
                }
            }
        )
    }

    private fun completeCheckoutReportApi() {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { api ->
                api.completeReport(
                    id = reportId,
                    request = CompleteReportRequest(blank_spaces_count = 0)
                )
            },
            listener = object : ApiResponseListener<ApiResponse<ReportData>> {
                override fun onSuccess(response: ApiResponse<ReportData>) {
                    if (response.success) {
                        showReportCompletedDialog()
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    showToast(errorMessage?.error?.message ?: "Failed to complete report")
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
                requestAction = { api ->
                    api.completeReport(
                        id = reportId,
                        request = CompleteReportRequest(blank_spaces_count = 0)
                    )
                },
                listener = object : ApiResponseListener<ApiResponse<ReportData>> {
                    override fun onSuccess(response: ApiResponse<ReportData>) {
                        bottomSheet.dismiss()
                        showReportCompletedDialog()
                    }

                    override fun onFailure(errorMessage: ErrorResponse?) {
                        showToast(errorMessage?.error?.message ?: "Failed to complete report")
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
        val isReviewOrComplete = reportStatus == TenantReportStatus.TENANT_REVIEW.value ||
                reportStatus == TenantReportStatus.COMPLETED.value
        if (isReviewOrComplete && coverImageStorageKey.isNullOrEmpty()) {
            binding.coverImageSection.visibility = View.GONE
            return
        }
        binding.coverImageSection.visibility = View.VISIBLE
        if (!coverImageStorageKey.isNullOrEmpty()) {
            Glide.with(this)
                .load("${ApiClient.IMAGE_BASE_URL}$coverImageStorageKey")
                .centerCrop()
                .into(binding.ivCoverImage)
        } else {
            binding.ivCoverImage.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.svg_img_placeholder)
            )
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

        popupBinding.tvView.setOnClickListener {
            popup.dismiss()
            viewCoverImage()
        }
        popupBinding.tvTakeNewPhoto.setOnClickListener {
            popup.dismiss()
            CameraActivity.pendingUris.clear()
            startActivityForResult(
                Intent(this, CameraActivity::class.java).putExtra("isCoverImage", true),
                CAMERA_REQUEST
            )
        }
        popupBinding.tvDelete.setOnClickListener {
            popup.dismiss()
            if (coverImageStorageKey.isNullOrEmpty()) return@setOnClickListener
            Utils.showDialogBox(
                this,
                "Delete Cover Image",
                "Are you sure you want to delete the cover image?"
            ) {
                patchCoverImageApi(null)
            }
        }

        val xOffset = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp)
        popup.showAsDropDown(anchor, xOffset, 0)
    }

    private fun viewCoverImage() {
        val url = "${ApiClient.IMAGE_BASE_URL}$coverImageStorageKey".takeIf { !coverImageStorageKey.isNullOrEmpty() } ?: return
        Utils.showFullScreenImage(
            context = this,
            images = listOf(ImageItem.Remote("", url)),
            startPosition = 0,
            title = "",
            onDelete = null
        )
    }

    private fun uploadCoverImageApi(uri: Uri) {
        AttachmentUploadHelper.uploadForStorageKey(
            activity = this,
            uri = uri,
            onSuccess = { storageKey -> patchCoverImageApi(storageKey) },
            onError = { msg -> showToast("Upload failed: $msg") }
        )
    }

    private fun patchCoverImageApi(storageKey: String?) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { api ->
                val json = if (storageKey != null)
                    """{"cover_image_storage_key":"$storageKey"}"""
                else
                    """{"cover_image_storage_key":null}"""
                val body = json.toRequestBody("application/json".toMediaType())
                api.updateReport(reportId, body)
            },
            listener = object : ApiResponseListener<ApiResponse<ReportData>> {
                override fun onSuccess(response: ApiResponse<ReportData>) {
                    coverImageStorageKey = storageKey
                    updateCoverImageView()
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    showToast(errorMessage?.error?.message ?: "Failed to update cover image")
                }

                override fun onError(throwable: Throwable) {
                    showToast("Error: ${throwable.message}")
                }
            }
        )
    }
}
