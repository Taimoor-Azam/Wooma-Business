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

    val locationSuggestionList = mutableListOf(
        "Living Room", "Dining Room", "Kitchen", "Hallway", "Landing", "Entrance Hall",
        "Utility Room", "Conservatory", "Study", "Office", "Garage", "Basement", "Attic", "Loft",
        "Master Bedroom", "Bedroom 1", "Bedroom 2", "Bedroom 3", "Bedroom 4", "Guest Bedroom",
        "Bathroom", "Ensuite", "WC", "Downstairs WC", "Family Bathroom", "Main Bathroom",
        "Front Garden", "Back Garden", "Shed", "Outbuilding", "Summer House"
    )

    val testResultSuggestionList = mutableListOf(
        "Detector tested. Audible alarm noted. Operating correctly.",
        "Detector tested. Unit failed to activate. Requires replacement.",
        "Detector present. Testing not completed at time of inspection.",
        "No detector fitted at this location.",
        "Detector tested. Audible alarm noted. Low battery warning detected.",
        "Detector present but inaccessible for testing."
    )

    private lateinit var suggestionsAdapter: SuggestionsAdapter
    private lateinit var locationSuggestionsAdapter: SuggestionsAdapter
    private lateinit var testResultSuggestionsAdapter: SuggestionsAdapter

    var isEdit = false
    private var hasChanges = false

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

        testResultSuggestionsAdapter = SuggestionsAdapter(
            this,
            testResultSuggestionList,
            object : SuggestionsAdapter.OnItemClickInterface {
                override fun onItemClick(item: String) {
                    binding.etTestResult.setText(item)
                    binding.etTestResult.text?.let { binding.etTestResult.setSelection(it.length) }
                    testResultSuggestionsAdapter.filter("")
                }
            })
        binding.rvTestResultSuggestions.adapter = testResultSuggestionsAdapter

        binding.etTestResult.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                testResultSuggestionsAdapter.filter(s?.toString() ?: "")
            }
        })

        binding.ivBack.setOnClickListener {
            if (hasChanges) showUnsavedChangesDialog { finish() } else finish()
        }

        setMeterData()
        attachChangeWatchers()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (hasChanges) showUnsavedChangesDialog { super.onBackPressed() } else super.onBackPressed()
    }

    private fun attachChangeWatchers() {
        val w = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { hasChanges = true }
        }
        binding.etType.addTextChangedListener(w)
        binding.etLocation.addTextChangedListener(w)
        binding.etTestResult.addTextChangedListener(w)
    }

    private fun setupCapturedImagesRecycler() {
        cameraBinding.rvRoomItems.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        cameraBinding.rvRoomItems.adapter = ImageAdapter(allImages, title = detectorItem?.name ?: "", onDelete = {
            capturedUris.clear()
            capturedUris.addAll(allImages.filterIsInstance<ImageItem.Local>().map { it.uri })
            hasChanges = true
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK) {
            val newUris = CameraActivity.pendingUris.toList()
            allImages.addAll(newUris.map { ImageItem.Local(it) })
            cameraBinding.rvRoomItems.adapter?.notifyDataSetChanged()
            if (newUris.isNotEmpty()) hasChanges = true
            if (savedDetectorId.isNotEmpty() && newUris.isNotEmpty()) {
                val progress = ProgressDialog(this).apply {
                    setMessage("Uploading images...")
                    setCancelable(false)
                    show()
                }
                AttachmentUploadHelper.uploadImages(
                    activity = this,
                    imageUris = newUris,
                    entityId = savedDetectorId,
                    entityType = "DETECTOR",
                    onComplete = { progress.dismiss() },
                    onError = { progress.dismiss() }
                )
            } else {
                capturedUris.addAll(newUris)
            }
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
                        if (detectorItem != null) {
                            uploadPhotosIfNeeded(savedDetectorId)
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
            imageUris = capturedUris.filterIsInstance<Uri>(),
            entityId = entityId,
            entityType = "DETECTOR",
            onComplete = { finish() },
            onError = { finish() }
        )
    }

    private fun uploadPhotosAfterCreate(requestBody: AddDetectorRequest) {
        if (capturedUris.isEmpty()) {
            finish()
            return
        }

        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = false,
            requestAction = { api -> api.getReportDetector(reportId, include_attachments = false) },
            listener = object : ApiResponseListener<ApiResponse<ArrayList<DetectorItem>>> {
                override fun onSuccess(response: ApiResponse<ArrayList<DetectorItem>>) {
                    val createdDetectorId = response.data
                        .asReversed()
                        .firstOrNull { detector ->
                            detector.name == requestBody.name &&
                                    (detector.location ?: "") == requestBody.location &&
                                    (detector.note ?: "") == requestBody.note
                        }?.id.orEmpty()

                    if (createdDetectorId.isEmpty()) {
                        showToast("Detector saved, but photos could not be attached")
                        finish()
                        return
                    }

                    savedDetectorId = createdDetectorId
                    uploadPhotosIfNeeded(savedDetectorId)
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    showToast("Detector saved, but photos could not be attached")
                    finish()
                }

                override fun onError(throwable: Throwable) {
                    showToast("Detector saved, but photos could not be attached")
                    finish()
                }
            }
        )
    }
}
