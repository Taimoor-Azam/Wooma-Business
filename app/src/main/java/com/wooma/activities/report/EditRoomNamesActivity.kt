package com.wooma.activities.report

import android.os.Build
import android.os.Bundle
import com.wooma.activities.BaseActivity
import com.wooma.adapter.EditRoomNamesAdapter
import com.wooma.databinding.ActivityEditRoomNamesBinding
import com.wooma.model.Rooms

class EditRoomNamesActivity : BaseActivity() {
    private lateinit var binding: ActivityEditRoomNamesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityEditRoomNamesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        val roomNamesList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(
                "roomNamesList",
                Rooms::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra("roomNamesList")
        }

        binding.ivBack.setOnClickListener { finish() }

        if (!roomNamesList.isNullOrEmpty()) {
            binding.rvRoomNames.adapter =
                EditRoomNamesAdapter(this, roomNamesList.toMutableList())
        }
    }
}