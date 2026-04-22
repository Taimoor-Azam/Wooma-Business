package com.wooma.activities.property

import android.os.Bundle
import android.util.Log
import android.view.View
import com.wooma.activities.BaseActivity
import com.wooma.adapter.ArchivePropertyAdapter
import com.wooma.customs.Utils
import com.wooma.data.network.ApiResponseListener
import com.wooma.data.network.MyApi
import com.wooma.data.network.makeApiRequest
import com.wooma.data.network.showToast
import com.wooma.databinding.ActivityArchivePropertiesListingBinding
import com.wooma.model.ApiResponse
import com.wooma.model.ErrorResponse
import com.wooma.model.Property
import com.wooma.model.TenantPropertiesWrapper

class ArchivePropertiesActivity : BaseActivity() {
    private lateinit var binding: ActivityArchivePropertiesListingBinding

    private var page: Int = 1

    private lateinit var adapter: ArchivePropertyAdapter
    private val properties = mutableListOf<Property>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityArchivePropertiesListingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        adapter = ArchivePropertyAdapter(
            this,
            properties, object : ArchivePropertyAdapter.OnItemClickInterface {
                override fun onItemClick(item: Property) {
                    Utils.showDialogBox(
                        this@ArchivePropertiesActivity,
                        "Restore Property",
                        "Are you sure you want to restore ${item.address}, ${item.city}, ${item.postcode} to your active properties?"
                    ) {
                        unarchivePropertyApi(item.id ?: "")
                    }
                }
            }
        )
        binding.rvArchiveProperties.adapter = adapter

//        binding.rvArchiveProperties.adapter = ArchivePropertyAdapter(this, mutableListOf())

        binding.ivBack.setOnClickListener { finish() }
        getPropertiesList()
    }

    private fun getPropertiesList() {
        val queryMap = mutableMapOf<String, Any>().apply {
            put("page", page)
            put("limit", 50)
            put("search", "")
            put("is_active", false)
        }

        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.getPropertiesList(queryMap) },
            listener = object : ApiResponseListener<ApiResponse<TenantPropertiesWrapper>> {
                override fun onSuccess(response: ApiResponse<TenantPropertiesWrapper>) {
                    properties.clear()
                    properties.addAll(response.data.data)
                    adapter.updateList(properties)
                    if (properties.isEmpty()) {
                        binding.tvNoArchive.visibility = View.VISIBLE
                        binding.rvArchiveProperties.visibility = View.GONE
                    } else {
                        binding.tvNoArchive.visibility = View.GONE
                        binding.rvArchiveProperties.visibility = View.VISIBLE
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

    private fun unarchivePropertyApi(id: String) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.restoreProperty(id) },
            listener = object : ApiResponseListener<ApiResponse<Property>> {
                override fun onSuccess(response: ApiResponse<Property>) {
                    if (response.success) {
                        getPropertiesList()
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