package com.wooma.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import com.wooma.R
import com.wooma.model.Rooms

class EditRoomNamesAdapter(
    val context: Context,
    private val originalList: MutableList<Rooms>,
) : RecyclerView.Adapter<EditRoomNamesAdapter.ViewHolder>() {

    private var filteredList = originalList.toMutableList()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val etRoomName: EditText = view.findViewById(R.id.etRoomName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_edit_room_names, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = filteredList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.etRoomName.setText( filteredList[position].address)

    }

    fun updateList(list: List<Rooms>) {
        filteredList = list.toMutableList()
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        filteredList = if (query.isEmpty()) {
            originalList.toMutableList()
        } else {
            originalList.filter {
                it.address.contains(query, true)
            }.toMutableList()
        }
        notifyDataSetChanged()
    }
}
