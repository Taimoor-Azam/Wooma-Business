package com.wooma.activities.report

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wooma.activities.BaseActivity
import com.wooma.adapter.InventoryRoomItemsAdapter
import com.wooma.data.network.ApiResponseListener
import com.wooma.data.network.MyApi
import com.wooma.data.network.makeApiRequest
import com.wooma.data.network.showToast
import com.wooma.data.repository.RoomItemRepository
import com.wooma.databinding.ActivityInventoryRoomsListBinding
import com.wooma.model.AddNewRoomItemsRequest
import com.wooma.model.ApiResponse
import com.wooma.model.ErrorResponse
import com.wooma.model.PropertyReportType
import com.wooma.model.ReportData
import com.wooma.model.RoomItem
import com.wooma.model.RoomsResponse
import com.wooma.model.enums.TenantReportStatus
import kotlinx.coroutines.launch

class InventoryRoomItemsListActivity : BaseActivity() {
    private lateinit var binding: ActivityInventoryRoomsListBinding
    private lateinit var adapter: InventoryRoomItemsAdapter

    var reportId = ""
    var roomId = ""
    var reportStatus = ""
    var reportType: PropertyReportType? = null
    var showTimestamp = true
    val roomItems = mutableListOf<RoomItem>()

    private val roomItemRepo by lazy { RoomItemRepository(this) }

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

        supportFragmentManager.setFragmentResultListener("sheet_key", this) { _, bundle ->
            val value = bundle.getString("added_room")
            val request = AddNewRoomItemsRequest(room_items = listOf(value ?: ""))
            addNewRoomApi(request)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                roomItemRepo.observeItems(roomId).collect { items ->
                    roomItems.clear()
                    roomItems.addAll(items)
                    adapter.updateList(roomItems)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            try { roomItemRepo.refreshItems(reportId, roomId) } catch (_: Exception) {}
        }
    }

    private fun addNewRoomApi(request: AddNewRoomItemsRequest) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.addRoomItemsToReport(reportId, roomId, request) },
            listener = object : ApiResponseListener<ApiResponse<ArrayList<ReportData>>> {
                override fun onSuccess(response: ApiResponse<ArrayList<ReportData>>) {
                    // Refresh from local cache so new items appear via the Flow
                    lifecycleScope.launch {
                        try { roomItemRepo.refreshItems(reportId, roomId) } catch (_: Exception) {}
                    }
                }
                override fun onFailure(errorMessage: ErrorResponse?) {
                    Log.e("API", errorMessage?.error?.message ?: "")
                }
                override fun onError(throwable: Throwable) {
                    Log.e("API", "Error: ${throwable.message}")
                }
            }
        )
    }

}