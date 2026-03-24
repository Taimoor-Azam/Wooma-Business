package com.wooma.business.adapter

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.wooma.business.R
import com.wooma.business.model.Rooms
import com.wooma.business.model.Tenant
import com.wooma.business.model.Users
import org.w3c.dom.Text

class AddTenantsAdapter(
    val context: Context,
) : RecyclerView.Adapter<AddTenantsAdapter.ViewHolder>() {
    private var originalList: MutableList<Tenant> = mutableListOf()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivDelete: ImageView = view.findViewById(R.id.ivDelete)
        val tvTenantPosition: TextView = view.findViewById(R.id.tvTenantPosition)
        val etFirstName: EditText = view.findViewById(R.id.etFirstName)
        val etLastName: EditText = view.findViewById(R.id.etLastName)
        val etEmail: EditText = view.findViewById(R.id.etEmail)
        val etPhone: EditText = view.findViewById(R.id.etPhone)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_add_tenants, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = originalList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.tvTenantPosition.text = "Tenant " + (position + 1)

        if (originalList.size == 1 && position == 0) {
            holder.ivDelete.visibility = View.GONE
        } else {
            holder.ivDelete.visibility = View.VISIBLE
        }

        holder.etFirstName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                originalList[position].first_name = holder.etFirstName.text.toString()
            }

            override fun afterTextChanged(s: Editable?) {
            }
        })

        holder.etLastName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                originalList[position].last_name = holder.etLastName.text.toString()
            }

            override fun afterTextChanged(s: Editable?) {
            }
        })

        holder.etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                originalList[position].email_address = holder.etEmail.text.toString()
            }

            override fun afterTextChanged(s: Editable?) {
            }
        })

        holder.etPhone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                originalList[position].mobile_number = holder.etPhone.text.toString()
            }

            override fun afterTextChanged(s: Editable?) {
            }
        })

        holder.ivDelete.setOnClickListener {
            originalList.removeAt(position)
            notifyDataSetChanged()
        }
    }

    fun updateItem(item: Tenant) {
        originalList.add(item)
        notifyDataSetChanged()
    }

    fun getItems(): ArrayList<Tenant> {
        return originalList as ArrayList<Tenant>
    }
}
