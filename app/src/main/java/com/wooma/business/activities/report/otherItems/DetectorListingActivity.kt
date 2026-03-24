package com.wooma.business.activities.report.otherItems

import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.wooma.business.activities.BaseActivity
import com.wooma.business.adapter.InventoryDetectorAdapter
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.data.network.showToast
import com.wooma.business.databinding.ActivityInventoryDetectorListBinding
import com.wooma.business.databinding.ActivityInventoryKeysListBinding
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.DetectorItem
import com.wooma.business.model.ErrorResponse

class DetectorListingActivity : BaseActivity() {
    private lateinit var adapter: InventoryDetectorAdapter
    private val detectorList = mutableListOf<DetectorItem>()
    private lateinit var binding: ActivityInventoryDetectorListBinding
    var reportId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityInventoryDetectorListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        reportId = intent.getStringExtra("reportId") ?: ""

        adapter = InventoryDetectorAdapter(this, detectorList, reportId)

        binding.rvMeters.adapter = adapter
        binding.ivBack.setOnClickListener { finish() }

        binding.ivAdd.setOnClickListener {
            startActivity(
                Intent(this, AddEditDetectorActivity::class.java).putExtra(
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
            requestAction = { apiService -> apiService.getReportDetector(reportId, true) },
            listener = object : ApiResponseListener<ApiResponse<ArrayList<DetectorItem>>> {
                override fun onSuccess(response: ApiResponse<ArrayList<DetectorItem>>) {
                    if (response.success) {
                        detectorList.clear()
                        detectorList.addAll(response.data)
                        adapter.updateList(detectorList)
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