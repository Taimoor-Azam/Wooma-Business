package com.wooma.business.activities.report

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import com.wooma.business.activities.BaseActivity
import com.wooma.business.activities.MainActivity
import com.wooma.business.activities.property.EditPropertyActivity
import com.wooma.business.adapter.ReportListingAdapter
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.data.network.showToast
import com.wooma.business.databinding.ActivityReportListingBinding
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.PropertyDetailResponse
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
        propertyId = intent.getStringExtra("propertyId") ?: ""

        adapter = ReportListingAdapter(this, reports, propertyId)
        binding.rvReports.adapter = adapter

        binding.btnContinue.setOnClickListener {
            val intent = Intent(this, SelectReportTypeActivity::class.java).putExtra(
                "propertyId",
                propertyId
            )
            startActivity(intent)
        }

        binding.ivBack.setOnClickListener { navigateToMainActivity() }
    }

    override fun onBackPressed() {
        navigateToMainActivity()
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
                        adapter.updateList(response.data.reports)
                        binding.tvNoReportFound.visibility = View.GONE
                    } else {
                        binding.tvNoReportFound.visibility = View.VISIBLE
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