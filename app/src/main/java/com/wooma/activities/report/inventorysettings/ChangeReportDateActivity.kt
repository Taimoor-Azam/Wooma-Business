package com.wooma.activities.report.inventorysettings

import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.wooma.activities.BaseActivity
import com.wooma.data.network.ApiResponseListener
import com.wooma.data.network.MyApi
import com.wooma.data.network.makeApiRequest
import com.wooma.data.network.showToast
import com.wooma.databinding.ActivityChangeReportDateBinding
import com.wooma.model.ApiResponse
import com.wooma.model.ChangeDateRequest
import com.wooma.model.ErrorResponse
import com.wooma.model.ReportData
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ChangeReportDateActivity : BaseActivity() {
    private lateinit var binding: ActivityChangeReportDateBinding

    var selectedDate = ""
    var reportId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityChangeReportDateBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        reportId = intent.getStringExtra("reportId") ?: ""
        val completionDate = intent.getStringExtra("completionDate") ?: ""

        val calendar = Calendar.getInstance()
        if (completionDate.isNotEmpty()) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val date = sdf.parse(completionDate)
                if (date != null) calendar.time = date
            } catch (_: Exception) {}
        }
        binding.calendarView.date = calendar.timeInMillis

        binding.ivBack.setOnClickListener { finish() }

        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            // 1. Create a Calendar instance and set it to the selected date
            val calendar = Calendar.getInstance()
            calendar.set(year, month, dayOfMonth, 0, 0, 0) // Sets time to 00:00:00

            // 2. Define your target format
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

            // 3. Format the date
            selectedDate = sdf.format(calendar.time)

            // Result: "2026-03-31T00:00:00"
            Log.d("SelectedDate", selectedDate)
        }

        binding.btnAddReport.setOnClickListener {
            if (selectedDate.isNotEmpty()) {
                changeDateApi()
            }
        }
    }

    private fun changeDateApi() {
        val item = ChangeDateRequest(selectedDate)
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.changeDate(reportId, item) },
            listener = object : ApiResponseListener<ApiResponse<ReportData>> {
                override fun onSuccess(response: ApiResponse<ReportData>) {
                    if (response.success) {
                        showToast("Completion Date changed successfully")
                        setResult(RESULT_OK, Intent().putExtra("completionDate", selectedDate))
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
}