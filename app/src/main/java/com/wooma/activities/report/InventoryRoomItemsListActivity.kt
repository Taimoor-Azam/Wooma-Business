package com.wooma.activities.report

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import com.wooma.activities.BaseActivity
import com.wooma.adapter.InventoryRoomItemsAdapter
import com.wooma.data.network.ApiResponseListener
import com.wooma.data.network.MyApi
import com.wooma.data.network.makeApiRequest
import com.wooma.data.network.showToast
import com.wooma.databinding.ActivityInventoryRoomsListBinding
import com.wooma.model.AddNewRoomItemsRequest
import com.wooma.model.ApiResponse
import com.wooma.model.ErrorResponse
import com.wooma.model.PropertyReportType
import com.wooma.model.ReportData
import com.wooma.model.RoomItem
import com.wooma.model.RoomsResponse
import com.wooma.model.enums.TenantReportStatus

class InventoryRoomItemsListActivity : BaseActivity() {
    private lateinit var binding: ActivityInventoryRoomsListBinding
    private lateinit var adapter: InventoryRoomItemsAdapter

    var reportId = ""
    var roomId = ""
    var reportStatus = ""
    var reportType: PropertyReportType? = null
    var showTimestamp = true
    val roomItems = mutableListOf<RoomItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityInventoryRoomsListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

//        val roomItems = intent.getParcelableArrayListExtra<RoomItem>("roomItems") ?: ArrayList()
        val roomName = intent.getStringExtra("roomName")
        reportStatus = intent.getStringExtra("reportStatus") ?: ""
        reportId = intent.getStringExtra("reportId") ?: ""
        roomId = intent.getStringExtra("roomId") ?: ""
        reportType = intent.getParcelableExtra("reportType")
        showTimestamp = intent.getBooleanExtra("showTimestamp", true)

        if (!roomName.isNullOrEmpty()) {
            binding.tvTitle.text = roomName
        }

        if (reportStatus != TenantReportStatus.IN_PROGRESS.value) {
            binding.btnAddItem.visibility = View.INVISIBLE
        }

        adapter =
            InventoryRoomItemsAdapter(this, roomItems, reportId, roomId, reportStatus, reportType, showTimestamp)
        binding.rvRoomItems.adapter = adapter

        binding.ivBack.setOnClickListener { finish() }
        binding.btnAddItem.setOnClickListener {
            startActivity(
                Intent(this, SelectRoomItemActivity::class.java)
                    .putExtra("reportId", reportId)
                    .putExtra("roomId", roomId)
            )
        }

        supportFragmentManager.setFragmentResultListener(
            "sheet_key",
            this
        ) { requestKey, bundle ->

            val value = bundle.getString("added_room")
            roomItems.add(
                0,
                RoomItem(
                    "",
                    true,
                    false,
                    "",
                    "",
                    roomId,
                    value ?: "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    ArrayList()
                )
            )
            println(value)

            val request = AddNewRoomItemsRequest(
                room_items = listOf(value ?: "")
            )

            addNewRoomApi(request)
            adapter.updateList(roomItems)
        }
    }

    override fun onResume() {
        super.onResume()
        getReportByIdApi()
    }

    private fun addNewRoomApi(request: AddNewRoomItemsRequest) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService ->
                apiService.addRoomItemsToReport(
                    reportId,
                    roomId,
                    request
                )
            },
            listener = object : ApiResponseListener<ApiResponse<ArrayList<ReportData>>> {
                override fun onSuccess(response: ApiResponse<ArrayList<ReportData>>) {
                    if (response.success) {
                    } else {
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    // Handle API error
//                    Log.e("API", errorMessage?.error?.message ?: "")
//                    showToast(errorMessage?.error?.message ?: "")
                }

                override fun onError(throwable: Throwable) {
                    // Handle network error
//                    Log.e("API", "Error: ${throwable.message}")
//                    showToast("Error: ${throwable.message}")
                }
            }
        )
    }

    private fun getReportByIdApi() {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.getRoomById(roomId, reportId, true, true) },
            listener = object : ApiResponseListener<ApiResponse<RoomsResponse>> {
                override fun onSuccess(response: ApiResponse<RoomsResponse>) {
                    if (response.success) {
                        roomItems.clear()
                        roomItems.addAll(response.data.items ?: emptyList())
                        adapter.updateList(roomItems)
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

}