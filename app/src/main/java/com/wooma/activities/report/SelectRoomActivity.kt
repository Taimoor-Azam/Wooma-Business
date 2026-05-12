package com.wooma.activities.report

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.recyclerview.widget.LinearLayoutManager
import com.wooma.activities.BaseActivity
import com.wooma.adapter.SelectRoomItemAdapter
import com.wooma.data.network.showToast
import com.wooma.data.repository.RoomRepository
import com.wooma.databinding.ActivitySelectRoomItemBinding
import com.wooma.sync.SyncScheduler
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

class SelectRoomActivity : BaseActivity() {

    private lateinit var binding: ActivitySelectRoomItemBinding
    private lateinit var adapter: SelectRoomItemAdapter

    private var reportId = ""
    private val checkedItems = mutableSetOf<String>()
    private val roomRepo by lazy { RoomRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySelectRoomItemBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        binding.tvTitle.text = "Add Room"
        binding.tvCustomLabel.text = "Custom Room"
        reportId = intent.getStringExtra("reportId") ?: ""

        adapter = SelectRoomItemAdapter(PREDEFINED_ROOMS.toMutableList(), checkedItems)
        binding.rvItems.layoutManager = LinearLayoutManager(this)
        binding.rvItems.adapter = adapter

        binding.ivBack.setOnClickListener { finish() }

        binding.etCustomName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrBlank()
                binding.tvAddCustom.alpha = if (hasText) 1f else 0.3f
                binding.tvAddCustom.isClickable = hasText
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.tvAddCustom.setOnClickListener {
            val name = binding.etCustomName.text.toString().trim()
            if (name.isNotEmpty()) {
                adapter.addCustomItem(name)
                binding.etCustomName.setText("")
            }
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnSave.setOnClickListener {
            if (checkedItems.isEmpty()) {
                showToast("Please select at least one room")
                return@setOnClickListener
            }
            if (reportId.isEmpty()) {
                val result = Intent().putStringArrayListExtra(
                    RESULT_ROOMS, ArrayList(checkedItems)
                )
                setResult(RESULT_OK, result)
                finish()
            } else {
                addRoomsOfflineFirst()
            }
        }
    }

    private fun addRoomsOfflineFirst() {
        lifecycleScope.launch {
            for (roomName in checkedItems) {
                roomRepo.addRoom(reportId, roomName)
            }
            // WorkManager uses CONNECTED — enqueue always so sync runs when network is available (offline-first).
            SyncScheduler.scheduleImmediateSync(this@SelectRoomActivity)
            showToast("Rooms added successfully")
            finish()
        }
    }

    companion object {
        const val RESULT_ROOMS = "result_rooms"
        val PREDEFINED_ROOMS = listOf(
            "Living room",
            "Lounge",
            "Sitting room",
            "Reception room",
            "Front room",
            "Drawing room",
            "Snug",
            "Family room",
            "Dining room",
            "Open plan living/dining",
            "Living/dining room",
            "Kitchen",
            "Kitchen/diner",
            "Breakfast room",
            "Galley kitchen",
            "Kitchenette",
            "Open plan kitchen/living",
            "Master bedroom",
            "Bedroom 1",
            "Bedroom 2",
            "Bedroom 3",
            "Bedroom 4",
            "Bedroom 5",
            "Guest bedroom",
            "Box room",
            "Single bedroom",
            "Double bedroom",
            "Spare room",
            "Bathroom",
            "En-suite",
            "En-suite bathroom",
            "Master en-suite",
            "Shower room",
            "WC",
            "Cloakroom",
            "Downstairs WC",
            "Toilet",
            "Jack and Jill bathroom",
            "Family bathroom",
            "Entrance hall",
            "Hallway",
            "Hall",
            "Landing",
            "First floor landing",
            "Second floor landing",
            "Staircase",
            "Stairs",
            "Lobby",
            "Utility room",
            "Laundry room",
            "Boot room",
            "Scullery",
            "Pantry",
            "Larder",
            "Airing cupboard",
            "Storage cupboard",
            "Cupboard",
            "Under stairs cupboard",
            "Boiler cupboard",
            "Walk-in wardrobe",
            "Dressing room",
            "Loft",
            "Attic",
            "Cellar",
            "Basement",
            "Study",
            "Home office",
            "Office",
            "Library",
            "Den",
            "Conservatory",
            "Orangery",
            "Garden room",
            "Sun room",
            "Porch",
            "Enclosed porch",
            "Games room",
            "Playroom",
            "Gym",
            "Cinema room",
            "Music room",
            "Wine cellar",
            "Workshop",
            "Garage",
            "Integral garage",
            "Garden",
            "Front garden",
            "Rear garden",
            "Back garden",
            "Balcony",
            "Terrace",
            "Patio",
            "Decking",
            "Courtyard",
            "Roof terrace",
            "Driveway",
            "Parking space",
            "Carport",
            "Shed",
            "Outbuilding",
            "Summer house",
            "Greenhouse",
            "Communal entrance",
            "Communal hallway",
            "Communal staircase",
            "Communal garden",
            "Bike store",
            "Bin store",
            "Residents lounge",
            "Concierge"
        )
    }
}
