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
import com.wooma.data.local.entity.AttachmentEntity
import com.wooma.data.repository.OtherItemsRepository
import com.wooma.databinding.ActivityInventoryMeterListBinding
import com.wooma.model.OtherItemsAttachment
import com.wooma.model.enums.TenantReportStatus
import kotlinx.coroutines.flow.combine
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

        val db = WoomaDatabase.getInstance(this)

        // Observe meters from Room — instant, works offline
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    repo.observeMeters(reportId),
                    db.attachmentDao().observeByEntityType("METER")
                ) { meters, allAttachments ->
                    meters.map { meter ->
                        val meterAttachments = allAttachments.filter { it.entityId == meter.id }
                        meter.copy(
                            attachments = meterAttachments.map { att ->
                                OtherItemsAttachment(
                                    id = att.serverId ?: att.id,
                                    is_active = true,
                                    is_deleted = false,
                                    created_at = "",
                                    updated_at = "",
                                    entityId = att.entityId,
                                    entityType = att.entityType,
                                    originalName = att.originalName,
                                    storageKey = att.storageKey ?: "",
                                    link = att.link,
                                    mimeType = att.mimeType,
                                    fileSize = att.fileSize.toString()
                                )
                            }
                        )
                    }
                }.collect { meters ->
                    metersList.clear()
                    metersList.addAll(meters)
                    adapter.updateList(metersList)
                    binding.tvEmpty.visibility = if (meters.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }

        // Observe sync status indicator
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
