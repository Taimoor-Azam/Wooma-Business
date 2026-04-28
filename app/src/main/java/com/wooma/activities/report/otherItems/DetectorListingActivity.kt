package com.wooma.activities.report.otherItems

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import com.wooma.activities.BaseActivity
import com.wooma.adapter.InventoryDetectorAdapter
import com.wooma.model.enums.TenantReportStatus
import com.wooma.data.network.ApiResponseListener
import com.wooma.data.network.MyApi
import com.wooma.data.network.makeApiRequest
import com.wooma.data.network.showToast
import com.wooma.databinding.ActivityInventoryDetectorListBinding
import com.wooma.model.ApiResponse
import com.wooma.model.DetectorItem
import com.wooma.model.ErrorResponse

class DetectorListingActivity : BaseActivity() {
    private lateinit var adapter: InventoryDetectorAdapter
    private val detectorList = mutableListOf<DetectorItem>()
    private lateinit var binding: ActivityInventoryDetectorListBinding
    var reportId = ""
    var reportStatus = ""
    var showTimestamp = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityInventoryDetectorListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        reportId = intent.getStringExtra("reportId") ?: ""
        reportStatus = intent.getStringExtra("reportStatus") ?: ""
        showTimestamp = intent.getBooleanExtra("showTimestamp", true)

        adapter =
            InventoryDetectorAdapter(this, detectorList, reportId, reportStatus, showTimestamp)

        binding.rvMeters.adapter = adapter
        binding.ivBack.setOnClickListener { finish() }

        if (reportStatus != TenantReportStatus.IN_PROGRESS.value) binding.ivAdd.visibility = View.GONE

        binding.ivAdd.setOnClickListener {
            startActivity(
                Intent(this, AddEditDetectorActivity::class.java).putExtra(
                    "reportId",
                    reportId
                ).putExtra("showTimestamp", showTimestamp)
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
            requestAction = { apiService -> apiService.getReportDetector(reportId, true) },
            listener = object : ApiResponseListener<ApiResponse<ArrayList<DetectorItem>>> {
                override fun onSuccess(response: ApiResponse<ArrayList<DetectorItem>>) {
                    if (response.success) {
                        detectorList.clear()
                        detectorList.addAll(response.data)
                        adapter.updateList(detectorList)
                        binding.tvEmpty.visibility = if (detectorList.isEmpty()) View.VISIBLE else View.GONE
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