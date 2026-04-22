package com.wooma.activities.report

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import androidx.recyclerview.widget.LinearLayoutManager
import com.wooma.activities.BaseActivity
import com.wooma.adapter.SelectRoomItemAdapter
import com.wooma.data.network.ApiResponseListener
import com.wooma.data.network.MyApi
import com.wooma.data.network.makeApiRequest
import com.wooma.data.network.showToast
import com.wooma.databinding.ActivitySelectRoomItemBinding
import com.wooma.model.AddNewRoomItemsRequest
import com.wooma.model.ApiResponse
import com.wooma.model.ErrorResponse
import com.wooma.model.ReportData

class SelectRoomItemActivity : BaseActivity() {

    private lateinit var binding: ActivitySelectRoomItemBinding
    private lateinit var adapter: SelectRoomItemAdapter

    private var reportId = ""
    private var roomId = ""

    private val checkedItems = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySelectRoomItemBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        binding.tvTitle.text = "Add Room Item"
        binding.tvCustomLabel.text = "Custom Item"
        reportId = intent.getStringExtra("reportId") ?: ""
        roomId = intent.getStringExtra("roomId") ?: ""

        adapter = SelectRoomItemAdapter(PREDEFINED_ITEMS.toMutableList(), checkedItems)
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
                showToast("Please select at least one item")
                return@setOnClickListener
            }
            addRoomItemsApi()
        }
    }

    private fun addRoomItemsApi() {
        val request = AddNewRoomItemsRequest(room_items = checkedItems.toList())
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { api -> api.addRoomItemsToReport(reportId, roomId, request) },
            listener = object : ApiResponseListener<ApiResponse<ArrayList<ReportData>>> {
                override fun onSuccess(response: ApiResponse<ArrayList<ReportData>>) {
                    if (response.success) {
                        showToast("Items added successfully")
                        finish()
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    Log.e("API", errorMessage?.error?.message ?: "")
                    showToast(errorMessage?.error?.message ?: "Failed to add items")
                }

                override fun onError(throwable: Throwable) {
                    Log.e("API", "Error: ${throwable.message}")
                    showToast("Error: ${throwable.message}")
                }
            }
        )
    }

    companion object {
        val PREDEFINED_ITEMS = listOf(
            "Doors",
            "Front Door",
            "Back Door",
            "Patio Doors",
            "Door Lock",
            "Door Handle",
            "Door Hinges",
            "Letter Box",
            "Peephole/Viewer",
            "Walls",
            "Flooring",
            "Carpet",
            "Laminate Flooring",
            "Vinyl Flooring",
            "Tile Flooring",
            "Wooden Flooring",
            "Ceiling",
            "Coving/Cornice",
            "Skirting/Architrave",
            "Windows",
            "Window Frames",
            "Window Sills",
            "Window Locks",
            "Staircase",
            "Bannister/Handrail",
            "Balustrade",
            "Lighting",
            "Light Fixtures",
            "Ceiling Light",
            "Pendant Light",
            "Wall Lights",
            "Spotlights",
            "Light Switches",
            "Switches/Sockets",
            "Power Sockets",
            "Double Sockets",
            "USB Sockets",
            "Fuse Box/Consumer Unit",
            "Smoke Alarm",
            "Carbon Monoxide Detector",
            "Burglar Alarm",
            "Doorbell",
            "Door Entry System",
            "TV Aerial Point",
            "Satellite Dish",
            "Telephone Socket",
            "Radiator/Heating",
            "Radiator",
            "Radiator Valve",
            "Thermostatic Valve",
            "Towel Rail",
            "Radiator/Towel Rail",
            "Underfloor Heating",
            "Storage Heater",
            "Boiler",
            "Gas Boiler",
            "Electric Boiler",
            "Combi Boiler",
            "Immersion Heater",
            "Hot Water Tank",
            "Thermostat",
            "Smart Thermostat",
            "Central Heating Timer",
            "Curtains/Blinds",
            "Curtains",
            "Curtain Rail",
            "Curtain Pole",
            "Curtain Rings",
            "Blinds",
            "Roller Blinds",
            "Venetian Blinds",
            "Vertical Blinds",
            "Roman Blinds",
            "Blackout Blinds",
            "Shutters",
            "Net Curtains",
            "Kitchen Units",
            "Base Units",
            "Wall Units",
            "Corner Units",
            "Tall Units",
            "Worktop",
            "Breakfast Bar",
            "Kitchen Island",
            "Drawer Units",
            "Cupboard Doors",
            "Cabinet Handles",
            "Cabinet Hinges",
            "Soft Close Mechanisms",
            "Larder Unit",
            "Pan Drawers",
            "Oven/Hob/Extractor",
            "Oven",
            "Built-in Oven",
            "Free-standing Oven",
            "Electric Oven",
            "Gas Oven",
            "Dual Fuel Oven",
            "Hob",
            "Gas Hob",
            "Electric Hob",
            "Induction Hob",
            "Ceramic Hob",
            "Extractor Fan",
            "Extractor Hood",
            "Cooker Hood",
            "Fridge/Freezer",
            "Fridge",
            "Freezer",
            "Fridge-Freezer",
            "American Style Fridge",
            "Integrated Fridge",
            "Under-counter Fridge",
            "Wine Cooler",
            "Washing Machine",
            "Integrated Washing Machine",
            "Free-standing Washing Machine",
            "Washer-Dryer",
            "Tumble Dryer",
            "Condenser Dryer",
            "Vented Dryer",
            "Heat Pump Dryer",
            "Dishwasher",
            "Integrated Dishwasher",
            "Slimline Dishwasher",
            "Microwave",
            "Built-in Microwave",
            "Combination Microwave",
            "Kettle",
            "Toaster",
            "Coffee Machine",
            "Sink/Taps",
            "Kitchen Sink",
            "Single Bowl Sink",
            "Double Bowl Sink",
            "Belfast Sink",
            "Undermount Sink",
            "Taps",
            "Mixer Tap",
            "Hot Tap",
            "Cold Tap",
            "Pull-out Tap",
            "Boiling Water Tap",
            "Waste Disposal Unit",
            "Sink Waste/Plug",
            "Draining Board",
            "Splashback",
            "Tiled Splashback",
            "Glass Splashback",
            "Upstand",
            "Bath/Shower",
            "Bath",
            "Roll-top Bath",
            "Corner Bath",
            "Jacuzzi Bath",
            "Bath Panel",
            "Bath Taps",
            "Shower",
            "Shower Cubicle",
            "Walk-in Shower",
            "Electric Shower",
            "Mixer Shower",
            "Power Shower",
            "Thermostatic Shower",
            "Shower Head",
            "Shower Hose",
            "Rainfall Shower Head",
            "Handheld Shower",
            "Shower Screen/Curtain",
            "Shower Screen",
            "Glass Shower Screen",
            "Shower Door",
            "Sliding Shower Door",
            "Shower Curtain",
            "Shower Rail",
            "Shower Tray",
            "Toilet",
            "WC",
            "Close-coupled Toilet",
            "Wall-hung Toilet",
            "Toilet Seat",
            "Toilet Cistern",
            "Dual Flush",
            "Toilet Roll Holder",
            "Sink/Taps",
            "Basin",
            "Washbasin",
            "Pedestal Basin",
            "Wall-hung Basin",
            "Vanity Unit",
            "Basin Taps",
            "Basin Waste/Plug",
            "Bidet",
            "Mirror/Cabinet",
            "Mirror",
            "Bathroom Mirror",
            "Illuminated Mirror",
            "Mirror Cabinet",
            "Medicine Cabinet",
            "Shaving Mirror",
            "Towel Rail",
            "Heated Towel Rail",
            "Towel Ring",
            "Towel Rack",
            "Toilet Brush Holder",
            "Soap Dish",
            "Soap Dispenser",
            "Toothbrush Holder",
            "Bathroom Shelf",
            "Bathroom Storage",
            "Extractor Fan",
            "Bathroom Fan",
            "Ceiling Extractor",
            "Wall Tiles",
            "Floor Tiles",
            "Grouting",
            "Sealant/Silicone",
            "Bath Sealant",
            "Shower Sealant",
            "Wardrobe/Storage",
            "Wardrobe",
            "Built-in Wardrobe",
            "Fitted Wardrobe",
            "Free-standing Wardrobe",
            "Sliding Wardrobe Doors",
            "Wardrobe Shelving",
            "Hanging Rail",
            "Clothes Rail",
            "Storage Cupboard",
            "Airing Cupboard",
            "Linen Cupboard",
            "Under-stairs Cupboard",
            "Hallway Cupboard",
            "Coat Cupboard",
            "Storage Units",
            "Shelving",
            "Fitted Shelving",
            "Floating Shelves",
            "Alcove Shelving",
            "Pantry",
            "Walk-in Wardrobe",
            "Fireplace",
            "Electric Fireplace",
            "Gas Fireplace",
            "Fire Surround",
            "Mantelpiece",
            "Hearth",
            "Desk",
            "Built-in Desk",
            "Bed",
            "Fixed Bed Frame",
            "Dining Table/Chairs",
            "Fixed Dining Table",
            "Kitchen Table",
            "Breakfast Bar Stools",
            "Sofa/Seating",
            "Fixed Seating",
            "Window Seat",
            "Coffee Table",
            "Side Table",
            "TV Unit",
            "TV",
            "Wall-mounted TV",
            "Desk/Chair",
            "Office Chair",
            "General Inspection",
            "Garden",
            "Lawn",
            "Grass Area",
            "Artificial Grass",
            "Flower Beds",
            "Borders",
            "Shrubs",
            "Trees",
            "Hedge",
            "Patio/Decking",
            "Patio",
            "Patio Slabs",
            "Decking",
            "Wooden Decking",
            "Composite Decking",
            "Fencing",
            "Panel Fence",
            "Picket Fence",
            "Brick Wall",
            "Boundary Wall",
            "Gate",
            "Side Gate",
            "Garden Gate",
            "Paths",
            "Garden Path",
            "Block Paving",
            "Gravel Path",
            "Shed",
            "Garden Shed",
            "Storage Shed",
            "Bike Shed",
            "Log Store",
            "Greenhouse",
            "Summerhouse",
            "Bin Storage",
            "Wheelie Bin Area",
            "Driveway",
            "Parking Space",
            "Garage",
            "Garden Furniture",
            "Washing Line",
            "Rotary Dryer",
            "Garden Lighting",
            "Outside Tap",
            "Water Butt",
            "Barbecue Area",
            "Hot Tub",
            "Electricity Meter",
            "Gas Meter",
            "Water Meter",
            "Smart Meter",
            "Prepayment Meter",
            "Meter Reading",
            "Front Door Keys",
            "Back Door Keys",
            "Window Keys",
            "Garage Keys",
            "Shed Keys",
            "Gate Keys",
            "Fob",
            "Entry Fob",
            "Communal Door Fob",
            "Key Fob",
            "Parking Permit",
            "Parking Space Number",
            "Communal Entrance",
            "Communal Hallway",
            "Communal Stairwell",
            "Lift",
            "Communal Garden",
            "Communal Bin Store",
            "Communal Bike Store",
            "Residents Lounge",
            "Laundry Room",
            "Communal Kitchen",
            "Communal Living Room",
            "Mail Room",
            "Parcel Room",
            "Concierge Desk",
            "Fire Extinguisher",
            "Fire Blanket",
            "Smoke Alarm",
            "Carbon Monoxide Detector",
            "Heat Detector",
            "Fire Door",
            "Fire Exit",
            "Emergency Lighting",
            "Window Locks",
            "Door Lock",
            "Door Chain",
            "Door Bolt",
            "Security Lock",
            "Mortice Lock",
            "Yale Lock",
            "Multi-point Lock",
            "CCTV Camera",
            "Security System",
            "Panic Alarm",
            "Extractor Fan",
            "Bathroom Extractor",
            "Kitchen Extractor",
            "Mechanical Ventilation",
            "Trickle Vents",
            "Air Vents",
            "Ceiling Vent",
            "Wall Vent",
            "Carpet",
            "Fitted Carpet",
            "Carpet Underlay",
            "Carpet Gripper",
            "Laminate Flooring",
            "Engineered Wood",
            "Solid Wood Flooring",
            "Parquet Flooring",
            "Vinyl Flooring",
            "Luxury Vinyl Tile (LVT)",
            "Vinyl Sheet",
            "Tile Flooring",
            "Ceramic Tiles",
            "Porcelain Tiles",
            "Stone Tiles",
            "Marble Tiles",
            "Slate Tiles",
            "Concrete Floor",
            "Polished Concrete",
            "Cork Flooring",
            "Rubber Flooring",
            "Threshold Strip",
            "Door Bar",
            "Floor Beading",
            "Loft",
            "Loft Hatch",
            "Loft Ladder",
            "Loft Insulation",
            "Loft Boarding",
            "Picture Rails",
            "Dado Rail",
            "Skirting Board",
            "Architrave",
            "Plinth",
            "Air Conditioning Unit",
            "Ceiling Fan",
            "Ceiling Rose",
            "Chandelier",
            "Intercom System",
            "Door Entry Phone",
            "Broadband Socket",
            "Ethernet Socket",
            "TV Point",
            "Aerial Socket",
            "Satellite Point",
            "Curtain Pelmet",
            "Curtain Tie-backs",
            "Cushions",
            "Throws",
            "Rugs",
            "Welcome Mat",
            "Doormat",
            "Shoe Rack",
            "Coat Hooks",
            "Hat Stand",
            "Umbrella Stand",
            "Radiator Cover",
            "Pipe Boxing",
            "Meter Cupboard",
            "Fuse Box Cover"
        )
    }
}
