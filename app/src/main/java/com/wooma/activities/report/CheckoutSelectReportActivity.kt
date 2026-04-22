package com.wooma.activities.report

import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.wooma.activities.BaseActivity
import com.wooma.adapter.CheckoutReportAdapter
import com.wooma.data.network.ApiResponseListener
import com.wooma.data.network.MyApi
import com.wooma.data.network.makeApiRequest
import com.wooma.data.network.showToast
import com.wooma.databinding.ActivityCheckoutSelectReportBinding
import com.wooma.model.AddReportResponse
import com.wooma.model.ApiResponse
import com.wooma.model.CreateReportFromPreviousRequest
import com.wooma.model.ErrorResponse
import com.wooma.model.PropertyDetailResponse
import com.wooma.model.Report

class CheckoutSelectReportActivity : BaseActivity() {

    private lateinit var binding: ActivityCheckoutSelectReportBinding
    private lateinit var adapter: CheckoutReportAdapter

    private var propertyId = ""
    private var reportTypeId = ""
    private var propertyAddress = ""
    private val reports = mutableListOf<Report>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheckoutSelectReportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        propertyId = intent.getStringExtra("propertyId") ?: ""
        reportTypeId = intent.getStringExtra("reportTypeId") ?: ""

        adapter = CheckoutReportAdapter(this, propertyAddress, reports) { report ->
        }
        binding.rvReports.adapter = adapter

        binding.ivBack.setOnClickListener { finish() }

        if (propertyId.isNotEmpty()) {
            loadReports()
        }
    }

    private fun loadReports() {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { api -> api.getPropertyById(propertyId) },
            listener = object : ApiResponseListener<ApiResponse<PropertyDetailResponse>> {
                override fun onSuccess(response: ApiResponse<PropertyDetailResponse>) {
                    propertyAddress = buildAddress(response.data)
                    val checkoutReports = response.data.reports
                        .filter { it.report_type?.id == reportTypeId }
                        .toMutableList()
                    adapter = CheckoutReportAdapter(
                        this@CheckoutSelectReportActivity,
                        propertyAddress,
                        checkoutReports
                    ) { report ->
                        createReportFromPrevious(report.id)
                    }
                    binding.rvReports.adapter = adapter
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

    private fun createReportFromPrevious(previousReportId: String) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { api ->
                api.createReport(
                    CreateReportFromPreviousRequest(
                        property_id = propertyId,
                        report_type_id = reportTypeId,
                        report_id = previousReportId
                    )
                )
            },
            listener = object : ApiResponseListener<ApiResponse<AddReportResponse>> {
                override fun onSuccess(response: ApiResponse<AddReportResponse>) {
                    if (response.success) {
                        showToast("Report Created Successfully")
                        startActivity(
                            Intent(this@CheckoutSelectReportActivity, InventoryListingActivity::class.java)
                                .putExtra("reportId", response.data.report_id)
                                .putExtra("reportStatus", response.data.status)
                        )
                        finish()
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

    private fun buildAddress(data: PropertyDetailResponse): String {
        return listOfNotNull(
            data.address.takeIf { it.isNotEmpty() },
            data.city.takeIf { it.isNotEmpty() },
            data.postcode.takeIf { it.isNotEmpty() }
        ).joinToString(", ")
    }
}
