package com.wooma.adapter

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.wooma.activities.report.otherItems.AddEditKeysActivity
import com.wooma.R
import com.wooma.data.network.ApiClient
import com.wooma.model.ImageItem
import com.wooma.model.enums.TenantReportStatus
import com.wooma.model.KeyItem

class InventoryKeysAdapter(
    val context: Context,
    private val originalList: MutableList<KeyItem>,
    val reportId: String,
    val reportStatus: String = "",
    val showTimestamp: Boolean = true,
    private val onReorder: ((keyId: String, prevRank: String?, nextRank: String?) -> Unit)? = null
) : RecyclerView.Adapter<InventoryKeysAdapter.ViewHolder>() {
    var isEditMode = false
        private set
    private var canReorder = false
    var itemTouchHelper: ItemTouchHelper? = null
    private var dragFromPosition = -1

    private var filteredList = originalList.toMutableList()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvItemName: TextView = view.findViewById(R.id.tvItemName)
        val ivSync: ImageView = view.findViewById(R.id.ivSync)
        val ivDragHandle: ImageView = view.findViewById(R.id.ivDragHandle)
        val tvQuantity: TextView = view.findViewById(R.id.tvQuantity)
        val qtyLayout: LinearLayout = view.findViewById(R.id.qtyLayout)
        val tvWhatForLabel: TextView = view.findViewById(R.id.tvWhatForLabel)
        val tvWhatFor: TextView = view.findViewById(R.id.tvWhatFor)
        val rvImages: RecyclerView = view.findViewById(R.id.rvImages)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_keys, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = filteredList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = filteredList[position]
        holder.ivDragHandle.visibility = if (isEditMode && canReorder) View.VISIBLE else View.GONE
        holder.ivSync.visibility = if (isEditMode) View.GONE else View.VISIBLE
        if (!isEditMode) {
            holder.ivSync.setImageResource(if (item.isSyncing) R.drawable.svg_syncing else R.drawable.svg_synced)
        }
        holder.ivDragHandle.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) itemTouchHelper?.startDrag(holder)
            false
        }

        holder.tvItemName.text = item.name

        val qty = item.no_of_keys ?: 0
        holder.tvQuantity.visibility = if (qty > 0) View.VISIBLE else View.GONE
        holder.qtyLayout.visibility = if (qty > 0) View.VISIBLE else View.GONE
        holder.tvQuantity.text = "$qty"

        val noteVisible = !item.note.isNullOrEmpty()
        holder.tvWhatForLabel.visibility = if (noteVisible) View.VISIBLE else View.GONE
        holder.tvWhatFor.visibility = if (noteVisible) View.VISIBLE else View.GONE
        holder.tvWhatFor.text = item.note ?: ""

        val imageItems = item.attachments.map {
            if (!it.storageKey.isNullOrEmpty()) {
                ImageItem.Remote(it.id, "${ApiClient.IMAGE_BASE_URL}${it.storageKey}")
            } else {
                ImageItem.Local(android.net.Uri.parse(it.link ?: ""))
            }
        }.toMutableList<ImageItem>()
        holder.rvImages.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        holder.rvImages.adapter = ImageAdapter(imageItems, showDelete = false, title = item.name)

        holder.itemView.setOnClickListener {
            if (reportStatus != TenantReportStatus.IN_PROGRESS.value) return@setOnClickListener
            context.startActivity(
                Intent(context, AddEditKeysActivity::class.java).putExtra(
                    "keyItem",
                    filteredList[position]
                ).putExtra("reportId", reportId)
                    .putExtra("showTimestamp", showTimestamp)
            )
        }
    }

    fun updateList(list: List<KeyItem>) {
        filteredList = list.toMutableList()
        notifyDataSetChanged()
    }

    fun setEditMode(editMode: Boolean, canReorder: Boolean = true) {
        isEditMode = editMode
        this.canReorder = canReorder
        notifyDataSetChanged()
    }

    fun onItemMove(from: Int, to: Int) {
        if (from !in filteredList.indices || to !in filteredList.indices) return
        if (dragFromPosition == -1) dragFromPosition = from
        java.util.Collections.swap(filteredList, from, to)
        notifyItemMoved(from, to)
    }

    fun onDropCompleted(finalPosition: Int) {
        if (dragFromPosition == -1 || finalPosition !in filteredList.indices) return
        val moved = filteredList[finalPosition]
        val prevRank = if (finalPosition > 0) filteredList[finalPosition - 1].display_order else null
        val nextRank = if (finalPosition < filteredList.size - 1) filteredList[finalPosition + 1].display_order else null
        onReorder?.invoke(moved.id, prevRank, nextRank)
        dragFromPosition = -1
    }

    fun filter(query: String) {
        filteredList = if (query.isEmpty()) {
            originalList.toMutableList()
        } else {
            originalList.filter {
                it.name.contains(query, true)
            }.toMutableList()
        }
        notifyDataSetChanged()
    }
}
