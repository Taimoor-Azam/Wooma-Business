
package com.wooma.activities.report

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.wooma.activities.BaseActivity
import com.wooma.adapter.InventoryRoomItemsAdapter
import com.wooma.data.local.WoomaDatabase
import com.wooma.data.repository.RoomItemRepository
import com.wooma.model.Attachment
import kotlinx.coroutines.flow.combine
import com.wooma.databinding.ActivityInventoryRoomsListBinding
import com.wooma.model.PropertyReportType
import com.wooma.model.RoomItem
import com.wooma.model.enums.TenantReportStatus
import com.wooma.sync.SyncScheduler
import com.wooma.sync.ConnectivityObserver
import kotlinx.coroutines.launch

class InventoryRoomItemsListActivity : BaseActivity() {
    private lateinit var binding: ActivityInventoryRoomsListBinding
    private lateinit var adapter: InventoryRoomItemsAdapter

    var reportId = ""
    var roomId = ""
    var reportStatus = ""
    var reportType: PropertyReportType? = null
    var showTimestamp = true
    val roomItems = mutableListOf<RoomItem>()
    private var isNetworkAvailable = false
    private var areAllRoomItemsSynced = false
    private var isEditMode = false

    private val roomItemRepo by lazy { RoomItemRepository(this) }
    private val db by lazy { WoomaDatabase.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityInventoryRoomsListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

//        val roomItems = intent.getParcelableArrayListExtra<RoomItem>("roomItems") ?: ArrayList()
        val roomName = intent.getStringExtra("roomName")
        reportStatus = intent.getStringExtra("reportStatus") ?: ""
        reportId = intent.getStringExtra("reportId") ?: ""
        roomId = intent.getStringExtra("roomId") ?: ""
        reportType = intent.getParcelableExtra("reportType")
        showTimestamp = intent.getBooleanExtra("showTimestamp", true)

        if (!roomName.isNullOrEmpty()) {
            binding.tvTitle.text = roomName
        }

        if (reportStatus != TenantReportStatus.IN_PROGRESS.value) {
            binding.btnAddItem.visibility = View.INVISIBLE
        }

        adapter =
            InventoryRoomItemsAdapter(
                this,
                roomItems,
                reportId,
                roomId,
                reportStatus,
                reportType,
                showTimestamp,
                onReorder = { itemId, prevRank, nextRank ->
                    lifecycleScope.launch {
                        val canReorderNow = isNetworkAvailable &&
                            reportStatus == TenantReportStatus.IN_PROGRESS.value &&
                            areAllRoomItemsSynced
                        if (!canReorderNow) {
                            adapter.updateList(roomItems)
                            return@launch
                        }
                        roomItemRepo.reorderItem(itemId, prevRank, nextRank)
                    }
                }
            )
        binding.rvRoomItems.adapter = adapter
        adapter.setEditMode(false, canReorder = false)

        binding.ivBack.setOnClickListener { finish() }
        binding.btnAddItem.setOnClickListener {
            startActivity(
                Intent(this, SelectRoomItemActivity::class.java)
                    .putExtra("reportId", reportId)
                    .putExtra("roomId", roomId)
            )
        }
        if (reportStatus != TenantReportStatus.IN_PROGRESS.value) {
            binding.tvEdit.visibility = View.GONE
        } else {
            binding.tvEdit.visibility = View.VISIBLE
        }
        binding.tvEdit.setOnClickListener {
            isEditMode = !isEditMode
            binding.tvEdit.text = if (isEditMode) "Done" else "Edit"
            updateReorderAvailability()
        }

        supportFragmentManager.setFragmentResultListener("sheet_key", this) { _, bundle ->
            val value = bundle.getString("added_room")
            if (!value.isNullOrBlank()) addNewRoomItem(value)
        }

        lifecycleScope.launch {
            val conditionRatings = db.ratingDao().getByCategory("condition")
            val cleanlinessRatings = db.ratingDao().getByCategory("cleanliness")
            adapter.setRatings(conditionRatings, cleanlinessRatings)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    roomItemRepo.observeItems(roomId),
                    db.attachmentDao().observeByEntityType("ROOM_ITEM")
                ) { items, allAttachments ->
                    items.map { item ->
                        val itemAttachments = allAttachments.filter { it.entityId == item.id }
                        val hasPendingImageUpload = itemAttachments.any { !it.isUploaded }
                        item.copy(
                            attachments = itemAttachments.map { att ->
                                Attachment(
                                    id = att.serverId ?: att.id,
                                    storageKey = att.storageKey,
                                    url = att.localUri?.let { "file://$it" }
                                        ?: if (att.isUploaded) att.link else null
                                )
                            },
                            isSyncing = item.isSyncing || hasPendingImageUpload
                        )
                    }
                }.collect { items ->
                    roomItems.clear()
                    roomItems.addAll(items)
                    adapter.updateList(roomItems)
                }
            }
        }

        val touchCallback = object : ItemTouchHelper.Callback() {
            private val edgeScrollThresholdPx by lazy { (72 * resources.displayMetrics.density).toInt() }
            private val edgeScrollStepPx by lazy { (16 * resources.displayMetrics.density).toInt() }

            override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int {
                val canMove = adapter.isEditMode && isNetworkAvailable && areAllRoomItemsSynced
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
        itemTouchHelper.attachToRecyclerView(binding.rvRoomItems)
        adapter.itemTouchHelper = itemTouchHelper

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ConnectivityObserver(this@InventoryRoomItemsListActivity)
                    .observeConnectivity()
                    .collect { connected ->
                        isNetworkAvailable = connected
                        updateReorderAvailability()
                    }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                db.roomItemDao().observeUnsyncedCountByRoom(roomId).collect { unsyncedCount ->
                    areAllRoomItemsSynced = unsyncedCount == 0
                    updateReorderAvailability()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            try {
                roomItemRepo.refreshItems(reportId, roomId)
            } catch (_: Exception) {
            }
        }
    }

    private fun addNewRoomItem(name: String) {
        lifecycleScope.launch {
            try {
                roomItemRepo.addItems(roomId, listOf(name))
                SyncScheduler.scheduleImmediateSync(this@InventoryRoomItemsListActivity)
            } catch (_: Exception) {}
        }
    }

    private fun updateReorderAvailability() {
        val canReorder = isNetworkAvailable &&
            reportStatus == TenantReportStatus.IN_PROGRESS.value &&
            areAllRoomItemsSynced
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