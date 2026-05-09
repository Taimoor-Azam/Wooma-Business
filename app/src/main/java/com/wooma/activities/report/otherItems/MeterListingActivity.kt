package com.wooma.activities.report.otherItems

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.wooma.activities.BaseActivity
import com.wooma.adapter.InventoryMetersAdapter
import com.wooma.data.local.WoomaDatabase
import com.wooma.data.repository.OtherItemsRepository
import com.wooma.databinding.ActivityInventoryMeterListBinding
import com.wooma.model.OtherItemsAttachment
import com.wooma.model.enums.TenantReportStatus
import com.wooma.sync.ConnectivityObserver
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MeterListingActivity : BaseActivity() {
    private lateinit var adapter: InventoryMetersAdapter
    private val metersList = mutableListOf<com.wooma.model.Meter>()
    private lateinit var binding: ActivityInventoryMeterListBinding
    var reportId = ""
    var reportStatus = ""
    var showTimestamp = true
    private var isNetworkAvailable = false
    private var areAllMetersSynced = false
    private var isEditMode = false

    private val repo by lazy { OtherItemsRepository(this) }
    private val db by lazy { WoomaDatabase.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityInventoryMeterListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        reportId = intent.getStringExtra("reportId") ?: ""
        reportStatus = intent.getStringExtra("reportStatus") ?: ""
        showTimestamp = intent.getBooleanExtra("showTimestamp", true)

        adapter = InventoryMetersAdapter(
            this,
            metersList,
            reportId,
            reportStatus,
            showTimestamp,
            onReorder = { meterId, prevRank, nextRank ->
                lifecycleScope.launch {
                    val canReorderNow = isNetworkAvailable &&
                        reportStatus == TenantReportStatus.IN_PROGRESS.value &&
                        areAllMetersSynced
                    if (!canReorderNow) {
                        adapter.updateList(metersList)
                        return@launch
                    }
                    repo.reorderMeter(meterId, prevRank, nextRank)
                }
            }
        )
        binding.rvMeters.adapter = adapter
        binding.ivBack.setOnClickListener { finish() }
        adapter.setEditMode(false, canReorder = false)

        if (reportStatus != TenantReportStatus.IN_PROGRESS.value) {
            binding.ivAdd.visibility = View.GONE
            binding.tvEdit.visibility = View.GONE
        } else {
            binding.tvEdit.visibility = View.VISIBLE
        }

        binding.ivAdd.setOnClickListener {
            startActivity(
                Intent(this, AddEditMeterActivity::class.java)
                    .putExtra("reportId", reportId)
                    .putExtra("showTimestamp", showTimestamp)
            )
        }
        binding.tvEdit.setOnClickListener {
            isEditMode = !isEditMode
            binding.tvEdit.text = if (isEditMode) "Done" else "Edit"
            updateReorderAvailability()
        }

        // Observe meters from Room — instant, works offline
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    repo.observeMeters(reportId),
                    db.attachmentDao().observeByEntityType("METER")
                ) { meters, allAttachments ->
                    meters.map { meter ->
                        val meterAttachments = allAttachments.filter { it.entityId == meter.id }
                        val hasPendingImageUpload = meterAttachments.any { !it.isUploaded }
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
                                    link = att.localUri?.let { "file://$it" }
                                        ?: if (att.isUploaded) att.link else null,
                                    mimeType = att.mimeType,
                                    fileSize = att.fileSize.toString()
                                )
                            },
                            isSyncing = meter.isSyncing || hasPendingImageUpload
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

        val touchCallback = object : ItemTouchHelper.Callback() {
            private val edgeScrollThresholdPx by lazy { (72 * resources.displayMetrics.density).toInt() }
            private val edgeScrollStepPx by lazy { (16 * resources.displayMetrics.density).toInt() }

            override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                val canMove = adapter.isEditMode && isNetworkAvailable
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
                ConnectivityObserver(this@MeterListingActivity)
                    .observeConnectivity()
                    .collect { connected ->
                        isNetworkAvailable = connected
                        updateReorderAvailability()
                    }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                db.meterDao().observeUnsyncedCountByReport(reportId).collect { unsyncedCount ->
                    areAllMetersSynced = unsyncedCount == 0
                    updateReorderAvailability()
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

    private fun updateReorderAvailability() {
        val canReorder = isNetworkAvailable &&
            reportStatus == TenantReportStatus.IN_PROGRESS.value &&
            areAllMetersSynced
        if (!canReorder && isEditMode) {
            isEditMode = false
            binding.tvEdit.text = "Edit"
        }
        val editEnabled = canReorder || isEditMode
        binding.tvEdit.isEnabled = editEnabled
        binding.tvEdit.alpha = if (editEnabled) 1f else 0.4f
        adapter.setEditMode(isEditMode, canReorder = canReorder)
    }
}
