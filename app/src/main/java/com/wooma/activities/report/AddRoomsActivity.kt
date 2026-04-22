package com.wooma.activities.report

import android.os.Bundle
import com.wooma.activities.BaseActivity
import com.wooma.adapter.AddRoomAdapter
import com.wooma.customs.AddCustomRoomDialog
import com.wooma.databinding.ActivityAddRoomsBinding
import com.wooma.model.Rooms

class AddRoomsActivity : BaseActivity() {
    private lateinit var adapter: AddRoomAdapter
    private val roomsList = mutableListOf<Rooms>()

    private lateinit var binding: ActivityAddRoomsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAddRoomsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        adapter = AddRoomAdapter(this, roomsList)
        binding.rvRooms.adapter = adapter

        loadProperties()

        binding.ivBack.setOnClickListener { finish() }

        binding.addCustomRoomLayout.setOnClickListener {
            AddCustomRoomDialog().show(
                supportFragmentManager,
                "InputBottomSheet"
            )
        }
    }

    private fun loadProperties() {
        roomsList.addAll(
            listOf(
                Rooms("Bedroom"),
                Rooms("Living room"),
                Rooms("Kitchen"),
                Rooms("Bathroom"),
                Rooms("Toilet / WC"),
                Rooms("Hallway"),
                Rooms("Toilet / WC"),
                Rooms("Hallway"),
                Rooms("Bathroom"),
                Rooms("Living room"),
                Rooms("Kitchen")
            )
        )
        adapter.updateList(roomsList)
    }
}