package com.wooma.adapter

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.net.Uri
import androidx.recyclerview.widget.ItemTouchHelper
import com.wooma.activities.report.InventoryRoomItemActivity
import com.wooma.R
import com.wooma.data.local.entity.RatingEntity
import com.wooma.data.network.ApiClient
import com.wooma.model.ImageItem
import com.wooma.model.RoomItem
import com.wooma.model.PropertyReportType
import com.wooma.model.enums.TenantReportStatus

class InventoryRoomItemsAdapter(
    val context: Context,
    private val originalList: MutableList<RoomItem>,
    val reportId: String,
    val roomId: String,
    val reportStatus: String,
    val reportType: PropertyReportType? = null,
    val showTimestamp: Boolean = true,
    private val onReorder: ((itemId: String, prevRank: String?, nextRank: String?) -> Unit)? = null
) : RecyclerView.Adapter<InventoryRoomItemsAdapter.ViewHolder>() {

    var isEditMode = false
        private set
    private var canReorder = false
    var itemTouchHelper: ItemTouchHelper? = null
    private var dragFromPosition = -1

    private var filteredList = originalList.toMutableList()
    private var conditionRatings = emptyList<RatingEntity>()
    private var cleanlinessRatings = emptyList<RatingEntity>()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvItemName: TextView = view.findViewById(R.id.tvItemName)
        val tvDescription: TextView = view.findViewById(R.id.tvDescription)
        val tvDescLabel: TextView = view.findViewById(R.id.tvDescLabel)
        val tvCondition: TextView = view.findViewById(R.id.tvCondition)
        val tvCleanliness: TextView = view.findViewById(R.id.tvCleanliness)
        val tvNotesLabel: TextView = view.findViewById(R.id.tvNotesLabel)
        val tvNotes: TextView = view.findViewById(R.id.tvNotes)
        val llConditionBadge: LinearLayout = view.findViewById(R.id.llConditionBadge)
        val llCleanlinessBadge: LinearLayout = view.findViewById(R.id.llCleanlinessBadge)
        val rvImages: RecyclerView = view.findViewById(R.id.rvImages)
        val ivSync: ImageView = view.findViewById(R.id.ivSync)
        val ivDragHandle: ImageView = view.findViewById(R.id.ivDragHandle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_room_items, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = filteredList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = filteredList[position]

        holder.tvItemName.text = item.name
        holder.ivSync.visibility = View.VISIBLE
        holder.ivDragHandle.visibility = if (isEditMode && canReorder) View.VISIBLE else View.GONE
        val syncIcon = when {
            item.isPendingDeletion -> R.drawable.svg_unsyncing
            item.isSyncing -> R.drawable.svg_syncing
            else -> R.drawable.svg_synced
        }
        holder.ivSync.setImageResource(syncIcon)
        holder.ivDragHandle.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) itemTouchHelper?.startDrag(holder)
            false
        }
        val description = item.description.orEmpty()
        holder.tvDescription.text = description
        holder.tvDescription.visibility = if (description.isBlank()) View.GONE else View.VISIBLE
        holder.tvDescLabel.visibility = if (description.isBlank()) View.GONE else View.VISIBLE

        val notes = item.note.orEmpty()
        holder.tvNotes.text = notes
        val notesVisible = if (notes.isBlank()) View.GONE else View.VISIBLE
        holder.tvNotesLabel.visibility = notesVisible
        holder.tvNotes.visibility = notesVisible

        val imageItems = item.attachments
            ?.mapNotNull { att ->
                when {
                    !att.storageKey.isNullOrEmpty() ->
                        ImageItem.Remote(
                            att.id ?: "",
                            "${ApiClient.IMAGE_BASE_URL}${att.storageKey}"
                        )

                    !att.url.isNullOrEmpty() ->
                        ImageItem.Local(Uri.parse(att.url))

                    else -> null
                }
            }
            ?.toMutableList<ImageItem>() ?: mutableListOf()
        holder.rvImages.visibility = if (imageItems.isEmpty()) View.GONE else View.VISIBLE
        holder.rvImages.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        holder.rvImages.adapter =
            ImageAdapter(imageItems, showDelete = false, title = item.name ?: "")

        val conditionCode = item.general_condition
        val conditionDisplay =
            conditionRatings.firstOrNull { it.typeCode == conditionCode }?.displayName
                ?: conditionCode?.replaceFirstChar { it.uppercaseChar() }
                ?: "Excellent"
        holder.tvCondition.text = conditionDisplay
        applyBadgeStyle(holder.llConditionBadge, holder.tvCondition, conditionCode)

        val cleanlinessCode = item.general_cleanliness
        val cleanlinessDisplay =
            cleanlinessRatings.firstOrNull { it.typeCode == cleanlinessCode }?.displayName
                ?: cleanlinessCode?.replaceFirstChar { it.uppercaseChar() }
                ?: "Excellent"
        holder.tvCleanliness.text = cleanlinessDisplay
        applyBadgeStyle(holder.llCleanlinessBadge, holder.tvCleanliness, cleanlinessCode)

        holder.itemView.setOnClickListener {
            if (item.isPendingDeletion) return@setOnClickListener
            if (reportStatus == TenantReportStatus.IN_PROGRESS.value) {
                context.startActivity(
                    Intent(context, InventoryRoomItemActivity::class.java).putExtra(
                        "roomItem",
                        filteredList[position]
                    ).putExtra("roomId", roomId)
                        .putExtra("reportId", reportId)
                        .putExtra("reportType", reportType)
                        .putExtra("showTimestamp", showTimestamp)
                )
            }
        }
    }

    private fun applyBadgeStyle(badge: LinearLayout, label: TextView, typeCode: String?) {
        val bgColor: Int
        val textColor: Int
        when (typeCode?.lowercase()) {
            "excellent", "good" -> {
                bgColor = Color.parseColor("#dbf7de")
                textColor = Color.parseColor("#00C802")
            }

            "poor" -> {
                bgColor = Color.parseColor("#f3ece0")
                textColor = Color.parseColor("#a38b6f")
            }

            "unacceptable" -> {
                bgColor = Color.parseColor("#fce7e5")
                textColor = Color.parseColor("#b26267")
            }

            else -> {
                bgColor = Color.parseColor("#ebebeb")
                textColor = Color.parseColor("#5b5b5b")
            }
        }
        val strokeWidth = (context.resources.displayMetrics.density + 0.5f).toInt()
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 50f
            setColor(bgColor)
            setStroke(strokeWidth, textColor)
        }
        badge.background = drawable
        label.setTextColor(textColor)
    }

    fun setRatings(condition: List<RatingEntity>, cleanliness: List<RatingEntity>) {
        conditionRatings = condition
        cleanlinessRatings = cleanliness
        notifyDataSetChanged()
    }

    fun updateList(list: List<RoomItem>) {
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
        val itemId = moved.id ?: ""
        if (itemId.isNotEmpty()) onReorder?.invoke(itemId, prevRank, nextRank)
        dragFromPosition = -1
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
