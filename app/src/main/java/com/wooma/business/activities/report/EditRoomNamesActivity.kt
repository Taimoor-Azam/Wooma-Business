package com.wooma.business.activities.report

import android.os.Build
import android.os.Bundle
import com.wooma.business.activities.BaseActivity
import com.wooma.business.adapter.EditRoomNamesAdapter
import com.wooma.business.databinding.ActivityEditRoomNamesBinding
import com.wooma.business.model.Rooms

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