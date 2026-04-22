package com.wooma.activities.report

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import com.wooma.activities.BaseActivity
import com.wooma.adapter.SelectPropertyAdapter
import com.wooma.data.network.ApiResponseListener
import com.wooma.data.network.MyApi
import com.wooma.data.network.makeApiRequest
import com.wooma.data.network.showToast
import com.wooma.databinding.ActivitySelectPropertyForReportBinding
import com.wooma.model.ApiResponse
import com.wooma.model.ErrorResponse
import com.wooma.model.Property
import com.wooma.model.TenantPropertiesWrapper

class SelectPropertyForReportActivity : BaseActivity() {
    private lateinit var adapter: SelectPropertyAdapter
    private val properties = mutableListOf<Property>()

    var isFromProperty = false
    private lateinit var binding: ActivitySelectPropertyForReportBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySelectPropertyForReportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)
        isFromProperty = intent.getBooleanExtra("isFromProperty", false)

        adapter = SelectPropertyAdapter(
            this, properties, isFromProperty,
            object : SelectPropertyAdapter.onPropertyClickInterface {
                override fun onPropertyClick(item: Property) {
                    val resultIntent = Intent()
                    resultIntent.putExtra("propertyItem", item)
                    setResult(RESULT_OK, resultIntent)
                    finish()
                }
            })
        binding.rvSelectProperty.adapter = adapter
        getPropertiesList()

        binding.ivBack.setOnClickListener { finish() }

        binding.searchView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s.toString())

            }

            override fun afterTextChanged(s: Editable?) {
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (ConfigureReportActivity.reportCreated) {
            ConfigureReportActivity.reportCreated = false
            val pid = ConfigureReportActivity.createdPropertyId
            ConfigureReportActivity.createdPropertyId = ""
            if (pid.isNotEmpty()) {
                startActivity(
                    Intent(this, ReportListingActivity::class.java)
                        .putExtra("propertyId", pid)
                )
            }
            finish()
        }
    }

    /*private fun loadProperties() {
         properties.addAll(
             listOf(
                 Property("42 Princes Street, Edinburgh, EH2 2BY"),
                 Property("158 Sauchiehall Street, Glasgow, G2 3EQ"),
                 Property("67 Union Street, Aberdeen, AB11 6BA"),
                 Property("23 George Street, Edinburgh, EH2 2PB"),
                 Property("91 Buchanan Street, Glasgow, G1 3HF"),
                 Property("134 Queen Street, Aberdeen, AB10 1XL"),
                 Property("76 Rose Street, Edinburgh, EH2 3DX"),
                 Property("205 Argyle Street, Glasgow, G2 8DL"),
                 Property("205 Argyle Street, Glasgow, G2 8DL"),
                 Property("205 Argyle Street, Glasgow, G2 8DL"),
                 Property("205 Argyle Street, Glasgow, G2 8DL"),
                 Property("205 Argyle Street, Glasgow, G2 8DL"),
                 Property("205 Argyle Street, Glasgow, G2 8DL"),
                 Property("207888`546546 Argyle Street, Glasgow, G2 8DL"),
                 Property("207888`546546 Argyle Street, Glasgow, G2 8DL"),
                 Property("207889 546546 Argyle Street, Glasgow, G2 8DL"),
             )
         )
        adapter.updateList(properties)
    }*/

    private fun getPropertiesList() {
        val queryMap = mutableMapOf<String, Any>().apply {
            put("page", 1)
            put("limit", 100)
            put("search", binding.searchView.text.toString())
            put("is_active", true)
        }

        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.getPropertiesList(queryMap) },
            listener = object : ApiResponseListener<ApiResponse<TenantPropertiesWrapper>> {
                override fun onSuccess(response: ApiResponse<TenantPropertiesWrapper>) {
                    if (response.data.data.isNotEmpty()) {
                        properties.clear()
                        properties.addAll(response.data.data)
                        adapter.updateList(properties)
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