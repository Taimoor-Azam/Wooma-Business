package com.wooma.business.customs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatTextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.wooma.business.R

class EditRoomNameDialog(
    private val currentName: String,
    private val onUpdate: (newName: String) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.dialog_add_custom_room, container, false)

        val ivClose = view.findViewById<ImageView>(R.id.ivClose)
        val tvLabel = view.findViewById<TextView>(R.id.tvLabel)
        val etRoom = view.findViewById<EditText>(R.id.etRoom)
        val btnContinue = view.findViewById<AppCompatTextView>(R.id.btnContinue)

        tvLabel.text = "Edit Room Name"
        etRoom.setText(currentName)
        etRoom.setSelection(currentName.length)
        btnContinue.text = "Update"

        ivClose.setOnClickListener { dismiss() }

        btnContinue.setOnClickListener {
            val newName = etRoom.text.toString().trim()
            if (newName.isNotEmpty()) {
                onUpdate(newName)
                dismiss()
            }
        }

        return view
    }
}
