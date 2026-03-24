package com.wooma.business.activities.report

import android.os.Bundle
import android.util.Log
import com.wooma.business.activities.BaseActivity
import com.wooma.business.adapter.ReportTypeAdapter
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.data.network.showToast
import com.wooma.business.databinding.ActivityReportTypeBinding
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.ReportType
import com.wooma.business.model.ReportTypeResponse
import com.wooma.business.model.TenantPropertiesWrapper

class SelectReportTypeActivity : BaseActivity() {
    private lateinit var adapter: ReportTypeAdapter
    private var reportTypeList = mutableListOf<ReportType>()

    var propertyId = ""
    private lateinit var binding: ActivityReportTypeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityReportTypeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)
        propertyId = intent.getStringExtra("propertyId")?: ""

        adapter = ReportTypeAdapter(this, reportTypeList, propertyId)
        binding.rvSelectProperty.adapter = adapter

        getReportTypeListApi()

        binding.ivBack.setOnClickListener { finish() }
    }

    private fun getReportTypeListApi() {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.getReportTypes() },
            listener = object : ApiResponseListener<ApiResponse<ReportTypeResponse>> {
                override fun onSuccess(response: ApiResponse<ReportTypeResponse>) {
                    if (response.data.data.isNotEmpty()) {
                        reportTypeList = response.data.data
                        adapter.updateList(reportTypeList)
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