package com.wooma.business.activities.report

import android.app.Activity
import com.wooma.business.data.network.ApiClient
import com.wooma.business.model.ImageItem
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.wooma.business.R
import com.wooma.business.activities.BaseActivity
import com.wooma.business.adapter.ConditionChipAdapter
import com.wooma.business.adapter.ImageAdapter
import com.wooma.business.adapter.ItemConditionAdapter
import com.wooma.business.customs.AttachmentUploadHelper
import com.wooma.business.customs.Utils
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.data.network.showToast
import com.wooma.business.databinding.ActivityInventoryRoomItemBinding
import com.wooma.business.databinding.AddImageLayoutBinding
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.ConditionDAO
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.PropertyReportType
import com.wooma.business.model.ReportData
import com.wooma.business.model.RoomItem
import com.wooma.business.model.UpdateRoomItemRequest
import com.wooma.business.model.UpsertRoomInspectionRequest
import com.wooma.business.model.enums.ReportTypes
import java.util.Locale

class InventoryRoomItemActivity : BaseActivity() {
    private lateinit var binding: ActivityInventoryRoomItemBinding
    private lateinit var cameraBinding: AddImageLayoutBinding
    var selectedCondition = ""
    var selectedCleanliness = ""
    var roomItems: RoomItem? = null
    var reportId = ""
    var roomId = ""

    var reportType: PropertyReportType? = null
    var isInspection = false
    var isIssue: Boolean = false
    var selectedPriority: String? = null
    private val conditionChips = listOf("Marked", "Scuffed", "Stained", "Loose fitting", "Cracked", "Damp", "Mould", "Faded")
    private val selectedChips = mutableSetOf<String>()

    private val capturedUris = mutableListOf<Uri>()
    private val allImages = mutableListOf<ImageItem>()
    private val CAMERA_REQUEST = 1001
    private val S3_BASE_URL = ApiClient.IMAGE_BASE_URL

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
        setupCapturedImagesRecycler()

        cameraBinding.ivAddImage.setOnClickListener {
            CameraActivity.pendingUris.clear()
            startActivityForResult(Intent(this, CameraActivity::class.java), CAMERA_REQUEST)
        }

        roomItems = intent.getParcelableExtra("roomItem")
        reportId = intent.getStringExtra("reportId") ?: ""
        roomId = intent.getStringExtra("roomId") ?: ""

        reportType = intent.getParcelableExtra("reportType")
        isInspection = reportType?.type_code?.lowercase() == ReportTypes.INSPECTION.value

        if (roomItems != null) {
            binding.tvTitle.text = roomItems?.name ?: ""
            binding.etItemName.setText(roomItems?.name ?: "")

            binding.etItemName.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    binding.tvTitle.text = s?.toString() ?: ""
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })

            selectedCondition = roomItems?.general_condition ?: ""
            selectedCleanliness = roomItems?.general_cleanliness ?: ""

            binding.etDescription.setText(roomItems?.description ?: "")
            binding.etNote.setText(roomItems?.note ?: "")

            // Load existing images from API
            roomItems?.attachments?.forEach { attachment ->
                val id = attachment.id ?: return@forEach
                val key = attachment.storageKey ?: return@forEach
                allImages.add(ImageItem.Remote(id, "$S3_BASE_URL$key"))
            }
            cameraBinding.rvRoomItems.adapter?.notifyDataSetChanged()

            binding.rvCondition.adapter =
                ItemConditionAdapter(this, conditionItems, selectedCondition) {
                    selectedCondition = it?.name ?: ""
                }
            binding.tvCleanliness.adapter =
                ItemConditionAdapter(this, conditionItems, selectedCleanliness) {
                    selectedCleanliness = it?.name ?: ""
                }
        }

        if (isInspection) {
            binding.regularLayout.visibility = android.view.View.GONE
            binding.inspectionLayout.visibility = android.view.View.VISIBLE
            binding.btnSave.text = "Done"

            setupConditionChipsRecycler()

            binding.btnAllOk.setOnClickListener {
                isIssue = false
                binding.issuesExpandedLayout.visibility = android.view.View.GONE
                updateToggleButtonStates()
            }

            binding.btnIssuesFound.setOnClickListener {
                isIssue = true
                binding.issuesExpandedLayout.visibility = android.view.View.VISIBLE
                updateToggleButtonStates()
            }

            binding.btnObservation.setOnClickListener {
                selectedPriority = if (selectedPriority == "observation") null else "observation"
                updatePriorityButtonStates()
            }

            binding.btnActionRequired.setOnClickListener {
                selectedPriority = if (selectedPriority == "action required") null else "action required"
                updatePriorityButtonStates()
            }

            binding.btnUrgent.setOnClickListener {
                selectedPriority = if (selectedPriority == "urgent") null else "urgent"
                updatePriorityButtonStates()
            }
        }

        binding.btnSave.setOnClickListener {
            if (isInspection) {
                upsertRoomInspectionApi()
            } else {
                val roomItem = UpdateRoomItemRequest(
                    name = binding.etItemName.text.toString().trim().ifEmpty { null },
                    general_condition = selectedCondition.lowercase(Locale.ROOT),
                    general_cleanliness = selectedCleanliness.lowercase(Locale.ROOT),
                    description = binding.etDescription.text.toString(),
                    note = binding.etNote.text.toString()
                )
                updateRoomItemApi(roomItem)
            }
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

    private fun setupCapturedImagesRecycler() {
        cameraBinding.rvRoomItems.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        cameraBinding.rvRoomItems.adapter = ImageAdapter(allImages)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK) {
            val newUris = CameraActivity.pendingUris.toList()
            capturedUris.addAll(newUris)
            allImages.addAll(newUris.map { ImageItem.Local(it) })
            cameraBinding.rvRoomItems.adapter?.notifyDataSetChanged()
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

    private fun setupConditionChipsRecycler() {
        binding.rvConditionChips.layoutManager =
            androidx.recyclerview.widget.LinearLayoutManager(this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
        binding.rvConditionChips.adapter = ConditionChipAdapter(conditionChips, selectedChips) { chip, isSelected ->
            if (isSelected) selectedChips.add(chip) else selectedChips.remove(chip)
            val joined = selectedChips.joinToString("; ")
            binding.etIssueNote.setText(joined)
            binding.etIssueNote.setSelection(binding.etIssueNote.text.length)
        }
    }

    private fun updateToggleButtonStates() {
        val selectedBg = ContextCompat.getDrawable(this, R.drawable.bg_button_green)
        val defaultBg = ContextCompat.getDrawable(this, R.drawable.bg_edittext)
        val whiteColor = ContextCompat.getColor(this, R.color.white)
        val blackColor = ContextCompat.getColor(this, R.color.black)

        binding.btnAllOk.background = if (!isIssue) selectedBg else defaultBg
        binding.btnAllOk.setTextColor(if (!isIssue) whiteColor else blackColor)
        binding.btnIssuesFound.background = if (isIssue) selectedBg else defaultBg
        binding.btnIssuesFound.setTextColor(if (isIssue) whiteColor else blackColor)
    }

    private fun updatePriorityButtonStates() {
        val selectedBg = ContextCompat.getDrawable(this, R.drawable.bg_button_green)
        val defaultBg = ContextCompat.getDrawable(this, R.drawable.bg_edittext)
        val whiteColor = ContextCompat.getColor(this, R.color.white)
        val blackColor = ContextCompat.getColor(this, R.color.black)

        binding.btnObservation.background = if (selectedPriority == "observation") selectedBg else defaultBg
        binding.btnObservation.setTextColor(if (selectedPriority == "observation") whiteColor else blackColor)
        binding.btnActionRequired.background = if (selectedPriority == "action required") selectedBg else defaultBg
        binding.btnActionRequired.setTextColor(if (selectedPriority == "action required") whiteColor else blackColor)
        binding.btnUrgent.background = if (selectedPriority == "urgent") selectedBg else defaultBg
        binding.btnUrgent.setTextColor(if (selectedPriority == "urgent") whiteColor else blackColor)
    }

    private fun upsertRoomInspectionApi() {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { api ->
                api.upsertRoomInspection(
                    UpsertRoomInspectionRequest(
                        room_id = roomItems?.room_id ?: roomId,
                        is_issue = isIssue,
                        note = if (isIssue) binding.etIssueNote.text.toString().ifEmpty { null } else null,
                        priority = if (isIssue) selectedPriority else null
                    )
                )
            },
            listener = object : ApiResponseListener<ApiResponse<Any>> {
                override fun onSuccess(response: ApiResponse<Any>) {
                    uploadPhotosIfNeeded()
                }
                override fun onFailure(errorMessage: ErrorResponse?) {
                    showToast(errorMessage?.error?.message ?: "Failed to save inspection")
                }
                override fun onError(throwable: Throwable) {
                    showToast("Error: ${throwable.message}")
                }
            }
        )
    }
}
