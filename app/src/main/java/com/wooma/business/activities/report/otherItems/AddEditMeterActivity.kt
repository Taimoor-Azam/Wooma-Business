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
import com.wooma.business.databinding.ActivityAddEditMeterBinding
import com.wooma.business.databinding.AddImageLayoutBinding
import com.wooma.business.model.AddMeterRequest
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.Meter
import com.wooma.business.model.ReportData

class AddEditMeterActivity : BaseActivity() {
    private lateinit var binding: ActivityAddEditMeterBinding
    private lateinit var cameraBinding: AddImageLayoutBinding
    var meterItem: Meter? = null
    var savedMeterId = ""

    private val capturedUris = mutableListOf<Uri>()
    private val allImages = mutableListOf<ImageItem>()
    private val CAMERA_REQUEST = 1001
    private val S3_BASE_URL = ApiClient.IMAGE_BASE_URL

    var reportId = ""
    val suggestionList =
        mutableListOf("Gas", "Electric", "Water", "Heating Oil", "Liquid Petroleum Gas (LPG)")

    var isEdit = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAddEditMeterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cameraBinding = binding.cameraLayout
        applyWindowInsetsToBinding(binding.root)
        setupCapturedImagesRecycler()
        meterItem = intent.getParcelableExtra("meterItem")
        savedMeterId = meterItem?.id ?: ""

        reportId = intent.getStringExtra("reportId") ?: ""
        isEdit = intent.getBooleanExtra("isEdit", false)

        cameraBinding.ivAddImage.setOnClickListener {
            CameraActivity.pendingUris.clear()
            startActivityForResult(Intent(this, CameraActivity::class.java), CAMERA_REQUEST)
        }

        binding.btnSave.setOnClickListener {
            if (isValid()) {
                addNewMeterApi()
            }
        }

        binding.ivDelete.visibility = if (meterItem != null) {
            View.VISIBLE
        } else {
            View.GONE
        }

        binding.ivDelete.setOnClickListener {
            Utils.showDialogBox(
                this,
                "Delete Meter",
                "Do you want to delete this ? This action can't be undone"
            ) {
                deleteMeterApi(meterItem?.id ?: "")
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

    fun setMeterData() {
        if (meterItem != null) {
            binding.etType.setText(meterItem?.name)
            binding.etReading.setText(meterItem?.reading)
            binding.etSerialNumber.setText(meterItem?.serial_number)
            binding.etLocation.setText(meterItem?.location)

            // Load existing images from API
            meterItem?.attachments?.forEach { attachment ->
                allImages.add(ImageItem.Remote(attachment.id, "$S3_BASE_URL${attachment.storageKey}"))
            }
            cameraBinding.rvRoomItems.adapter?.notifyDataSetChanged()
        }
    }

    private fun isValid(): Boolean {
        if (binding.etType.text.toString().isEmpty()) {
            showToast("Please enter Meter Type")
            return false
        } else if (binding.etReading.text.toString().isEmpty()) {
            showToast("Please enter Meter Reading")
            return false
        } else if (binding.etSerialNumber.text.toString().isEmpty()) {
            showToast("Please enter Serial Number")
            return false
        } else if (binding.etLocation.text.toString().isEmpty()) {
            showToast("Please enter Meter Location")
            return false
        }
        return true
    }

    private fun deleteMeterApi(meterId: String) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.deleteMeter(reportId, meterId) },
            listener = object : ApiResponseListener<ApiResponse<ReportData>> {
                override fun onSuccess(response: ApiResponse<ReportData>) {
                    if (response.success) {
                        showToast("Meter Deleted successfully")
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

    private fun addNewMeterApi() {
        val body = AddMeterRequest(
            binding.etType.text.toString(),
            binding.etReading.text.toString(),
            binding.etLocation.text.toString(),
            binding.etSerialNumber.text.toString()
        )

        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService ->
                if (meterItem != null) apiService.updateMeter(
                    reportId,
                    meterItem?.id ?: "",
                    body
                ) else apiService.addNewMeter(reportId, body)
            },
            listener = object : ApiResponseListener<ApiResponse<ReportData>> {
                override fun onSuccess(response: ApiResponse<ReportData>) {
                    if (response.success) {
                        showToast("Meter Added successfully")
                        uploadPhotosIfNeeded(savedMeterId)
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
            imageUris = capturedUris,
            entityId = entityId,
            entityType = "METER",
            onComplete = { finish() },
            onError = { finish() }
        )
    }
}
