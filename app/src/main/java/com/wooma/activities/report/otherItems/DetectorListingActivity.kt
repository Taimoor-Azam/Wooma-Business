package com.wooma.activities.report.otherItems

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wooma.R
import com.wooma.activities.BaseActivity
import com.wooma.adapter.InventoryDetectorAdapter
import com.wooma.data.local.WoomaDatabase
import com.wooma.data.repository.OtherItemsRepository
import com.wooma.databinding.ActivityInventoryDetectorListBinding
import com.wooma.model.enums.TenantReportStatus
import kotlinx.coroutines.launch

class DetectorListingActivity : BaseActivity() {
    private lateinit var adapter: InventoryDetectorAdapter
    private val detectorList = mutableListOf<com.wooma.model.DetectorItem>()
    private lateinit var binding: ActivityInventoryDetectorListBinding
    var reportId = ""
    var reportStatus = ""
    var showTimestamp = true

    private val repo by lazy { OtherItemsRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityInventoryDetectorListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        reportId = intent.getStringExtra("reportId") ?: ""
        reportStatus = intent.getStringExtra("reportStatus") ?: ""
        showTimestamp = intent.getBooleanExtra("showTimestamp", true)

        adapter = InventoryDetectorAdapter(this, detectorList, reportId, reportStatus, showTimestamp)
        binding.rvMeters.adapter = adapter
        binding.ivBack.setOnClickListener { finish() }

        if (reportStatus != TenantReportStatus.IN_PROGRESS.value) binding.ivAdd.visibility = View.GONE

        binding.ivAdd.setOnClickListener {
            startActivity(
                Intent(this, AddEditDetectorActivity::class.java)
                    .putExtra("reportId", reportId)
                    .putExtra("showTimestamp", showTimestamp)
            )
        }

        // Observe detectors from Room — instant, works offline
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repo.observeDetectors(reportId).collect { detectors ->
                    detectorList.clear()
                    detectorList.addAll(detectors)
                    adapter.updateList(detectorList)
                    binding.tvEmpty.visibility = if (detectors.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }

        // Observe sync status indicator
        val db = WoomaDatabase.getInstance(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                db.syncQueueDao().countPending().collect { count ->
                    binding.ivSyncStatus.setImageResource(
                        if (count > 0) R.drawable.svg_syncing else R.drawable.svg_synced
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            try { repo.refreshDetectors(reportId) } catch (_: Exception) {}
        }
    }
}
