package com.wooma.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.wooma.activities.report.InspectionRoomActivity
import com.wooma.activities.report.InventoryRoomItemsListActivity
import com.wooma.R
import com.wooma.customs.EditRoomNameDialog
import com.wooma.model.PropertyReportType
import com.wooma.model.RoomsResponse
import com.wooma.model.enums.ReportTypes
import java.util.Collections

class InventoryRoomsAdapter(
    val context: Context,
    private val originalList: MutableList<RoomsResponse>,
    val reportId: String,
    val reportStatus: String,
    var reportType: PropertyReportType? = null,
    var showTimestamp: Boolean = true,
    private val onDeleteRoom: ((roomId: String?) -> Unit)? = null,
    private val onReorder: ((roomId: String, prevRank: String?, nextRank: String?) -> Unit)? = null,
    private val onUpdateRoom: ((roomId: String, newName: String) -> Unit)? = null,
) : RecyclerView.Adapter<InventoryRoomsAdapter.ViewHolder>() {

    var isEditMode = false
        private set
    private var canReorder = true

    private var filteredList = originalList.toMutableList()
    private var pendingItemRoomIds: Set<String> = emptySet()
    private var dragFromPosition = -1
    private var localOrderOverrideIds: List<String>? = null
    var itemTouchHelper: ItemTouchHelper? = null

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvAddress: TextView = view.findViewById(R.id.tvAddress)
        val roomMainLayout: ConstraintLayout = view.findViewById(R.id.roomMainLayout)
        val ivDragHandle: ImageView = view.findViewById(R.id.ivDragHandle)
        val ivEdit: ImageView = view.findViewById(R.id.ivEdit)
        val ivDelete: ImageView = view.findViewById(R.id.ivDelete)
        val imgArrow: ImageView = view.findViewById(R.id.imgArrow)
        val ivSync: ImageView = view.findViewById(R.id.ivSync)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inventory_room, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = filteredList.size

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val room = filteredList[position]
        holder.tvAddress.text = room.name

        holder.ivDragHandle.visibility = if (isEditMode && canReorder) View.VISIBLE else View.GONE
        holder.ivEdit.visibility = if (isEditMode) View.VISIBLE else View.GONE
        holder.ivDelete.visibility = if (isEditMode) View.VISIBLE else View.GONE
        holder.imgArrow.visibility = if (isEditMode) View.GONE else View.VISIBLE
        holder.ivSync.visibility = if (isEditMode) View.GONE else View.VISIBLE
        val hasPendingItemSync = room.id?.let { pendingItemRoomIds.contains(it) } == true
        val shouldShowSyncing = room.isSyncing || hasPendingItemSync
        holder.ivSync.setImageResource(
            if (shouldShowSyncing) R.drawable.svg_syncing else R.drawable.svg_synced
        )

        holder.ivDragHandle.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                itemTouchHelper?.startDrag(holder)
            }
            false
        }

        holder.ivEdit.setOnClickListener {
            val activity = context as? FragmentActivity ?: return@setOnClickListener
            EditRoomNameDialog(room.name ?: "") { newName ->
                onUpdateRoom?.invoke(room.id ?: return@EditRoomNameDialog, newName)
            }.show(activity.supportFragmentManager, "EditRoom")
        }

        holder.ivDelete.setOnClickListener {
            onDeleteRoom?.invoke(room.id)
        }

        holder.roomMainLayout.setOnClickListener {
            if (isEditMode) return@setOnClickListener
            val isInspection = reportType?.type_code == ReportTypes.INSPECTION.value
            if (isInspection) {
                context.startActivity(
                    Intent(context, InspectionRoomActivity::class.java)
                        .putExtra("room", room)
                        .putExtra("reportId", reportId)
                        .putExtra("reportStatus", reportStatus)
                        .putExtra("showTimestamp", showTimestamp)
                )
            } else {
                context.startActivity(
                    Intent(context, InventoryRoomItemsListActivity::class.java)
                        .putParcelableArrayListExtra("roomItems", room.items)
                        .putExtra("roomName", room.name ?: "")
                        .putExtra("roomId", room.id ?: "")
                        .putExtra("reportId", reportId)
                        .putExtra("reportStatus", reportStatus)
                        .putExtra("reportType", reportType)
                        .putExtra("showTimestamp", showTimestamp)
                )
            }
        }
    }

    fun setEditMode(editMode: Boolean, canReorder: Boolean = true) {
        isEditMode = editMode
        this.canReorder = canReorder
        notifyDataSetChanged()
    }

    fun onItemMove(from: Int, to: Int) {
        if (dragFromPosition == -1) dragFromPosition = from
        Collections.swap(filteredList, from, to)
        notifyItemMoved(from, to)
    }

    fun onDropCompleted(finalPosition: Int) {
        if (dragFromPosition == -1 || finalPosition < 0 || finalPosition >= filteredList.size) return
        val movedRoom = filteredList[finalPosition]
        val prevRank = if (finalPosition > 0) filteredList[finalPosition - 1].displayOrder else null
        val nextRank = if (finalPosition < filteredList.size - 1) filteredList[finalPosition + 1].displayOrder else null
        localOrderOverrideIds = filteredList.mapNotNull { it.id }
        onReorder?.invoke(movedRoom.id ?: "", prevRank, nextRank)
        dragFromPosition = -1
    }

    fun updateList(list: List<RoomsResponse>) {
        val incoming = list.toMutableList()
        val overrideIds = localOrderOverrideIds
        if (overrideIds != null) {
            val incomingIds = incoming.mapNotNull { it.id }
            if (incomingIds.toSet() == overrideIds.toSet() && incomingIds.size == overrideIds.size) {
                val byId = incoming.associateBy { it.id }
                filteredList = overrideIds.mapNotNull { byId[it] }.toMutableList()
            } else {
                // Structural list change (add/remove): drop override and follow source order.
                localOrderOverrideIds = null
                filteredList = incoming
            }
        } else {
            filteredList = incoming
        }
        notifyDataSetChanged()
    }

    fun updatePendingItemRoomIds(ids: Set<String>) {
        pendingItemRoomIds = ids
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        filteredList = if (query.isEmpty()) {
            originalList.toMutableList()
        } else {
            originalList.filter {
                it.name?.contains(query, true) == true
            }.toMutableList()
        }
        notifyDataSetChanged()
    }
}
