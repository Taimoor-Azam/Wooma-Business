package com.wooma.adapter

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hbb20.CountryCodePicker
import com.wooma.R
import com.wooma.model.Tenant

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
        val ccp: CountryCodePicker = view.findViewById(R.id.ccp)

        val phoneTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                syncPhoneToModel()
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        private val firstNameWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION && pos in originalList.indices) {
                    originalList[pos].first_name = etFirstName.text?.toString().orEmpty()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        private val lastNameWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION && pos in originalList.indices) {
                    originalList[pos].last_name = etLastName.text?.toString().orEmpty()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        private val emailWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION && pos in originalList.indices) {
                    originalList[pos].email_address = etEmail.text?.toString().orEmpty()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        init {
            ccp.registerCarrierNumberEditText(etPhone)
            ccp.setOnCountryChangeListener { syncPhoneToModel() }
        }

        fun syncPhoneToModel() {
            val pos = bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION || pos !in originalList.indices) return
            originalList[pos].mobile_number =
                if (etPhone.text.isNullOrBlank()) "" else (ccp.fullNumberWithPlus ?: "")
        }

        fun attachFieldWatchers() {
            etFirstName.removeTextChangedListener(firstNameWatcher)
            etLastName.removeTextChangedListener(lastNameWatcher)
            etEmail.removeTextChangedListener(emailWatcher)
            etPhone.removeTextChangedListener(phoneTextWatcher)
            etFirstName.addTextChangedListener(firstNameWatcher)
            etLastName.addTextChangedListener(lastNameWatcher)
            etEmail.addTextChangedListener(emailWatcher)
            etPhone.addTextChangedListener(phoneTextWatcher)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_add_tenants, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = originalList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tenant = originalList[position]
        holder.tvTenantPosition.text = "Tenant " + (position + 1)

        if (originalList.size == 1 && position == 0) {
            holder.ivDelete.visibility = View.GONE
        } else {
            holder.ivDelete.visibility = View.VISIBLE
        }

        holder.etFirstName.setText(tenant.first_name)
        holder.etLastName.setText(tenant.last_name)
        holder.etEmail.setText(tenant.email_address)

        holder.etPhone.removeTextChangedListener(holder.phoneTextWatcher)
        if (tenant.mobile_number.isNotBlank()) {
            try {
                holder.ccp.setFullNumber(tenant.mobile_number)
            } catch (_: Exception) {
                holder.etPhone.setText(tenant.mobile_number)
            }
        } else {
            holder.etPhone.setText("")
        }

        holder.attachFieldWatchers()

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
