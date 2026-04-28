package com.wooma.activities.property

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wooma.activities.BaseActivity
import com.wooma.adapter.ArchivePropertyAdapter
import com.wooma.customs.Utils
import com.wooma.data.local.mapper.toProperty
import com.wooma.data.network.ApiResponseListener
import com.wooma.data.network.MyApi
import com.wooma.data.network.makeApiRequest
import com.wooma.data.network.showToast
import com.wooma.data.repository.PropertyRepository
import com.wooma.databinding.ActivityArchivePropertiesListingBinding
import com.wooma.model.ApiResponse
import com.wooma.model.ErrorResponse
import com.wooma.model.Property
import kotlinx.coroutines.launch

class ArchivePropertiesActivity : BaseActivity() {
    private lateinit var binding: ActivityArchivePropertiesListingBinding

    private lateinit var propertyRepo: PropertyRepository
    private lateinit var adapter: ArchivePropertyAdapter
    private val properties = mutableListOf<Property>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityArchivePropertiesListingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        propertyRepo = PropertyRepository(this)

        adapter = ArchivePropertyAdapter(
            this,
            properties,
            object : ArchivePropertyAdapter.OnItemClickInterface {
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

        binding.ivBack.setOnClickListener { finish() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                propertyRepo.observeArchivedProperties().collect { entities ->
                    val mapped = entities.map { it.toProperty() }
                    properties.clear()
                    properties.addAll(mapped)
                    adapter.updateList(properties)
                    if (properties.isEmpty()) {
                        binding.tvNoArchive.visibility = View.VISIBLE
                        binding.rvArchiveProperties.visibility = View.GONE
                    } else {
                        binding.tvNoArchive.visibility = View.GONE
                        binding.rvArchiveProperties.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            try {
                propertyRepo.refreshArchivedProperties()
            } catch (_: Exception) {}
        }
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
                        lifecycleScope.launch {
                            try {
                                propertyRepo.upsertFromServer(response.data)
                            } catch (_: Exception) {}
                            try {
                                propertyRepo.refreshArchivedProperties()
                            } catch (_: Exception) {}
                        }
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
