package com.wooma.activities.report.otherItems

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.wooma.R
import com.wooma.activities.BaseActivity
import com.wooma.adapter.InventoryDetectorAdapter
import com.wooma.data.local.WoomaDatabase
import com.wooma.data.repository.OtherItemsRepository
import com.wooma.databinding.ActivityInventoryDetectorListBinding
import com.wooma.model.OtherItemsAttachment
import com.wooma.model.enums.TenantReportStatus
import com.wooma.sync.ConnectivityObserver
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class DetectorListingActivity : BaseActivity() {
    private lateinit var adapter: InventoryDetectorAdapter
    private val detectorList = mutableListOf<com.wooma.model.DetectorItem>()
    private lateinit var binding: ActivityInventoryDetectorListBinding
    var reportId = ""
    var reportStatus = ""
    var showTimestamp = true
    private var isNetworkAvailable = false
    private var areAllDetectorsSynced = false

    private val repo by lazy { OtherItemsRepository(this) }
    private val db by lazy { WoomaDatabase.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityInventoryDetectorListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        reportId = intent.getStringExtra("reportId") ?: ""
        reportStatus = intent.getStringExtra("reportStatus") ?: ""
        showTimestamp = intent.getBooleanExtra("showTimestamp", true)

        adapter = InventoryDetectorAdapter(
            this,
            detectorList,
            reportId,
            reportStatus,
            showTimestamp,
            onReorder = { detectorId, prevRank, nextRank ->
                lifecycleScope.launch {
                    val canReorderNow = isNetworkAvailable &&
                        reportStatus == TenantReportStatus.IN_PROGRESS.value &&
                        areAllDetectorsSynced
                    if (!canReorderNow) {
                        adapter.updateList(detectorList)
                        return@launch
                    }
                    repo.reorderDetector(detectorId, prevRank, nextRank)
                }
            }
        )
        binding.rvMeters.adapter = adapter
        binding.ivBack.setOnClickListener { finish() }
        adapter.setEditMode(true, canReorder = false)

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
                combine(
                    repo.observeDetectors(reportId),
                    db.attachmentDao().observeByEntityType("DETECTOR")
                ) { detectors, allAttachments ->
                    detectors.map { detector ->
                        val detectorAttachments = allAttachments.filter { it.entityId == detector.id }
                        detector.copy(
                            attachments = detectorAttachments.map { att ->
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
                                    link = att.localUri?.let { "file://$it" }
                                        ?: if (att.isUploaded) att.link else null,
                                    mimeType = att.mimeType,
                                    fileSize = att.fileSize.toString()
                                )
                            }
                        )
                    }
                }.collect { detectors ->
                    detectorList.clear()
                    detectorList.addAll(detectors)
                    adapter.updateList(detectorList)
                    binding.tvEmpty.visibility = if (detectors.isEmpty()) View.VISIBLE else View.GONE
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

        val touchCallback = object : ItemTouchHelper.Callback() {
            private val edgeScrollThresholdPx by lazy { (72 * resources.displayMetrics.density).toInt() }
            private val edgeScrollStepPx by lazy { (16 * resources.displayMetrics.density).toInt() }

            override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                val canMove = adapter.isEditMode && isNetworkAvailable && areAllDetectorsSynced
                return if (canMove) makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
                else makeMovementFlags(0, 0)
            }

            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                adapter.onItemMove(vh.adapterPosition, target.adapterPosition)
                return true
            }

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled() = false

            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                adapter.onDropCompleted(vh.adapterPosition)
            }

            override fun onChildDraw(
                c: android.graphics.Canvas,
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive)
                if (actionState != ItemTouchHelper.ACTION_STATE_DRAG || !isCurrentlyActive) return

                val itemTop = vh.itemView.top + dY
                val itemBottom = vh.itemView.bottom + dY
                val topEdge = rv.paddingTop + edgeScrollThresholdPx
                val bottomEdge = rv.height - rv.paddingBottom - edgeScrollThresholdPx
                when {
                    itemTop < topEdge -> rv.scrollBy(0, -edgeScrollStepPx)
                    itemBottom > bottomEdge -> rv.scrollBy(0, edgeScrollStepPx)
                }
            }
        }
        val itemTouchHelper = ItemTouchHelper(touchCallback)
        itemTouchHelper.attachToRecyclerView(binding.rvMeters)
        adapter.itemTouchHelper = itemTouchHelper

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ConnectivityObserver(this@DetectorListingActivity)
                    .observeConnectivity()
                    .collect { connected ->
                        isNetworkAvailable = connected
                        updateReorderAvailability()
                    }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                db.detectorDao().observeUnsyncedCountByReport(reportId).collect { unsyncedCount ->
                    areAllDetectorsSynced = unsyncedCount == 0
                    updateReorderAvailability()
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

    private fun updateReorderAvailability() {
        val canReorder = isNetworkAvailable &&
            reportStatus == TenantReportStatus.IN_PROGRESS.value &&
            areAllDetectorsSynced
        adapter.setEditMode(true, canReorder = canReorder)
    }
}
