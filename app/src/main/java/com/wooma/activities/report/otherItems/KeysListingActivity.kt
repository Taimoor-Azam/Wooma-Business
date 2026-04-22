package com.wooma.activities.report.otherItems

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import com.wooma.activities.BaseActivity
import com.wooma.adapter.InventoryKeysAdapter
import com.wooma.model.enums.TenantReportStatus
import com.wooma.data.network.ApiResponseListener
import com.wooma.data.network.MyApi
import com.wooma.data.network.makeApiRequest
import com.wooma.data.network.showToast
import com.wooma.databinding.ActivityInventoryKeysListBinding
import com.wooma.model.ApiResponse
import com.wooma.model.ErrorResponse
import com.wooma.model.KeyItem

class KeysListingActivity : BaseActivity() {
    private lateinit var adapter: InventoryKeysAdapter
    private val keysList = mutableListOf<KeyItem>()
    private lateinit var binding: ActivityInventoryKeysListBinding
    var reportId = ""
    var reportStatus = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityInventoryKeysListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        reportId = intent.getStringExtra("reportId") ?: ""
        reportStatus = intent.getStringExtra("reportStatus") ?: ""

        adapter = InventoryKeysAdapter(this, keysList, reportId, reportStatus)

        binding.rvMeters.adapter = adapter
        binding.ivBack.setOnClickListener { finish() }

        if (reportStatus != TenantReportStatus.IN_PROGRESS.value) binding.ivAdd.visibility = View.GONE

        binding.ivAdd.setOnClickListener {
            startActivity(
                Intent(this, AddEditKeysActivity::class.java).putExtra(
                    "reportId",
                    reportId
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        getReportByIdApi()
    }

    private fun getReportByIdApi() {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.getReportKeys(reportId, true) },
            listener = object : ApiResponseListener<ApiResponse<ArrayList<KeyItem>>> {
                override fun onSuccess(response: ApiResponse<ArrayList<KeyItem>>) {
                    if (response.success) {
                        keysList.clear()
                        keysList.addAll(response.data)
                        adapter.updateList(keysList)
                        binding.tvEmpty.visibility = if (keysList.isEmpty()) View.VISIBLE else View.GONE
                    } else {
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