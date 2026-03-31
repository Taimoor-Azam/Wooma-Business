package com.wooma.business.activities.report.otherItems

import android.app.Activity
import com.wooma.business.data.network.ApiClient
import com.wooma.business.model.ImageItem
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.wooma.business.activities.BaseActivity
import com.wooma.business.activities.report.CameraActivity
import com.wooma.business.adapter.ImageAdapter
import com.wooma.business.adapter.SuggestionsAdapter
import com.wooma.business.customs.AttachmentUploadHelper
import com.wooma.business.customs.Utils
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.data.network.showToast
import com.wooma.business.databinding.ActivityAddEditDetectorBinding
import com.wooma.business.databinding.AddImageLayoutBinding
import com.wooma.business.model.AddDetectorRequest
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.DetectorItem
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.ReportData

class AddEditDetectorActivity : BaseActivity() {
    private lateinit var binding: ActivityAddEditDetectorBinding
    private lateinit var cameraBinding: AddImageLayoutBinding
    var detectorItem: DetectorItem? = null
    var savedDetectorId = ""

    private val capturedUris = mutableListOf<Uri>()
    private val allImages = mutableListOf<ImageItem>()
    private val CAMERA_REQUEST = 1001

    var reportId = ""
    val suggestionList =
        mutableListOf(
            "Smoke Alarm",
            "Carbon Monoxide Detector",
            "Heat Detector",
            "Smoke and Heat Detector",
            "Carbon Monoxide and Smoke Alarm",
            "Fire Alarm",
            "Combined Smoke and CO Detector",
            "Ionization Smoke Alarm",
            "Photoelectric Smoke Alarm",
            "Gas Detector",
            "Radon Detector",
            "Water Leak Detector",
            "Motion Detector",
            "Security Alarm"
        )

    var isEdit = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAddEditDetectorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cameraBinding = binding.cameraLayout
        applyWindowInsetsToBinding(binding.root)
        setupCapturedImagesRecycler()
        detectorItem = intent.getParcelableExtra("detectorItem")
        savedDetectorId = detectorItem?.id ?: ""

        reportId = intent.getStringExtra("reportId") ?: ""
        isEdit = intent.getBooleanExtra("isEdit", false)

        cameraBinding.ivAddImage.setOnClickListener {
            CameraActivity.pendingUris.clear()
            startActivityForResult(Intent(this, CameraActivity::class.java), CAMERA_REQUEST)
        }

        binding.btnSave.setOnClickListener {
            if (isValid()) {
                addNewDetectorApi()
            }
        }

        binding.ivDelete.visibility = if (detectorItem != null) {
            View.VISIBLE
        } else {
            View.GONE
        }

        binding.ivDelete.setOnClickListener {
            Utils.showDialogBox(
                this,
                "Delete Detector",
                "Do you want to delete this ? This action can't be undone"
            ) {
                deleteDetectorApi(detectorItem?.id ?: "")
            }
        }

        binding.rvSuggestions.adapter = SuggestionsAdapter(
            this,
            suggestionList,
            object : SuggestionsAdapter.OnItemClickInterface {
                override fun onItemClick(item: String) {
                    binding.etType.setText(item)
                }
            })

        binding.ivBack.setOnClickListener { finish() }

        setMeterData()
    }

    private fun setupCapturedImagesRecycler() {
        cameraBinding.rvRoomItems.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        cameraBinding.rvRoomItems.adapter = ImageAdapter(allImages, title = detectorItem?.name ?: "", onDelete = {
            capturedUris.clear()
            capturedUris.addAll(allImages.filterIsInstance<ImageItem.Local>().map { it.uri })
        })
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

    private fun setMeterData() {
        if (detectorItem != null) {
            binding.etType.setText(detectorItem?.name)
            binding.etLocation.setText(detectorItem?.location ?: "")
            binding.etTestResult.setText(detectorItem?.note ?: "")

            detectorItem?.attachments?.forEach { attachment ->
                allImages.add(ImageItem.Remote(attachment.id, "${ApiClient.IMAGE_BASE_URL}${attachment.storageKey}"))
            }
            cameraBinding.rvRoomItems.adapter?.notifyDataSetChanged()
        }
    }

    private fun isValid(): Boolean {
        if (binding.etType.text.toString().isEmpty()) {
            showToast("Please enter Detector Type")
            return false
        } else if (binding.etLocation.text.toString().isEmpty()) {
            showToast("Please enter Location")
            return false
        } else if (binding.etTestResult.text.toString().isEmpty()) {
            showToast("Please enter Test Result")
            return false
        }
        return true
    }

    private fun deleteDetectorApi(detectorId: String) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.deleteDetector(reportId, detectorId) },
            listener = object : ApiResponseListener<ApiResponse<ReportData>> {
                override fun onSuccess(response: ApiResponse<ReportData>) {
                    if (response.success) {
                        showToast("Detector Deleted successfully")
                        finish()
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    Log.e("API", errorMessage?.error?.message ?: "")
                    showToast(errorMessage?.error?.message ?: "")
                }

                override fun onError(throwable: Throwable) {
                    Log.e("API", "Error: ${throwable.message}")
                    showToast("Error: ${throwable.message}")
                }
            }
        )
    }

    private fun addNewDetectorApi() {
        val body = AddDetectorRequest(
            binding.etType.text.toString(),
            binding.etLocation.text.toString(),
            binding.etTestResult.text.toString()
        )

        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService ->
                if (detectorItem != null) apiService.updateDetector(
                    reportId,
                    detectorItem?.id ?: "",
                    body
                ) else apiService.addNewDetector(reportId, body)
            },
            listener = object : ApiResponseListener<ApiResponse<ReportData>> {
                override fun onSuccess(response: ApiResponse<ReportData>) {
                    if (response.success) {
                        showToast(
                            if (detectorItem != null) "Detector Updated successfully"
                            else "Detector Added successfully"
                        )
                        uploadPhotosIfNeeded(savedDetectorId)
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    Log.e("API", errorMessage?.error?.message ?: "")
                    showToast(errorMessage?.error?.message ?: "")
                }

                override fun onError(throwable: Throwable) {
                    Log.e("API", "Error: ${throwable.message}")
                    showToast("Error: ${throwable.message}")
                }
            }
        )
    }

    private fun uploadPhotosIfNeeded(entityId: String) {
        if (capturedUris.isEmpty() || entityId.isEmpty()) {
            finish()
            return
        }
        AttachmentUploadHelper.uploadImages(
            activity = this,
            imageUris = capturedUris.filterIsInstance<Uri>(),
            entityId = entityId,
            entityType = "DETECTOR",
            onComplete = { finish() },
            onError = { finish() }
        )
    }
}
