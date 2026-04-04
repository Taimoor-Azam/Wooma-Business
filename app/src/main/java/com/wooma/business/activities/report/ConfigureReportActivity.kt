package com.wooma.business.activities.report

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.wooma.business.R
import com.wooma.business.activities.BaseActivity
import com.wooma.business.adapter.ReportRoomsAdapter
import com.wooma.business.adapter.TemplateHorizontalAdapter
import com.wooma.business.customs.AddCustomRoomDialog
import com.wooma.business.customs.Utils
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.data.network.showToast
import com.wooma.business.databinding.ActivityConfigureReportBinding
import com.wooma.business.model.AddReportResponse
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.CreateReportFromPreviousRequest
import com.wooma.business.model.CreateReportRequest
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.PropertyDetailResponse
import com.wooma.business.model.Report
import com.wooma.business.model.Room
import com.wooma.business.model.SendRequestRoom
import com.wooma.business.model.Template
import com.wooma.business.model.TemplateData
import java.util.Collections

class ConfigureReportActivity : BaseActivity(), AdapterView.OnItemSelectedListener {
    private lateinit var binding: ActivityConfigureReportBinding

    companion object {
        var reportCreated = false
    }

    private lateinit var adapter: ReportRoomsAdapter
    private var roomsList = mutableListOf<Room>()
    private var templateList = mutableListOf<Template>()

    var propertyId = ""
    var reportTypeId = ""
    var previousReportsList = ArrayList<Report>()
    var previousReportId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityConfigureReportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        propertyId = intent.getStringExtra("propertyId") ?: ""
        reportTypeId = intent.getStringExtra("reportTypeId") ?: ""

        adapter = ReportRoomsAdapter(this, roomsList)
        binding.rvRooms.adapter = adapter

//        loadProperties()
        getReportTemplates()
        getPropertyByIdApi()
        binding.ivBack.setOnClickListener { finish() }

        /*binding.addRoomLayout.setOnClickListener {
            startActivity(
                Intent(this, AddRoomsActivity::class.java)
                    .putParcelableArrayListExtra("roomNamesList", ArrayList(roomsList))
            )
        }*/

        /*  binding.btnCreateReport.setOnClickListener {

          }*/

        binding.ivEdit.setOnClickListener {
            /*               val intent = Intent(this, EditRoomNamesActivity::class.java)
               intent.putParcelableArrayListExtra("roomNamesList", ArrayList(roomsList))
               startActivity(intent)*/
            AddCustomRoomDialog().show(
                supportFragmentManager,
                "InputBottomSheet"
            )
        }

        supportFragmentManager.setFragmentResultListener(
            "sheet_key",
            this
        ) { requestKey, bundle ->

            val value = bundle.getString("added_room")
            roomsList.add(0, Room("", "", value ?: "", "", true, ArrayList()))
            println(value)
            adapter.updateList(roomsList)
        }

        binding.btnCreateReport.setOnClickListener {
            if (binding.switchButton.isChecked) {
                val previousReport =
                    CreateReportFromPreviousRequest(propertyId, reportTypeId, previousReportId)
                addReportServerApi(null, previousReport)
            } else {
                val selectedRooms = adapter.getSelectedRooms()
                val sendServerList = ArrayList<SendRequestRoom>()
                if (selectedRooms.isNotEmpty()) {
                    for (roomsss in selectedRooms) {
                        val selectedRoomss =
                            SendRequestRoom(roomsss.name, roomsss.items.map { it.name })
                        sendServerList.add(selectedRoomss)
                    }
                    val sendRoomToServer =
                        CreateReportRequest(propertyId, reportTypeId, sendServerList)
                    addReportServerApi(sendRoomToServer)
                }
            }
        }
    }

    private fun moveRooms() {
        val callback = DragManageAdapter(this, adapter, roomsList) { from, to ->
//            Toast.makeText(this, "Item moved from $from to $to", Toast.LENGTH_SHORT).show()
            // Optionally save new order to DB / SharedPreferences
        }
        val itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.rvRooms)
    }

    private fun addReportServerApi(
        createObject: CreateReportRequest?,
        previousReport: CreateReportFromPreviousRequest? = null
    ) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService ->
                if (previousReport != null) apiService.createReport(
                    previousReport
                ) else apiService.createReport(createObject)
            },
            listener = object : ApiResponseListener<ApiResponse<AddReportResponse>> {
                override fun onSuccess(response: ApiResponse<AddReportResponse>) {
                    if (response.success) {
                        showToast("Report Created Successfully")
                        reportCreated = true
                        startActivity(
                            Intent(
                                this@ConfigureReportActivity,
                                InventoryListingActivity::class.java
                            ).putExtra("reportId", response.data.report_id)
                                .putExtra("reportStatus", response.data.status)
                        )
                        finish()
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    // Handle API error
                    Log.e("API", errorMessage?.error?.message ?: "")
                    showToast(errorMessage?.error?.message ?: "")
                }

                override fun onError(throwable: Throwable) {
                    // Handle network error
                    Log.e("API", "Error: ${throwable.message}")
                    showToast("Error: ${throwable.message}")
                }
            }
        )
    }

    private fun getReportTemplates() {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.getReportTemplates() },
            listener = object : ApiResponseListener<ApiResponse<TemplateData>> {
                override fun onSuccess(response: ApiResponse<TemplateData>) {
                    if (response.data.templates.isNotEmpty()) {
                        templateList = response.data.templates
                        for (template in templateList) {
                            for (room in template.rooms) {
                                room.isSelected = true
                            }
                        }

                        roomsList = templateList[0].rooms
                        updateRoomsFromTemplate()
                        val templateAdapter = TemplateHorizontalAdapter(
                            this@ConfigureReportActivity,
                            templateList,
                            object : TemplateHorizontalAdapter.OnItemClickInterface {
                                override fun onItemClick(item: ArrayList<Room>) {
                                    roomsList = item
                                    updateRoomsFromTemplate()
                                }
                            })
                        binding.rvTemplate.adapter = templateAdapter
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    // Handle API error
                    Log.e("API", errorMessage?.error?.message ?: "")
                    showToast(errorMessage?.error?.message ?: "")
                }

                override fun onError(throwable: Throwable) {
                    // Handle network error
                    Log.e("API", "Error: ${throwable.message}")
                    showToast("Error: ${throwable.message}")
                }
            }
        )
    }

    private fun getPropertyByIdApi() {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.getPropertyById(propertyId) },
            listener = object : ApiResponseListener<ApiResponse<PropertyDetailResponse>> {
                override fun onSuccess(response: ApiResponse<PropertyDetailResponse>) {
                    if (response.success && response.data.reports.isNotEmpty()) {
                        previousReportsList = ArrayList(response.data.reports.filter {
                            it.report_type?.id == reportTypeId
                        })

                        if (previousReportsList.isNotEmpty()) {
                            binding.configureLayout.visibility = View.VISIBLE
                            setSpinner()
                            binding.switchButton.setOnCheckedChangeListener { button, bool ->
                                if (bool) {
                                    binding.rvTemplate.visibility = View.GONE
                                    binding.roomsRelLayout.visibility = View.GONE
                                    binding.previousReportLayout.visibility = View.VISIBLE
                                } else {
                                    binding.rvTemplate.visibility = View.VISIBLE
                                    binding.roomsRelLayout.visibility = View.VISIBLE
                                    binding.previousReportLayout.visibility = View.GONE
                                }
                            }
                        } else {
                            binding.configureLayout.visibility = View.GONE
                        }
                    } else {
                        binding.configureLayout.visibility = View.GONE
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    // Handle API error
                    Log.e("API", errorMessage?.error?.message ?: "")
                    showToast(errorMessage?.error?.message ?: "")
                }

                override fun onError(throwable: Throwable) {
                    // Handle network error
                    Log.e("API", "Error: ${throwable.message}")
                    showToast("Error: ${throwable.message}")
                }
            }
        )
    }

    private fun setSpinner() {
        val reports =
            previousReportsList.map { "${it.report_type?.display_name} ${Utils.formatDate(it.created_at)}" }

        previousReportId = previousReportsList[0].id
        val mArrayAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, reports) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = super.getView(position, convertView, parent) as TextView
                tv.setTextColor(ContextCompat.getColor(context, R.color.black))
                return tv
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val tv = super.getDropDownView(position, convertView, parent) as TextView
                tv.setBackgroundColor(ContextCompat.getColor(context, R.color.white))
                tv.setTextColor(ContextCompat.getColor(context, R.color.black))
                return tv
            }
        }.apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        with(binding.spinnerTitle)
        {
            adapter = mArrayAdapter
            setSelection(0, false)
            onItemSelectedListener = this@ConfigureReportActivity
            prompt = "Select Previous Report"
            gravity = Gravity.CENTER
        }
    }

    private fun updateRoomsFromTemplate() {
        adapter.updateList(roomsList)
        moveRooms()
    }

    override fun onItemSelected(
        parent: AdapterView<*>?,
        view: View?,
        position: Int,
        id: Long
    ) {
        previousReportId = previousReportsList[position].id
    }

    override fun onNothingSelected(parent: AdapterView<*>?) {
    }

    class DragManageAdapter(
        val context: Context,
        private val adapter: RecyclerView.Adapter<*>,
        private val data: MutableList<Room>,
        private val onItemDropped: ((fromPosition: Int, toPosition: Int) -> Unit)? = null
    ) : ItemTouchHelper.Callback() {

        private var dragStartPosition: Int = -1

        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
            return makeMovementFlags(dragFlags, 0)
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val from = viewHolder.adapterPosition
            val to = target.adapterPosition

            // Track start position only on first move
            if (dragStartPosition == -1) dragStartPosition = from

            Collections.swap(data, from, to)
            adapter.notifyItemMoved(from, to)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

        override fun isLongPressDragEnabled(): Boolean = true

        // ✅ Called when drag ends
        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)

            val finalPosition = viewHolder.adapterPosition

            if (dragStartPosition != -1 && dragStartPosition != finalPosition) {
                onItemDropped?.invoke(dragStartPosition, finalPosition)
            }

            // Reset start position
            dragStartPosition = -1
        }
    }
}