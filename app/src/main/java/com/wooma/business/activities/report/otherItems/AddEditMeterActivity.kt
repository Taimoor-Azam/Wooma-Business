package com.wooma.business.activities.report.otherItems

import android.app.Activity
import android.app.ProgressDialog
import com.wooma.business.data.network.ApiClient
import com.wooma.business.model.ImageItem
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
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

    val locationSuggestionList = mutableListOf(
        "Kitchen", "Under Kitchen Sink", "Utility Room", "Under Stairs",
        "Under Staircase Cupboard", "Garage", "Loft", "Attic", "Basement", "Cellar",
        "Outside Wall", "External Wall Box", "Front of Property", "Rear of Property",
        "Side of Property", "Meter Cupboard", "Hallway", "Entrance Hall", "Porch",
        "Outhouse", "Outbuilding", "Airing Cupboard", "Boiler Cupboard"
    )

    private lateinit var suggestionsAdapter: SuggestionsAdapter
    private lateinit var locationSuggestionsAdapter: SuggestionsAdapter

    var isEdit = false
    private var hasChanges = false
    private var initialType = ""
    private var initialReading = ""
    private var initialSerialNumber = ""
    private var initialLocation = ""

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
            CameraActivity.existingImages = allImages.toList()
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

        suggestionsAdapter = SuggestionsAdapter(
            this,
            suggestionList,
            object : SuggestionsAdapter.OnItemClickInterface {
                override fun onItemClick(item: String) {
                    binding.etType.setText(item)
                    binding.etType.text?.let { binding.etType.setSelection(it.length) }
                    suggestionsAdapter.filter("")
                }
            })
        binding.rvSuggestions.adapter = suggestionsAdapter

        binding.etType.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                suggestionsAdapter.filter(s?.toString() ?: "")
            }
        })

        locationSuggestionsAdapter = SuggestionsAdapter(
            this,
            locationSuggestionList,
            object : SuggestionsAdapter.OnItemClickInterface {
                override fun onItemClick(item: String) {
                    binding.etLocation.setText(item)
                    binding.etLocation.text?.let { binding.etLocation.setSelection(it.length) }
                    locationSuggestionsAdapter.filter("")
                }
            })
        binding.rvLocationSuggestions.adapter = locationSuggestionsAdapter

        binding.etLocation.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                locationSuggestionsAdapter.filter(s?.toString() ?: "")
            }
        })

        binding.ivBack.setOnClickListener {
            handleBackPress()
        }
        setMeterData()
        cacheInitialValues()
        attachChangeWatchers()

        onBackPressedDispatcher.addCallback(this) {
            handleBackPress()
        }

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        }

        onBackPressedDispatcher.addCallback(this, callback)
    }

    private fun attachChangeWatchers() {
        val w = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                hasChanges = true
            }
        }
        binding.etType.addTextChangedListener(w)
        binding.etReading.addTextChangedListener(w)
        binding.etSerialNumber.addTextChangedListener(w)
        binding.etLocation.addTextChangedListener(w)
    }

    private fun setupCapturedImagesRecycler() {
        cameraBinding.rvRoomItems.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        cameraBinding.rvRoomItems.adapter =
            ImageAdapter(allImages, title = meterItem?.name ?: "", onDelete = {
                capturedUris.clear()
                capturedUris.addAll(allImages.filterIsInstance<ImageItem.Local>().map { it.uri })
                hasChanges = true
            })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK) {
            val newUris = CameraActivity.pendingUris.toList()

            allImages.removeAll { it is ImageItem.Local }
            allImages.addAll(newUris.map { ImageItem.Local(it) })
            cameraBinding.rvRoomItems.adapter?.notifyDataSetChanged()

            if (newUris.isNotEmpty()) hasChanges = true
            if (savedMeterId.isNotEmpty() && newUris.isNotEmpty()) {
                val progress = ProgressDialog(this).apply {
                    setMessage("Uploading images...")
                    setCancelable(false)
                    show()
                }
                AttachmentUploadHelper.uploadImages(
                    activity = this,
                    imageUris = newUris,
                    entityId = savedMeterId,
                    entityType = "METER",
                    onComplete = { progress.dismiss() },
                    onError = { progress.dismiss() }
                )
            } else {
                capturedUris.clear()
                capturedUris.addAll(newUris)
            }
        }
    }

    private fun handleBackPress() {
        if (hasUnsavedChanges()) {
            showUnsavedChangesDialog { finish() }
        } else {
            finish()
        }
    }

    private fun cacheInitialValues() {
        initialType = binding.etType.text?.toString().orEmpty()
        initialReading = binding.etReading.text?.toString().orEmpty()
        initialSerialNumber = binding.etSerialNumber.text?.toString().orEmpty()
        initialLocation = binding.etLocation.text?.toString().orEmpty()
        hasChanges = false
    }

    private fun hasUnsavedChanges(): Boolean {
        if (hasChanges) return true

        val textChanged =
            binding.etType.text?.toString().orEmpty() != initialType ||
                    binding.etReading.text?.toString().orEmpty() != initialReading ||
                    binding.etSerialNumber.text?.toString().orEmpty() != initialSerialNumber ||
                    binding.etLocation.text?.toString().orEmpty() != initialLocation

        // For new meters, images are only local until Save is tapped.
        val hasPendingLocalImages = capturedUris.isNotEmpty()

        return textChanged || hasPendingLocalImages
    }

    fun setMeterData() {
        if (meterItem != null) {
            binding.etType.setText(meterItem?.name)
            binding.etReading.setText(meterItem?.reading)
            binding.etSerialNumber.setText(meterItem?.serial_number)
            binding.etLocation.setText(meterItem?.location)

            // Load existing images from API
            meterItem?.attachments?.forEach { attachment ->
                allImages.add(
                    ImageItem.Remote(
                        attachment.id,
                        "$S3_BASE_URL${attachment.storageKey}"
                    )
                )
            }
            cameraBinding.rvRoomItems.adapter?.notifyDataSetChanged()
        }
    }

    private fun isValid(): Boolean {
        if (binding.etType.text.toString().isEmpty()) {
            showToast("Please enter Meter Type")
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
                        hasChanges = false
                        if (meterItem != null) {
                            uploadPhotosIfNeeded(savedMeterId)
                        } else {
                            uploadPhotosAfterCreate(body)
                        }
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

    private fun uploadPhotosAfterCreate(requestBody: AddMeterRequest) {
        if (capturedUris.isEmpty()) {
            finish()
            return
        }

        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = false,
            requestAction = { api -> api.getReportMeters(reportId, include_attachments = false) },
            listener = object : ApiResponseListener<ApiResponse<ArrayList<Meter>>> {
                override fun onSuccess(response: ApiResponse<ArrayList<Meter>>) {
                    val createdMeterId = response.data
                        .asReversed()
                        .firstOrNull { meter ->
                            meter.name == requestBody.name &&
                                    (meter.reading ?: "") == requestBody.reading &&
                                    (meter.location ?: "") == requestBody.location &&
                                    (meter.serial_number ?: "") == requestBody.serial_number
                        }?.id.orEmpty()

                    if (createdMeterId.isEmpty()) {
                        showToast("Meter saved, but photos could not be attached")
                        finish()
                        return
                    }

                    savedMeterId = createdMeterId
                    uploadPhotosIfNeeded(savedMeterId)
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    showToast("Meter saved, but photos could not be attached")
                    finish()
                }

                override fun onError(throwable: Throwable) {
                    showToast("Meter saved, but photos could not be attached")
                    finish()
                }
            }
        )
    }
}
