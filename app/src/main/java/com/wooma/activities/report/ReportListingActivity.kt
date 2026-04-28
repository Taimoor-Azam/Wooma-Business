package com.wooma.activities.report

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wooma.activities.BaseActivity
import com.wooma.activities.MainActivity
import com.wooma.activities.property.EditPropertyActivity
import com.wooma.adapter.ReportListingAdapter
import com.wooma.data.local.mapper.toReport
import com.wooma.data.repository.PropertyRepository
import com.wooma.data.repository.ReportRepository
import com.wooma.databinding.ActivityReportListingBinding
import com.wooma.model.AddReportResponse
import com.wooma.model.PropertyReportType
import com.wooma.model.Report
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ReportListingActivity : BaseActivity() {
    private lateinit var binding: ActivityReportListingBinding
    private lateinit var adapter: ReportListingAdapter

    var propertyId = ""
    private val reports = mutableListOf<Report>()

    private lateinit var propertyRepo: PropertyRepository
    private lateinit var reportRepo: ReportRepository

    private var observeJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityReportListingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        propertyRepo = PropertyRepository(this)
        reportRepo = ReportRepository(this)

        propertyId = intent.getStringExtra("propertyId") ?: ""

        setupAdapter()
        if (propertyId.isNotEmpty()) {
            startObserving(propertyId)
        }
        handleIntent(intent)

        binding.btnContinue.setOnClickListener {
            startActivity(
                Intent(this, SelectReportTypeActivity::class.java)
                    .putExtra("propertyId", propertyId)
            )
        }

        binding.ivBack.setOnClickListener { navigateToMainActivity() }

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateToMainActivity()
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)
    }

    private fun setupAdapter() {
        adapter = ReportListingAdapter(this, reports, propertyId)
        binding.rvReports.adapter = adapter
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let {
            setIntent(it)
            handleIntent(it)
        }
    }

    private fun handleIntent(intent: Intent) {
        val newPropertyId = intent.getStringExtra("propertyId") ?: ""
        if (newPropertyId.isNotEmpty() && newPropertyId != propertyId) {
            propertyId = newPropertyId
            setupAdapter()
            startObserving(newPropertyId)
        }

        val duplicatedReport = intent.getParcelableExtra<AddReportResponse>("duplicatedReport")
        if (duplicatedReport != null) {
            Log.d("ReportListingActivity", "Handling duplicated report: ${duplicatedReport.report_id}")

            val intentToInventory = Intent(this, InventoryListingActivity::class.java)
                .putExtra("reportStatus", duplicatedReport.status)
                .putExtra("reportId", duplicatedReport.report_id)
                .putExtra(
                    "reportType", PropertyReportType(
                        id = duplicatedReport.report_type.id,
                        display_name = duplicatedReport.report_type.display_name,
                        type_code = duplicatedReport.report_type.type_code
                    )
                )
                .putExtra("assessor", duplicatedReport.assessor)
                .putExtra("propertyId", propertyId)

            startActivity(intentToInventory)
            intent.removeExtra("duplicatedReport")
        }
    }

    private fun startObserving(targetPropertyId: String) {
        observeJob?.cancel()
        observeJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    propertyRepo.observeById(targetPropertyId).collect { prop ->
                        prop ?: return@collect
                        binding.tvTitle.text = prop.address
                        binding.ivReportEdit.setOnClickListener {
                            startActivity(
                                Intent(this@ReportListingActivity, EditPropertyActivity::class.java)
                                    .putExtra("id", prop.serverId ?: prop.id)
                                    .putExtra("address", prop.address)
                                    .putExtra("address_line_2", prop.addressLine2)
                                    .putExtra("city", prop.city)
                                    .putExtra("postcode", prop.postcode)
                            )
                        }
                    }
                }

                reportRepo.observeByProperty(targetPropertyId).collect { entities ->
                    val mapped = entities.map { it.toReport() }
                    reports.clear()
                    reports.addAll(mapped)
                    adapter.updateList(reports)
                    binding.tvNoReportFound.visibility =
                        if (reports.isNotEmpty()) View.GONE else View.VISIBLE
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (propertyId.isNotEmpty()) {
            lifecycleScope.launch {
                if (reportRepo.hasData(propertyId)) {
                    try { reportRepo.refreshByProperty(propertyId) } catch (_: Exception) {}
                } else {
                    val progress = ProgressDialog(this@ReportListingActivity).apply {
                        setMessage("Please Wait...")
                        setCancelable(false)
                    }
                    if (!isFinishing && !isDestroyed) progress.show()
                    try {
                        reportRepo.refreshByProperty(propertyId)
                    } catch (e: Exception) {
                        Log.e("ReportListing", "Refresh failed: ${e.message}")
                    } finally {
                        if (progress.isShowing) progress.dismiss()
                    }
                }
            }
        }
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }
}
