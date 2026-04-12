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

class AddCustomRoomDialog(private val value: String = "") : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.dialog_add_custom_room, container, false)

        val close = view.findViewById<ImageView>(R.id.ivClose)
        val etRoom = view.findViewById<EditText>(R.id.etRoom)
        val tvLabel = view.findViewById<TextView>(R.id.tvLabel)
        val btnContinue = view.findViewById<AppCompatTextView>(R.id.btnContinue)

        if (value.isNotEmpty()){
            tvLabel.text=value
        }

        close.setOnClickListener {
            dismiss()
        }

        btnContinue.setOnClickListener {
            val input = etRoom.text.toString()
            // handle input here
            val result = Bundle()
            result.putString("added_room", input)

            parentFragmentManager.setFragmentResult("sheet_key", result)
            dismiss()
            dismiss()
        }

        return view
    }
}
