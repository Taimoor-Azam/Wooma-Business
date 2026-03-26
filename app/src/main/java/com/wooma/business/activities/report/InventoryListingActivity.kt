package com.wooma.business.activities.report

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.wooma.business.R
import com.wooma.business.activities.BaseActivity
import com.wooma.business.activities.report.complete.CompleteReportActivity
import com.wooma.business.activities.report.EditTenantActivity
import com.wooma.business.activities.report.complete.ExtendTimerActivity
import com.wooma.business.activities.report.inventorysettings.InventoryReportSettingActivity
import com.wooma.business.adapter.InventoryOtherItemsAdapter
import com.wooma.business.adapter.InventoryRoomsAdapter
import com.wooma.business.adapter.ReportTenantsAdapter
import com.wooma.business.customs.AddCustomRoomDialog
import com.wooma.business.customs.GridSpacingItemDecoration
import com.wooma.business.customs.Utils
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.data.network.showToast
import com.wooma.business.databinding.ActivityInventoryListingBinding
import com.wooma.business.model.AddNewRoomsRequest
import com.wooma.business.model.CountItem
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.Assessor
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.PropertyReportType
import com.wooma.business.model.ReportData
import com.wooma.business.model.ReportType
import com.wooma.business.model.RoomsResponse
import com.wooma.business.model.CompleteReportRequest
import com.wooma.business.model.TenantReview
import com.wooma.business.model.enums.ReportTypes
import com.wooma.business.model.enums.TenantReportStatus
import com.wooma.business.model.toCountItemList

class InventoryListingActivity : BaseActivity() {
    private lateinit var adapter: InventoryRoomsAdapter
    private val roomsList = mutableListOf<RoomsResponse>()
    private lateinit var binding: ActivityInventoryListingBinding
    var reportId = ""
    var reportStatus = ""
    var reportType: PropertyReportType? = null
    var reportData: ReportData? = null

    companion object {
        private const val TENANT_REQUEST_CODE = 2001
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == TENANT_REQUEST_CODE && resultCode == android.app.Activity.RESULT_OK) {
            getTenantReviewsApi()
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
            onDeleteRoom = { roomId, position ->
                Utils.showDialogBox(
                    this,
                    "Delete Room",
                    "Are you sure you want to remove this room from the report?"
                ) {
                    deleteRoomApi(roomId ?: "", position)
                }
            }
        )
        binding.rvRooms.adapter = adapter

        updateViewAccToStatus()
        binding.ivBack.setOnClickListener { finish() }

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
        val spacingInPx = spacingInDp * resources.displayMetrics.density.toInt()

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
            )
        }

        binding.btnCompleteReport.setOnClickListener {
            if (reportType?.type_code == ReportTypes.INSPECTION.value) {
                showCompleteInspectionBottomSheet()
            } else {
                startActivity(
                    Intent(this, CompleteReportActivity::class.java).putExtra(
                        "reportId",
                        reportId
                    )
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
            AddCustomRoomDialog().show(
                supportFragmentManager,
                "InputBottomSheet"
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
    }


    private fun updateViewAccToStatus() {
        if (reportStatus == TenantReportStatus.IN_PROGRESS.value) {
            binding.ivAddRoom.visibility = View.VISIBLE
            binding.btnCompleteReport.visibility = View.VISIBLE
        } else {
            binding.ivAddRoom.visibility = View.GONE
            binding.btnCompleteReport.visibility = View.GONE

        }
    }

    override fun onResume() {
        super.onResume()
        getReportByIdApi()
    }

    /* private fun loadProperties() {
         roomsList.addAll(
             listOf(
                 Rooms("Meter"),
                 Rooms("Keys"),
                 Rooms("Detectors"),
                 Rooms("Checklist")
             )
         )
         adapter.updateList(roomsList)
     }*/

    private fun deleteRoomApi(roomId: String, position: Int) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.deleteRoom(reportId, roomId) },
            listener = object : ApiResponseListener<ApiResponse<Any>> {
                override fun onSuccess(response: ApiResponse<Any>) {
                    roomsList.removeAt(position)
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

    private fun addNewRoomApi(request: AddNewRoomsRequest) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.addRomToReport(reportId, request) },
            listener = object : ApiResponseListener<ApiResponse<ReportData>> {
                override fun onSuccess(response: ApiResponse<ReportData>) {
                    if (response.success) {
                    } else {
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    // Handle API error
//                    Log.e("API", errorMessage?.error?.message ?: "")
//                    showToast(errorMessage?.error?.message ?: "")
                }

                override fun onError(throwable: Throwable) {
                    // Handle network error
//                    Log.e("API", "Error: ${throwable.message}")
//                    showToast("Error: ${throwable.message}")
                }
            }
        )
    }

    private fun getReportByIdApi() {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.getReportById(reportId, true, true) },
            listener = object : ApiResponseListener<ApiResponse<ReportData>> {
                override fun onSuccess(response: ApiResponse<ReportData>) {
                    if (response.success) {
                        reportData = response.data
                        roomsList.clear()
                        roomsList.addAll(response.data.rooms ?: ArrayList())
                        adapter.updateList(roomsList)

                        val allItems = response.data.counts.toCountItemList()
                        val isInspection = reportType?.type_code == ReportTypes.INSPECTION.value
                        val otherItems: MutableList<CountItem> = if (isInspection)
                            allItems.filter { it.label == "Checklists" }.toMutableList()
                        else
                            allItems
                        (binding.rvOtherItems.layoutManager as? androidx.recyclerview.widget.GridLayoutManager)
                            ?.spanCount = if (otherItems.size == 1) 1 else 2
                        binding.rvOtherItems.adapter =
                            InventoryOtherItemsAdapter(
                                this@InventoryListingActivity,
                                otherItems,
                                reportId,
                                response.data.status ?: ""
                            )

                        if (response.data.status == TenantReportStatus.TENANT_REVIEW.value) {
                            getTenantReviewsApi()
                            binding.tvDate.text = "Tenant Review"

                            binding.tvDate.setTextColor(
                                ContextCompat.getColor(
                                    this@InventoryListingActivity,
                                    R.color.blue
                                )
                            )

                            binding.tvDate.background = ContextCompat.getDrawable(
                                this@InventoryListingActivity,
                                R.drawable.bg_report_status
                            )
                        } else if (response.data.status == TenantReportStatus.COMPLETED.value) {
                            binding.tvDate.text = "Completed"
                            binding.tvDate.setTextColor(
                                ContextCompat.getColor(
                                    this@InventoryListingActivity,
                                    R.color.green
                                )
                            )

                            binding.tvDate.background = ContextCompat.getDrawable(
                                this@InventoryListingActivity,
                                R.drawable.bg_report_status
                            )

                            updateViewForCompletedReport()
                        }
                    } else {
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    // Handle API error
                    Log.e("API", errorMessage?.error?.message ?: "")
                    showToast(errorMessage?.error?.message ?: "")
                }

                override fun onError(throwable: Throwable) {
                    // Handle network error
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
                        updateViewForTenantReview(response.data)
                    } else {
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    // Handle API error
                    Log.e("API", errorMessage?.error?.message ?: "")
                    showToast(errorMessage?.error?.message ?: "")
                }

                override fun onError(throwable: Throwable) {
                    // Handle network error
                    Log.e("API", "Error: ${throwable.message}")
                    showToast("Error: ${throwable.message}")
                }
            }
        )
    }

    private fun updateViewForCompletedReport() {
        binding.completedReportLayout.visibility = View.VISIBLE
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateViewForTenantReview(data: ArrayList<TenantReview>) {
        binding.tenantReviewLayout.visibility = View.VISIBLE
        val adapter = ReportTenantsAdapter(
            context = this,
            originalList = data,
            reportId = reportId,
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
    }

    private fun showCompleteInspectionBottomSheet() {
        val bottomSheet = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.bottom_sheet_complete_inspection, null)
        bottomSheet.setContentView(sheetView)

        val cbConfirm = sheetView.findViewById<android.widget.CheckBox>(R.id.cbConfirm)
        val btnComplete = sheetView.findViewById<android.widget.Button>(R.id.btnCompleteReport)
        val ivClose = sheetView.findViewById<android.widget.ImageView>(R.id.ivClose)

        ivClose.setOnClickListener { bottomSheet.dismiss() }

        cbConfirm.setOnCheckedChangeListener { _, isChecked ->
            btnComplete.isEnabled = isChecked
            btnComplete.alpha = if (isChecked) 1f else 0.5f
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
                        finish()
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
}