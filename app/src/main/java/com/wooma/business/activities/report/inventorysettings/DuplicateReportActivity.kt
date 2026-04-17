package com.wooma.business.activities.report.inventorysettings

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import com.wooma.business.activities.BaseActivity
import com.wooma.business.adapter.DuplicatePropertySelectAdapter
import com.wooma.business.model.PropertyReportType
import com.wooma.business.activities.report.InventoryListingActivity
import com.wooma.business.activities.report.ReportListingActivity
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.data.network.showToast
import com.wooma.business.databinding.ActivityDuplicateReportBinding
import com.wooma.business.model.AddReportResponse
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.CreateDuplicateReport
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.Property
import com.wooma.business.model.TenantPropertiesWrapper

class DuplicateReportActivity : BaseActivity() {
    private lateinit var binding: ActivityDuplicateReportBinding
    private lateinit var adapter: DuplicatePropertySelectAdapter
    private val properties = mutableListOf<Property>()

    var property: Property? = null
    var reportId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDuplicateReportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        reportId = intent.getStringExtra("reportId") ?: ""

        adapter = DuplicatePropertySelectAdapter(properties) { selected ->
            property = selected
        }
        binding.rvSelectProperty.adapter = adapter

        getPropertiesList()

        binding.ivBack.setOnClickListener { finish() }
        binding.btnAddReport.setOnClickListener {
            if (property != null)
                duplicateReportApi() else showToast("Please select property first.")
        }

        binding.searchView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                adapter.filter(s?.toString().orEmpty())
            }
        })
    }

    private fun duplicateReportApi() {
        val item = CreateDuplicateReport(
            property_id = property?.id ?: "",
            include_images = if (binding.cbCopyPhoto.isChecked) true else null,
            include_descriptions = true,
            report_id = reportId
        )

        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.createDuplicateReport(item) },
            listener = object : ApiResponseListener<ApiResponse<AddReportResponse>> {
                override fun onSuccess(response: ApiResponse<AddReportResponse>) {
                    if (response.success) {
                        showToast("Report duplicated successfully")
                        val intent = Intent(this@DuplicateReportActivity, InventoryListingActivity::class.java)
                        intent.putExtra("reportId", response.data.report_id)
                        intent.putExtra("propertyId", property?.id)
                        intent.putExtra("reportStatus", response.data.status)
                        intent.putExtra("reportType", PropertyReportType(
                            id = response.data.report_type.id,
                            display_name = response.data.report_type.display_name,
                            type_code = response.data.report_type.type_code
                        ))
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
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

    private fun getPropertiesList() {
        val queryMap = mutableMapOf<String, Any>().apply {
            put("page", 1)
            put("limit", 100)
            put("search", "")
            put("is_active", true)
        }

        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.getPropertiesList(queryMap) },
            listener = object : ApiResponseListener<ApiResponse<TenantPropertiesWrapper>> {
                override fun onSuccess(response: ApiResponse<TenantPropertiesWrapper>) {
                    if (response.success) {
                        properties.clear()
                        properties.addAll(response.data.data)
                        adapter.updateList(properties)
                        adapter.setSelectedProperty(property)
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