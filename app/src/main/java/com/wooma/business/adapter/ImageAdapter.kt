package com.wooma.business.adapter

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.wooma.business.R
import com.wooma.business.customs.Utils
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.data.network.showToast
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.ImageItem

class ImageAdapter(
    private val list: MutableList<ImageItem>,
    private val showDelete: Boolean = true,
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

        when (item) {
            is ImageItem.Local -> Glide.with(holder.itemView.context).load(item.uri).into(holder.image)
            is ImageItem.Remote -> Glide.with(holder.itemView.context).load(item.url).into(holder.image)
        }

        holder.image.setOnClickListener {
            when (item) {
                is ImageItem.Local -> Utils.showFullScreenImage(holder.itemView.context, uri = item.uri)
                is ImageItem.Remote -> Utils.showFullScreenImage(holder.itemView.context, url = item.url)
            }
        }

        if (!showDelete) {
            holder.delete.visibility = View.GONE
        } else {
            holder.delete.visibility = View.VISIBLE
            holder.delete.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos == RecyclerView.NO_ID.toInt()) return@setOnClickListener
                val ctx = holder.itemView.context
                val activity = ctx as? Activity ?: return@setOnClickListener
                Utils.showDialogBox(ctx, "Delete Image", "Are you sure you want to delete this image?") {
                    when (val current = list[pos]) {
                        is ImageItem.Remote -> {
                            activity.makeApiRequest(
                                apiServiceClass = MyApi::class.java,
                                context = activity,
                                showLoading = true,
                                requestAction = { api -> api.deleteAttachment(current.id) },
                                listener = object : ApiResponseListener<ApiResponse<Any>> {
                                    override fun onSuccess(response: ApiResponse<Any>) {
                                        if (response.success) {
                                            list.removeAt(pos)
                                            notifyItemRemoved(pos)
                                            onDelete?.invoke()
                                        }
                                    }
                                    override fun onFailure(errorMessage: ErrorResponse?) {
                                        ctx.showToast(errorMessage?.error?.message ?: "Failed to delete image")
                                    }
                                    override fun onError(throwable: Throwable) {
                                        ctx.showToast("Error: ${throwable.message}")
                                    }
                                }
                            )
                        }
                        is ImageItem.Local -> {
                            list.removeAt(pos)
                            notifyItemRemoved(pos)
                            onDelete?.invoke()
                        }
                    }
                }
            }
        }
    }

    override fun getItemCount() = list.size
}
