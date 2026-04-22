package com.wooma.activities.report

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import com.wooma.activities.BaseActivity
import com.wooma.data.network.ApiResponseListener
import com.wooma.data.network.MyApi
import com.wooma.data.network.makeApiRequest
import com.wooma.data.network.showToast
import com.wooma.databinding.ActivityCheckoutCopyOptionBinding
import com.wooma.model.ApiResponse
import com.wooma.model.ErrorResponse
import com.wooma.model.PropertyDetailResponse

class CheckoutCopyOptionActivity : BaseActivity() {
    private lateinit var binding: ActivityCheckoutCopyOptionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheckoutCopyOptionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        val propertyId = intent.getStringExtra("propertyId") ?: ""
        val reportTypeId = intent.getStringExtra("reportTypeId") ?: ""

        binding.ivBack.setOnClickListener { finish() }

        binding.layoutStartFromScratch.setOnClickListener {
            startActivity(
                Intent(this, ConfigureReportActivity::class.java)
                    .putExtra("propertyId", propertyId)
                    .putExtra("reportTypeId", reportTypeId)
                    .putExtra("isCheckout", true)
            )
        }

        binding.layoutCopyFromReport.setOnClickListener {
            startActivity(
                Intent(this, CheckoutSelectReportActivity::class.java)
                    .putExtra("propertyId", propertyId)
                    .putExtra("reportTypeId", reportTypeId)
            )
        }

        if (propertyId.isNotEmpty()) {
            checkCheckoutReportsExist(propertyId, reportTypeId)
        }
    }

    private fun checkCheckoutReportsExist(propertyId: String, reportTypeId: String) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { api -> api.getPropertyById(propertyId) },
            listener = object : ApiResponseListener<ApiResponse<PropertyDetailResponse>> {
                override fun onSuccess(response: ApiResponse<PropertyDetailResponse>) {
                    val checkoutReports = response.data.reports.filter {
                        it.report_type?.id == reportTypeId
                    }
                    binding.layoutCopyFromReport.visibility =
                        if (checkoutReports.isEmpty()) View.GONE else View.VISIBLE
                    binding.view.visibility =
                        if (checkoutReports.isEmpty()) View.GONE else View.VISIBLE
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
