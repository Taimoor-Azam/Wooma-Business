package com.wooma.business.activities.report

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.widget.SearchView
import com.wooma.business.activities.BaseActivity
import com.wooma.business.adapter.SelectPropertyAdapter
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.data.network.showToast
import com.wooma.business.databinding.ActivitySelectPropertyForReportBinding
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.Property
import com.wooma.business.model.TenantPropertiesWrapper

class SelectPropertyForReportActivity : BaseActivity() {
    private lateinit var adapter: SelectPropertyAdapter
    private val properties = mutableListOf<Property>()

    private lateinit var binding: ActivitySelectPropertyForReportBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySelectPropertyForReportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        adapter = SelectPropertyAdapter(this, properties, false,
            object : SelectPropertyAdapter.onPropertyClickInterface {
                override fun onPropertyClick(item: Property) {
                    val resultIntent = Intent()
                    resultIntent.putExtra("propertyItem", item)
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                }
            })
        binding.rvSelectProperty.adapter = adapter
        getPropertiesList()

        binding.ivBack.setOnClickListener { finish() }

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.filter(newText.orEmpty())
                return true
            }
        })
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
            put("limit", 50)
            put("search", binding.searchView.query.toString())
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