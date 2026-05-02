package com.wooma.activities.report.otherItems

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wooma.R
import com.wooma.activities.BaseActivity
import com.wooma.adapter.InventoryKeysAdapter
import com.wooma.data.local.WoomaDatabase
import com.wooma.data.repository.OtherItemsRepository
import com.wooma.databinding.ActivityInventoryKeysListBinding
import com.wooma.model.OtherItemsAttachment
import com.wooma.model.enums.TenantReportStatus
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class KeysListingActivity : BaseActivity() {
    private lateinit var adapter: InventoryKeysAdapter
    private val keysList = mutableListOf<com.wooma.model.KeyItem>()
    private lateinit var binding: ActivityInventoryKeysListBinding
    var reportId = ""
    var reportStatus = ""
    var showTimestamp = true

    private val repo by lazy { OtherItemsRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityInventoryKeysListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        reportId = intent.getStringExtra("reportId") ?: ""
        reportStatus = intent.getStringExtra("reportStatus") ?: ""
        showTimestamp = intent.getBooleanExtra("showTimestamp", true)

        adapter = InventoryKeysAdapter(this, keysList, reportId, reportStatus, showTimestamp)
        binding.rvMeters.adapter = adapter
        binding.ivBack.setOnClickListener { finish() }

        if (reportStatus != TenantReportStatus.IN_PROGRESS.value) binding.ivAdd.visibility = View.GONE

        binding.ivAdd.setOnClickListener {
            startActivity(
                Intent(this, AddEditKeysActivity::class.java)
                    .putExtra("reportId", reportId)
                    .putExtra("showTimestamp", showTimestamp)
            )
        }

        val db = WoomaDatabase.getInstance(this)

        // Observe keys from Room — instant, works offline
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    repo.observeKeys(reportId),
                    db.attachmentDao().observeByEntityType("KEY")
                ) { keys, allAttachments ->
                    keys.map { key ->
                        val keyAttachments = allAttachments.filter { it.entityId == key.id }
                        key.copy(
                            attachments = keyAttachments.map { att ->
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
                }.collect { keys ->
                    keysList.clear()
                    keysList.addAll(keys)
                    adapter.updateList(keysList)
                    binding.tvEmpty.visibility = if (keys.isEmpty()) View.VISIBLE else View.GONE
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
        lifecycleScope.launch {
            try { repo.refreshKeys(reportId) } catch (_: Exception) {}
        }
    }
}
