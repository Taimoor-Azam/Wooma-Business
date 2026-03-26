package com.wooma.business.activities.report

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.wooma.business.R
import com.wooma.business.activities.BaseActivity
import com.wooma.business.adapter.ItemCondtionAdapter
import com.wooma.business.customs.AttachmentUploadHelper
import com.wooma.business.customs.Utils
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.databinding.ActivityInventoryRoomItemBinding
import com.wooma.business.databinding.AddImageLayoutBinding
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.ConditionDAO
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.ReportData
import com.wooma.business.model.RoomItem
import com.wooma.business.model.UpdateRoomItemRequest
import java.util.Locale

class InventoryRoomItemActivity : BaseActivity() {
    private lateinit var binding: ActivityInventoryRoomItemBinding
    private lateinit var cameraBinding: AddImageLayoutBinding
    var selectedCondition = ""
    var selectedCleanliness = ""
    var roomItems: RoomItem? = null
    var reportId = ""
    var roomId = ""

    private val capturedUris = mutableListOf<Uri>()
    private val CAMERA_REQUEST = 1001

    val conditionItems = mutableListOf(
        ConditionDAO(R.drawable.svg_excellent, "Excellent"),
        ConditionDAO(R.drawable.svg_excellent, "Good"),
        ConditionDAO(R.drawable.svg_poor, "Poor"),
        ConditionDAO(R.drawable.svg_poor, "Unacceptable")
//        ConditionDAO(R.drawable.svg_poor, "N/A")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityInventoryRoomItemBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cameraBinding = binding.cameraLayout

        applyWindowInsetsToBinding(binding.root)

        cameraBinding.ivAddImage.setOnClickListener {
            CameraActivity.pendingUris.clear()
            startActivityForResult(Intent(this, CameraActivity::class.java), CAMERA_REQUEST)
        }

        roomItems = intent.getParcelableExtra("roomItem")
        reportId = intent.getStringExtra("reportId") ?: ""
        roomId = intent.getStringExtra("roomId") ?: ""

        if (roomItems != null) {
            binding.tvTitle.text = roomItems?.name ?: ""

            selectedCondition = roomItems?.general_condition ?: ""
            selectedCleanliness = roomItems?.general_cleanliness ?: ""

            binding.etDescription.setText(roomItems?.description ?: "")
            binding.etNote.setText(roomItems?.note ?: "")

            binding.rvCondition.adapter =
                ItemCondtionAdapter(this, conditionItems, selectedCondition) {
                    selectedCondition = it?.name ?: ""
                }
            binding.tvCleanliness.adapter =
                ItemCondtionAdapter(this, conditionItems, selectedCleanliness) {
                    selectedCleanliness = it?.name ?: ""
                }
        }

        binding.btnSave.setOnClickListener {
            val roomItem = UpdateRoomItemRequest(
                selectedCondition.lowercase(Locale.ROOT),
                selectedCleanliness.lowercase(Locale.ROOT),
                binding.etDescription.text.toString(),
                binding.etNote.text.toString()
            )
            updateRoomItemApi(roomItem)
        }

        binding.btnDelete.setOnClickListener {
            Utils.showDialogBox(
                this@InventoryRoomItemActivity,
                "Delete Room Item",
                "Are you sure you want to delete '${roomItems?.name}' ?"
            ) {
                deleteRoomItemApi()
            }
        }

        binding.ivBack.setOnClickListener { finish() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK) {
            capturedUris.addAll(CameraActivity.pendingUris)
        }
    }

    private fun deleteRoomItemApi() {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService ->
                apiService.deleteRoomItem(
                    reportId,
                    roomId,
                    roomItems?.id ?: ""
                )
            },
            listener = object : ApiResponseListener<ApiResponse<ReportData>> {
                override fun onSuccess(response: ApiResponse<ReportData>) {
                    if (response.success) {
                        finish()
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {}
                override fun onError(throwable: Throwable) {}
            }
        )
    }

    private fun updateRoomItemApi(item: UpdateRoomItemRequest) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService ->
                apiService.updateRoomItem(
                    reportId,
                    roomId,
                    roomItems?.id ?: "",
                    item
                )
            },
            listener = object : ApiResponseListener<ApiResponse<ReportData>> {
                override fun onSuccess(response: ApiResponse<ReportData>) {
                    if (response.success) {
                        uploadPhotosIfNeeded()
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {}
                override fun onError(throwable: Throwable) {}
            }
        )
    }

    private fun uploadPhotosIfNeeded() {
        val itemId = roomItems?.id ?: return finish()
        if (capturedUris.isEmpty()) {
            finish()
            return
        }
        AttachmentUploadHelper.uploadImages(
            activity = this,
            imageUris = capturedUris,
            entityId = itemId,
            entityType = "ROOM_ITEM",
            onComplete = { finish() },
            onError = { finish() }
        )
    }
}
