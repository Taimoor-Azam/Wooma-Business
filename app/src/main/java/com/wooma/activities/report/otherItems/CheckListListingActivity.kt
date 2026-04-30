package com.wooma.activities.report.otherItems

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wooma.activities.BaseActivity
import com.wooma.adapter.CheckListAdapter
import com.wooma.data.repository.ChecklistRepository
import com.wooma.databinding.ActivityCheckListListingBinding
import com.wooma.model.Checklist
import com.wooma.model.enums.TenantReportStatus
import com.wooma.sync.SyncScheduler
import kotlinx.coroutines.launch

class CheckListListingActivity : BaseActivity() {
    private lateinit var binding: ActivityCheckListListingBinding
    private lateinit var adapter: CheckListAdapter
    private val checklistItems = mutableListOf<Checklist>()
    private var reportId = ""
    private var reportStatus = ""
    private var isReadOnly = false
    private var showTimestamp = true

    private lateinit var checklistRepo: ChecklistRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCheckListListingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        reportId = intent.getStringExtra("reportId") ?: ""
        reportStatus = intent.getStringExtra("reportStatus") ?: ""
        showTimestamp = intent.getBooleanExtra("showTimestamp", true)
        isReadOnly = reportStatus == TenantReportStatus.COMPLETED.value ||
                reportStatus == TenantReportStatus.HISTORICAL.value ||
                reportStatus == TenantReportStatus.TENANT_REVIEW.value

        checklistRepo = ChecklistRepository(this)

        adapter = CheckListAdapter(
            list = checklistItems,
            isReadOnly = isReadOnly,
            onToggle = { id, isActive ->
                lifecycleScope.launch {
                    checklistRepo.updateChecklistStatus(id, isActive)
                    SyncScheduler.scheduleImmediateSync(this@CheckListListingActivity)
                    adapter.updateItem(id, isActive)
                }
            },
            onClick = { checklist ->
                val intent = Intent(this, CheckListDetailActivity::class.java)
                intent.putExtra("reportId", reportId)
                intent.putExtra("checklistId", checklist.id)
                intent.putExtra("checklistName", checklist.name)
                intent.putExtra("reportStatus", reportStatus)
                intent.putExtra("showTimestamp", showTimestamp)
                startActivity(intent)
            }
        )

        binding.rvChecklists.adapter = adapter
        binding.ivBack.setOnClickListener { finish() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                checklistRepo.observeChecklists(reportId).collect { entities ->
                    val checklists = entities.map { Checklist(id = it.id, name = it.name, is_active = it.isActive) }
                    checklistItems.clear()
                    checklistItems.addAll(checklists)
                    adapter.updateList(checklistItems)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            try { checklistRepo.refreshChecklistStatuses(reportId) } catch (_: Exception) {}
        }
    }
}
