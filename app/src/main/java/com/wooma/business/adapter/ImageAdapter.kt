package com.wooma.business.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.wooma.business.R

class ImageAdapter(
    private val list: MutableList<Any>, // Uri for local, String URL for remote
    private val onDelete: (() -> Unit)? = null
) : RecyclerView.Adapter<ImageAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.image)
        val delete: ImageView = view.findViewById(R.id.delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image, parent, false)

        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = list[position]
        Glide.with(holder.itemView.context)
            .load(item) // works for both Uri and String URL
            .into(holder.image)

        holder.delete.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_ID.toInt()) {
                list.removeAt(pos)
                notifyItemRemoved(pos)
                onDelete?.invoke()
            }
        }
    }

    override fun getItemCount() = list.size
}