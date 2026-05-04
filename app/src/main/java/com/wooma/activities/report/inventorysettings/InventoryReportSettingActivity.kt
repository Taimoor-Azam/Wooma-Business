package com.wooma.activities.report.inventorysettings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.wooma.activities.BaseActivity
import com.wooma.activities.report.ReportListingActivity
import com.wooma.customs.Utils
import com.wooma.data.network.ApiResponseListener
import com.wooma.data.network.MyApi
import com.wooma.data.network.makeApiRequest
import com.wooma.data.network.showToast
import com.wooma.data.repository.ReportRepository
import com.wooma.databinding.ActivityInventoryReportSettingBinding
import com.wooma.model.ApiResponse
import com.wooma.model.Assessor
import com.wooma.model.ErrorResponse
import com.wooma.model.PropertyReportType
import com.wooma.model.ReportData
import com.wooma.model.enums.ReportTypes
import com.wooma.model.enums.TenantReportStatus
import kotlinx.coroutines.launch

class InventoryReportSettingActivity : BaseActivity() {

    private lateinit var binding: ActivityInventoryReportSettingBinding
    private lateinit var reportRepo: ReportRepository
    var reportStatus = ""
    var reportId = ""
    var propertyId = ""

    var assessor: Assessor? = null
    var reportType: PropertyReportType? = null
    var completionDate = ""

    private val changeDateLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            completionDate = result.data?.getStringExtra("completionDate") ?: completionDate
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityInventoryReportSettingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)
        reportRepo = ReportRepository(this)
        reportStatus = intent.getStringExtra("reportStatus") ?: ""
        reportId = intent.getStringExtra("reportId") ?: ""
        propertyId = intent.getStringExtra("propertyId") ?: ""

        assessor = intent.getParcelableExtra("assessor")
        reportType = intent.getParcelableExtra("reportType")
        completionDate = intent.getStringExtra("completionDate") ?: ""

        if (reportId.isNotEmpty()) {
            binding.tvReportId.text = reportId
        }

        if (reportStatus != TenantReportStatus.IN_PROGRESS.value) {
            binding.reportTypeLayout.visibility = View.GONE
            binding.assessorLayout.visibility = View.GONE
            binding.dateLayout.visibility = View.GONE
        }

        if (reportType?.type_code?.equals(ReportTypes.INSPECTION.value) == true
            || reportType?.type_code?.equals(ReportTypes.CHECK_IN.value) == true
        ) {
            binding.reportTypeLayout.visibility = View.GONE
        }

        binding.ivBack.setOnClickListener { finish() }
        binding.ivCopyReport.setOnClickListener {
            val clipboard: ClipboardManager =
                getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("ReportId", binding.tvReportId.text.toString())
            clipboard.setPrimaryClip(clip)

            showToast("Report Id copied!!")
        }

        binding.duplicateLayout.setOnClickListener {
            if (!Utils.isOnline(this)) {
                showToast("Please connect to internet to continue")
                return@setOnClickListener
            }
            startActivity(Intent(this, DuplicateReportActivity::class.java).putExtra("reportId", reportId))
        }

        binding.reportTypeLayout.setOnClickListener {
            val intent = Intent(this, ChangeReportTypeActivity::class.java)
                .putExtra("reportTypeName", reportType?.display_name)
                .putExtra("reportTypeId", reportType?.id)
                .putExtra("reportId", reportId)
                .putExtra("propertyId", propertyId)
            startActivity(intent)
        }

        binding.assessorLayout.setOnClickListener {
            if (!Utils.isOnline(this)) {
                showToast("Please connect to internet to continue")
                return@setOnClickListener
            }
            startActivity(
                Intent(this, ChangeAssessorActivity::class.java)
                    .putExtra("assessor", assessor)
                    .putExtra("reportId", reportId)
            )
        }

        binding.dateLayout.setOnClickListener {
            if (!Utils.isOnline(this)) {
                showToast("Please connect to internet to continue")
                return@setOnClickListener
            }
            changeDateLauncher.launch(
                Intent(this, ChangeReportDateActivity::class.java)
                    .putExtra("reportId", reportId)
                    .putExtra("completionDate", completionDate)
            )
        }

        binding.archiveReportLayout.setOnClickListener {
            Utils.showDialogBox(this, "Archive Report", "Do you want to archive this report?") {
                if (!Utils.isOnline(this)) {
                    lifecycleScope.launch {
                        reportRepo.archiveOffline(reportId)
                        navigateToReportListing()
                    }
                } else {
                    archiveReportApi()
                }
            }
        }
    }

    private fun navigateToReportListing() {
        startActivity(
            Intent(this, ReportListingActivity::class.java)
                .putExtra("propertyId", propertyId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }

    private fun archiveReportApi() {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.archiveReport(reportId) },
            listener = object : ApiResponseListener<ApiResponse<ReportData>> {
                override fun onSuccess(response: ApiResponse<ReportData>) {
                    if (response.success) {
                        showToast("Report archived successfully")
                        lifecycleScope.launch {
                            reportRepo.markArchivedSynced(reportId)
                            navigateToReportListing()
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
}