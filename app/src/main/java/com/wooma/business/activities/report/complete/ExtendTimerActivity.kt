package com.wooma.business.activities.report.complete

import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import com.wooma.business.activities.BaseActivity
import com.wooma.business.customs.Utils
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.databinding.ActivityExtendTimerBinding
import com.wooma.business.model.AddNewRoomsRequest
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.ExtendTimeRequest
import com.wooma.business.model.ReportData

class ExtendTimerActivity : BaseActivity() {
    var count = 1

    private lateinit var binding: ActivityExtendTimerBinding
    var reportId = ""
    var expiryDate = ""

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityExtendTimerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        reportId = intent.getStringExtra("reportId") ?: ""
        expiryDate = intent.getStringExtra("expiryDate") ?: ""

        applyWindowInsetsToBinding(binding.root)

        binding.ivBack.setOnClickListener { finish() }

        binding.btnSendReview.setOnClickListener {
            val timeExtended = ExtendTimeRequest(Utils.addDaysToDate(expiryDate, count.toLong()))
            extendTime(timeExtended)
        }

        binding.ivMinus.setOnClickListener {
            if (count > 1)
                count--
            binding.tvTotalSigns.text = "$count days"
        }

        binding.ivPlus.setOnClickListener {
            count++
            binding.tvTotalSigns.text = "$count days"
        }
    }

    private fun extendTime(request: ExtendTimeRequest) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.extendReviewTime(reportId, request) },
            listener = object : ApiResponseListener<ApiResponse<ReportData>> {
                override fun onSuccess(response: ApiResponse<ReportData>) {
                    if (response.success) {
                        finish()
                    } else {
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    // Handle API error
//                    Log.e("API", errorMessage?.error?.message ?: "")
//                    showToast(errorMessage?.error?.message ?: "")
                }

                override fun onError(throwable: Throwable) {
                    // Handle network error
//                    Log.e("API", "Error: ${throwable.message}")
//                    showToast("Error: ${throwable.message}")
                }
            }
        )
    }

}