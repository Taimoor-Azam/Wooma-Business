package com.wooma.adapter

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.wooma.R

class ConditionChipAdapter(
    private val chips: List<String>,
    private val selectedChips: MutableSet<String>,
    private val onChipToggle: (chip: String, isSelected: Boolean) -> Unit
) : RecyclerView.Adapter<ConditionChipAdapter.ChipViewHolder>() {

    inner class ChipViewHolder(val tvChip: TextView) : RecyclerView.ViewHolder(tvChip)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChipViewHolder {
        val tv = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_condition_chip, parent, false) as TextView
        return ChipViewHolder(tv)
    }

    override fun getItemCount() = chips.size

    override fun onBindViewHolder(holder: ChipViewHolder, position: Int) {
        val chip = chips[position]
        val isSelected = selectedChips.contains(chip)
        holder.tvChip.text = chip
        updateChipStyle(holder.tvChip, isSelected)

        holder.tvChip.setOnClickListener {
            val nowSelected = !selectedChips.contains(chip)
            if (nowSelected) selectedChips.add(chip) else selectedChips.remove(chip)
            updateChipStyle(holder.tvChip, nowSelected)
            onChipToggle(chip, nowSelected)
        }
    }

    private fun updateChipStyle(tv: TextView, isSelected: Boolean) {
        val context = tv.context
        val bg = tv.background.mutate() as? GradientDrawable
        if (isSelected) {
            bg?.setColor(ContextCompat.getColor(context, R.color.green))
            bg?.setStroke(0, 0)
            tv.setTextColor(ContextCompat.getColor(context, R.color.white))
        } else {
            bg?.setColor(Color.TRANSPARENT)
            bg?.setStroke(
                (1 * context.resources.displayMetrics.density).toInt(),
                ContextCompat.getColor(context, R.color.border_stroke)
            )
            tv.setTextColor(ContextCompat.getColor(context, R.color.black))
        }
    }
}
