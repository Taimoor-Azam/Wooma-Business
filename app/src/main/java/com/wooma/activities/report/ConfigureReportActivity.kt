package com.wooma.activities.report

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
import com.wooma.R
import com.wooma.activities.BaseActivity
import com.wooma.adapter.ReportRoomsAdapter
import com.wooma.adapter.TemplateHorizontalAdapter
import com.wooma.customs.Utils
import com.wooma.data.network.ApiResponseListener
import com.wooma.data.network.MyApi
import com.wooma.data.network.makeApiRequest
import com.wooma.data.network.showToast
import com.wooma.databinding.ActivityConfigureReportBinding
import com.wooma.model.AddReportResponse
import com.wooma.model.ApiResponse
import com.wooma.model.CreateReportFromPreviousRequest
import com.wooma.model.CreateReportRequest
import com.wooma.model.ErrorResponse
import com.wooma.model.PropertyDetailResponse
import com.wooma.model.Report
import com.wooma.model.Room
import com.wooma.model.SendRequestRoom
import com.wooma.model.Template
import com.wooma.model.TemplateData
import com.wooma.sync.ConnectivityObserver
import java.util.Collections

class ConfigureReportActivity : BaseActivity(), AdapterView.OnItemSelectedListener {
    private lateinit var binding: ActivityConfigureReportBinding

    companion object {
        var reportCreated = false
        var createdPropertyId = ""
        private const val REQUEST_ADD_ROOM = 2001
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
        val isCheckout = intent.getBooleanExtra("isCheckout", false)

        adapter = ReportRoomsAdapter(this, roomsList)
        binding.rvRooms.adapter = adapter

        if (isCheckout) binding.configureLayout.visibility = View.GONE

//        loadProperties()
        getReportTemplates()
        getPropertyByIdApi()
        binding.ivBack.setOnClickListener { finish() }

        binding.ivEdit.setOnClickListener {
            startActivityForResult(
                Intent(this, SelectRoomActivity::class.java),
                REQUEST_ADD_ROOM
            )
        }

        binding.btnCreateReport.setOnClickListener {
            if (!ConnectivityObserver(this).isConnected()) {
                showToast("Please connect to internet to create report")
                return@setOnClickListener
            }
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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ADD_ROOM && resultCode == RESULT_OK) {
            val rooms = data?.getStringArrayListExtra(SelectRoomActivity.RESULT_ROOMS) ?: return
            rooms.forEach { name ->
                roomsList.add(0, Room("", "", name, "", true, ArrayList()))
            }
            adapter.updateList(roomsList)
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
                        createdPropertyId = propertyId
                        startActivity(
                            Intent(this@ConfigureReportActivity, InventoryListingActivity::class.java)
                                .putExtra("reportId", response.data.report_id)
                                .putExtra("reportStatus", response.data.status)
                                .putExtra("propertyId", propertyId)
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

            (adapter as? ReportRoomsAdapter)?.onItemMove(from, to)
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