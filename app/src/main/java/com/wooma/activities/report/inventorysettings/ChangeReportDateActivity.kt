package com.wooma.activities.report.inventorysettings

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.wooma.activities.BaseActivity
import com.wooma.data.network.showToast
import com.wooma.data.repository.ReportRepository
import com.wooma.databinding.ActivityChangeReportDateBinding
import com.wooma.sync.ConnectivityObserver
import com.wooma.sync.SyncScheduler
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ChangeReportDateActivity : BaseActivity() {
    private lateinit var binding: ActivityChangeReportDateBinding
    private val reportRepo by lazy { ReportRepository(this) }

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
        if (!ConnectivityObserver(this).isConnected()) {
            showToast("Internet connection required")
            return
        }
        lifecycleScope.launch {
            reportRepo.updateCompletionDate(reportId, selectedDate)
            SyncScheduler.scheduleImmediateSync(this@ChangeReportDateActivity)
            showToast("Completion Date changed successfully")
            setResult(RESULT_OK, Intent().putExtra("completionDate", selectedDate))
            finish()
        }
    }
}