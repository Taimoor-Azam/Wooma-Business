package com.wooma.business.activities.report.inventorysettings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import com.wooma.business.activities.BaseActivity
import com.wooma.business.activities.MainActivity
import com.wooma.business.customs.Utils
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.data.network.showToast
import com.wooma.business.databinding.ActivityInventoryReportSettingBinding
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.ReportData
import com.wooma.business.model.enums.TenantReportStatus

class InventoryReportSettingActivity : BaseActivity() {

    private lateinit var binding: ActivityInventoryReportSettingBinding
    var reportStatus = ""
    var reportId = ""

    var reportTypeName = ""
    var reportTypeId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityInventoryReportSettingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)
        reportStatus = intent.getStringExtra("reportStatus") ?: ""
        reportId = intent.getStringExtra("reportId") ?: ""

        reportTypeName = intent.getStringExtra("reportTypeName") ?: ""
        reportTypeId = intent.getStringExtra("reportTypeId") ?: ""

        if (reportId.isNotEmpty()) {
            binding.tvReportId.text = reportId
        }

        if (reportStatus != TenantReportStatus.IN_PROGRESS.value) {
            binding.reportTypeLayout.visibility = View.GONE
            binding.assessorLayout.visibility = View.GONE
            binding.dateLayout.visibility = View.GONE
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
            val intent =
                Intent(this, DuplicateReportActivity::class.java).putExtra("reportId", reportId)
            startActivity(intent)
        }

        binding.reportTypeLayout.setOnClickListener {
            val intent = Intent(this, ChangeReportTypeActivity::class.java).putExtra(
                "reportTypeName",
                reportTypeName
            )
                .putExtra("reportTypeId", reportTypeId)
                .putExtra("reportId", reportId)
            startActivity(intent)
        }
        binding.assessorLayout.setOnClickListener {
            val intent = Intent(this, ChangeAssessorActivity::class.java)
            startActivity(intent)
        }
        binding.dateLayout.setOnClickListener {
            val intent = Intent(this, ChangeReportDateActivity::class.java)
            startActivity(intent)
        }
        binding.archiveReportLayout.setOnClickListener {
            Utils.showDialogBox(this, "Archive Report", "Do you want to archive this report?") {
                archiveReportApi()
            }
        }
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

                        val intent =
                            Intent(
                                this@InventoryReportSettingActivity,
                                MainActivity::class.java
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            }
                        startActivity(intent)
                        finish()
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
}