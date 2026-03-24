package com.wooma.business.activities.report

import android.os.Bundle
import com.wooma.business.activities.BaseActivity
import com.wooma.business.adapter.AddRoomAdapter
import com.wooma.business.customs.AddCustomRoomDialog
import com.wooma.business.databinding.ActivityAddRoomsBinding
import com.wooma.business.model.Rooms

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