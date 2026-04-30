package com.wooma.activities.report.otherItems

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wooma.R
import com.wooma.activities.BaseActivity
import com.wooma.adapter.InventoryMetersAdapter
import com.wooma.data.local.WoomaDatabase
import com.wooma.data.repository.OtherItemsRepository
import com.wooma.databinding.ActivityInventoryMeterListBinding
import com.wooma.model.enums.TenantReportStatus
import kotlinx.coroutines.launch

class MeterListingActivity : BaseActivity() {
    private lateinit var adapter: InventoryMetersAdapter
    private val metersList = mutableListOf<com.wooma.model.Meter>()
    private lateinit var binding: ActivityInventoryMeterListBinding
    var reportId = ""
    var reportStatus = ""
    var showTimestamp = true

    private val repo by lazy { OtherItemsRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityInventoryMeterListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        reportId = intent.getStringExtra("reportId") ?: ""
        reportStatus = intent.getStringExtra("reportStatus") ?: ""
        showTimestamp = intent.getBooleanExtra("showTimestamp", true)

        adapter = InventoryMetersAdapter(this, metersList, reportId, reportStatus, showTimestamp)
        binding.rvMeters.adapter = adapter
        binding.ivBack.setOnClickListener { finish() }

        if (reportStatus != TenantReportStatus.IN_PROGRESS.value) binding.ivAdd.visibility = View.GONE

        binding.ivAdd.setOnClickListener {
            startActivity(
                Intent(this, AddEditMeterActivity::class.java)
                    .putExtra("reportId", reportId)
                    .putExtra("showTimestamp", showTimestamp)
            )
        }

        // Observe meters from Room — instant, works offline
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repo.observeMeters(reportId).collect { meters ->
                    metersList.clear()
                    metersList.addAll(meters)
                    adapter.updateList(metersList)
                    binding.tvEmpty.visibility = if (meters.isEmpty()) View.VISIBLE else View.GONE
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
        // Silent background refresh — updates Room cache, Flow re-emits automatically
        lifecycleScope.launch {
            try { repo.refreshMeters(reportId) } catch (_: Exception) {}
        }
    }
}
