package com.wooma.business.adapter

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
import com.wooma.business.R
import com.wooma.business.customs.EditRoomNameDialog
import com.wooma.business.activities.report.InspectionRoomActivity
import com.wooma.business.activities.report.InventoryRoomItemsListActivity
import com.wooma.business.model.PropertyReportType
import com.wooma.business.model.RoomsResponse
import com.wooma.business.model.enums.ReportTypes
import java.util.Collections

class InventoryRoomsAdapter(
    val context: Context,
    private val originalList: MutableList<RoomsResponse>,
    val reportId: String,
    val reportStatus: String,
    var reportType: PropertyReportType? = null,
    private val onDeleteRoom: ((roomId: String?) -> Unit)? = null,
    private val onReorder: ((roomId: String, prevRank: String?, nextRank: String?) -> Unit)? = null,
    private val onUpdateRoom: ((roomId: String, newName: String) -> Unit)? = null,
) : RecyclerView.Adapter<InventoryRoomsAdapter.ViewHolder>() {

    var isEditMode = false
        private set

    private var filteredList = originalList.toMutableList()
    private var dragFromPosition = -1
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

        holder.ivDragHandle.visibility = if (isEditMode) View.VISIBLE else View.GONE
        holder.ivEdit.visibility = if (isEditMode) View.VISIBLE else View.GONE
        holder.ivDelete.visibility = if (isEditMode) View.VISIBLE else View.GONE
        holder.imgArrow.visibility = if (isEditMode) View.GONE else View.VISIBLE
        holder.ivSync.visibility = if (isEditMode) View.GONE else View.VISIBLE

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
                )
            }
        }
    }

    fun setEditMode(editMode: Boolean) {
        isEditMode = editMode
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
        onReorder?.invoke(movedRoom.id ?: "", prevRank, nextRank)
        dragFromPosition = -1
    }

    fun updateList(list: List<RoomsResponse>) {
        filteredList = list.toMutableList()
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
