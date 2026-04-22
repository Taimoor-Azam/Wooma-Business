package com.wooma.activities.report.otherItems

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import com.wooma.activities.BaseActivity
import com.wooma.adapter.InventoryMetersAdapter
import com.wooma.model.enums.TenantReportStatus
import com.wooma.data.network.ApiResponseListener
import com.wooma.data.network.MyApi
import com.wooma.data.network.makeApiRequest
import com.wooma.data.network.showToast
import com.wooma.databinding.ActivityInventoryMeterListBinding
import com.wooma.model.ApiResponse
import com.wooma.model.ErrorResponse
import com.wooma.model.Meter

class MeterListingActivity : BaseActivity() {
    private lateinit var adapter: InventoryMetersAdapter
    private val metersList = mutableListOf<Meter>()
    private lateinit var binding: ActivityInventoryMeterListBinding
    var reportId = ""
    var reportStatus = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityInventoryMeterListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        reportId = intent.getStringExtra("reportId") ?: ""
        reportStatus = intent.getStringExtra("reportStatus") ?: ""

        adapter = InventoryMetersAdapter(this, metersList, reportId, reportStatus)

        binding.rvMeters.adapter = adapter
        binding.ivBack.setOnClickListener { finish() }

        if (reportStatus != TenantReportStatus.IN_PROGRESS.value) binding.ivAdd.visibility = View.GONE

        binding.ivAdd.setOnClickListener {
            startActivity(
                Intent(this, AddEditMeterActivity::class.java).putExtra(
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
            requestAction = { apiService -> apiService.getReportMeters(reportId, true) },
            listener = object : ApiResponseListener<ApiResponse<ArrayList<Meter>>> {
                override fun onSuccess(response: ApiResponse<ArrayList<Meter>>) {
                    if (response.success) {
                        metersList.clear()
                        metersList.addAll(response.data)
                        adapter.updateList(metersList)
                        binding.tvEmpty.visibility = if (metersList.isEmpty()) View.VISIBLE else View.GONE
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