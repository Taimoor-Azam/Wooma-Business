package com.wooma.business.activities.report

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.OnBackPressedCallback
import com.wooma.business.activities.BaseActivity
import com.wooma.business.activities.MainActivity
import com.wooma.business.activities.property.EditPropertyActivity
import com.wooma.business.adapter.ReportListingAdapter
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.data.network.showToast
import com.wooma.business.databinding.ActivityReportListingBinding
import com.wooma.business.model.AddReportResponse
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.PropertyDetailResponse
import com.wooma.business.model.PropertyReportType
import com.wooma.business.model.Report

class ReportListingActivity : BaseActivity() {
    private lateinit var binding: ActivityReportListingBinding
    private lateinit var adapter: ReportListingAdapter

    var propertyId = ""
    private val reports = mutableListOf<Report>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityReportListingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        // Initialize propertyId from intent
        propertyId = intent.getStringExtra("propertyId") ?: ""

        setupAdapter()
        handleIntent(intent)

        binding.btnContinue.setOnClickListener {
            val intent = Intent(this, SelectReportTypeActivity::class.java).putExtra(
                "propertyId",
                propertyId
            )
            startActivity(intent)
        }

        binding.ivBack.setOnClickListener { navigateToMainActivity() }

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateToMainActivity()
                // If you want default behavior after your logic:
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        onBackPressedDispatcher.addCallback(this, callback)
    }

    private fun setupAdapter() {
        adapter = ReportListingAdapter(this, reports, propertyId)
        binding.rvReports.adapter = adapter
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let {
            setIntent(it)
            handleIntent(it)
        }
    }

    private fun handleIntent(intent: Intent) {
        val newPropertyId = intent.getStringExtra("propertyId") ?: ""
        if (newPropertyId.isNotEmpty() && newPropertyId != propertyId) {
            propertyId = newPropertyId
            setupAdapter() // Re-setup adapter with new propertyId
            getPropertyByIdApi()
        }

        val duplicatedReport = intent.getParcelableExtra<AddReportResponse>("duplicatedReport")
        if (duplicatedReport != null) {
            Log.d(
                "ReportListingActivity",
                "Handling duplicated report: ${duplicatedReport.report_id}"
            )

            val intentToInventory = Intent(this, InventoryListingActivity::class.java)
                .putExtra("reportStatus", duplicatedReport.status)
                .putExtra("reportId", duplicatedReport.report_id)
                .putExtra(
                    "reportType", PropertyReportType(
                        id = duplicatedReport.report_type.id,
                        display_name = duplicatedReport.report_type.display_name,
                        type_code = duplicatedReport.report_type.type_code
                    )
                )
                .putExtra("assessor", duplicatedReport.assessor)
                .putExtra("propertyId", propertyId)

            startActivity(intentToInventory)

            // Remove the extra so it's not re-handled on subsequent onResume/onNewIntent
            intent.removeExtra("duplicatedReport")
        }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (propertyId.isNotEmpty()) {
            getPropertyByIdApi()
        }
    }

    private fun getPropertyByIdApi() {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.getPropertyById(propertyId) },
            listener = object : ApiResponseListener<ApiResponse<PropertyDetailResponse>> {
                override fun onSuccess(response: ApiResponse<PropertyDetailResponse>) {
                    binding.tvTitle.text = response.data.address

                    binding.ivReportEdit.setOnClickListener {
                        val intent = Intent(
                            this@ReportListingActivity,
                            EditPropertyActivity::class.java
                        ).putExtra("id", response.data.id)
                            .putExtra("address", response.data.address)
                            .putExtra("address_line_2", response.data.address_line_2)
                            .putExtra("city", response.data.city)
                            .putExtra("postcode", response.data.postcode)
                        startActivity(intent)
                    }

                    if (response.success && response.data.reports.isNotEmpty()) {
                        reports.clear()
                        reports.addAll(response.data.reports)
                        adapter.updateList(reports)
                        binding.tvNoReportFound.visibility = View.GONE
                    } else {
                        reports.clear()
                        adapter.updateList(reports)
                        binding.tvNoReportFound.visibility = View.VISIBLE
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    Log.e("API", errorMessage?.error?.message ?: "Unknown error")
                }

                override fun onError(throwable: Throwable) {
                    Log.e("API", "Error: ${throwable.message}")
                }
            }
        )
    }
}
