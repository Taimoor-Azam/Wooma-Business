package com.wooma.activities.report.otherItems

import android.app.ProgressDialog
import com.wooma.data.network.ApiClient
import com.wooma.model.ImageItem
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.recyclerview.widget.LinearLayoutManager
import com.wooma.activities.BaseActivity
import com.wooma.activities.report.CameraActivity
import com.wooma.adapter.ImageAdapter
import com.wooma.adapter.SuggestionsAdapter
import com.wooma.customs.AttachmentUploadHelper
import com.wooma.customs.Utils
import com.wooma.data.network.ApiResponseListener
import com.wooma.data.network.MyApi
import com.wooma.data.network.makeApiRequest
import com.wooma.data.network.showToast
import com.wooma.databinding.ActivityAddEditKeysBinding
import com.wooma.databinding.AddImageLayoutBinding
import com.wooma.model.AddKeyRequest
import com.wooma.model.ApiResponse
import com.wooma.model.ErrorResponse
import com.wooma.model.KeyItem
import com.wooma.model.ReportData

class AddEditKeysActivity : BaseActivity() {
    private lateinit var binding: ActivityAddEditKeysBinding
    private lateinit var cameraBinding: AddImageLayoutBinding
    var keyItem: KeyItem? = null
    var savedKeyId = ""
    var count = 1

    private val capturedUris = mutableListOf<Uri>()
    private val allImages = mutableListOf<ImageItem>()
    private val CAMERA_REQUEST = 1001

    var reportId = ""
    val suggestionList =
        mutableListOf(
            "Yale",
            "Mortice",
            "Cylinder",
            "Chubb",
            "Union",
            "Era",
            "Abloy",
            "Mul-T-Lock",
            "UPVC Door Key",
            "Window Key",
            "Deadbolt Key",
            "Padlock Key",
            "Garage Key",
            "Shed Key",
            "Gate Key"
        )

    val noteSuggestionList = mutableListOf(
        "Front Door", "Back Door", "Side Door", "Garage Door", "Shed Door", "Gate",
        "Window", "Garage", "Shed", "Outhouse", "Gate Key", "Main Entrance",
        "Rear Entrance", "Internal Door", "Office Door", "Storage Room",
        "Utility Room", "Boiler Room", "Basement Door", "Attic Door"
    )

    private lateinit var suggestionsAdapter: SuggestionsAdapter
    private lateinit var noteSuggestionsAdapter: SuggestionsAdapter

    var isEdit = false
    private var hasChanges = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAddEditKeysBinding.inflate(layoutInflater)
        setContentView(binding.root)
        cameraBinding = binding.cameraLayout
        applyWindowInsetsToBinding(binding.root)
        setupCapturedImagesRecycler()
        keyItem = intent.getParcelableExtra("keyItem")
        savedKeyId = keyItem?.id ?: ""

        reportId = intent.getStringExtra("reportId") ?: ""
        isEdit = intent.getBooleanExtra("isEdit", false)

        cameraBinding.ivAddImage.setOnClickListener {
            CameraActivity.Companion.existingImages = allImages.toList()
            CameraActivity.Companion.pendingUris.clear()
            startActivityForResult(Intent(this, CameraActivity::class.java), CAMERA_REQUEST)
        }

        binding.btnSave.setOnClickListener {
            if (isValid()) {
                addNewKeyApi()
            }
        }

        binding.ivDelete.visibility = if (keyItem != null) {
            View.VISIBLE
        } else {
            View.GONE
        }

        binding.ivDelete.setOnClickListener {
            Utils.showDialogBox(
                this,
                "Delete Key",
                "Do you want to delete this ? This action can't be undone"
            ) {
                deleteKeyApi(keyItem?.id ?: "")
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

        noteSuggestionsAdapter = SuggestionsAdapter(
            this,
            noteSuggestionList,
            object : SuggestionsAdapter.OnItemClickInterface {
                override fun onItemClick(item: String) {
                    binding.etNote.setText(item)
                    binding.etNote.text?.let { binding.etNote.setSelection(it.length) }
                    noteSuggestionsAdapter.filter("")
                }
            })
        binding.rvNoteSuggestions.adapter = noteSuggestionsAdapter

        binding.etNote.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                noteSuggestionsAdapter.filter(s?.toString() ?: "")
            }
        })

        binding.ivBack.setOnClickListener {
            if (hasChanges) showUnsavedChangesDialog { finish() } else finish()
        }

        binding.ivMinus.setOnClickListener {
            if (count > 1) { count--; hasChanges = true }
            binding.tvQty.text = "$count"
        }

        binding.ivPlus.setOnClickListener {
            count++
            hasChanges = true
            binding.tvQty.text = "$count"
        }
        setMeterData()
        attachChangeWatchers()

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasChanges) showUnsavedChangesDialog {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                } else {
                    // If you want default behavior after your logic:
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, callback)
    }

    private fun attachChangeWatchers() {
        val w = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { hasChanges = true }
        }
        binding.etType.addTextChangedListener(w)
        binding.etNote.addTextChangedListener(w)
    }

    private fun setupCapturedImagesRecycler() {
        cameraBinding.rvRoomItems.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        cameraBinding.rvRoomItems.adapter =
            ImageAdapter(allImages, title = keyItem?.name ?: "", onDelete = {
                capturedUris.clear()
                capturedUris.addAll(allImages.filterIsInstance<ImageItem.Local>().map { it.uri })
                hasChanges = true
            })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_REQUEST && resultCode == RESULT_OK) {
            val newUris = CameraActivity.Companion.pendingUris.toList()
            
            allImages.removeAll { it is ImageItem.Local }
            allImages.addAll(newUris.map { ImageItem.Local(it) })
            cameraBinding.rvRoomItems.adapter?.notifyDataSetChanged()
            
            if (newUris.isNotEmpty()) hasChanges = true
            if (savedKeyId.isNotEmpty() && newUris.isNotEmpty()) {
                val progress = ProgressDialog(this).apply {
                    setMessage("Uploading images...")
                    setCancelable(false)
                    show()
                }
                AttachmentUploadHelper.uploadImages(
                    activity = this,
                    imageUris = newUris,
                    entityId = savedKeyId,
                    entityType = "KEY",
                    onComplete = { progress.dismiss() },
                    onError = { progress.dismiss() }
                )
            } else {
                capturedUris.clear()
                capturedUris.addAll(newUris)
            }
        }
    }

    private fun setMeterData() {
        if (keyItem != null) {
            binding.etType.setText(keyItem?.name)
            count = keyItem?.no_of_keys ?: 1
            binding.tvQty.text = (keyItem?.no_of_keys ?: 0).toString()
            binding.etNote.setText(keyItem?.note ?: "")

            keyItem?.attachments?.forEach { attachment ->
                allImages.add(ImageItem.Remote(attachment.id, "${ApiClient.IMAGE_BASE_URL}${attachment.storageKey}"))
            }
            cameraBinding.rvRoomItems.adapter?.notifyDataSetChanged()
        }
    }

    private fun isValid(): Boolean {
        if (binding.etType.text.toString().isEmpty()) {
            showToast("Please enter Key Type")
            return false
        }
        return true
    }

    private fun deleteKeyApi(keyId: String) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.deleteKey(reportId, keyId) },
            listener = object : ApiResponseListener<ApiResponse<ReportData>> {
                override fun onSuccess(response: ApiResponse<ReportData>) {
                    if (response.success) {
                        showToast("Key Deleted successfully")
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

    private fun addNewKeyApi() {
        val body = AddKeyRequest(
            binding.etType.text.toString(),
            binding.tvQty.text.toString().toInt(),
            binding.etNote.text.toString()
        )

        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService ->
                if (keyItem != null) apiService.updateKey(
                    reportId,
                    keyItem?.id ?: "",
                    body
                ) else apiService.addNewKey(reportId, body)
            },
            listener = object : ApiResponseListener<ApiResponse<ReportData>> {
                override fun onSuccess(response: ApiResponse<ReportData>) {
                    if (response.success) {
                        showToast(
                            if (keyItem != null) "Key Updated successfully"
                            else "Key Added successfully"
                        )
                        if (keyItem != null) {
                            uploadPhotosIfNeeded(savedKeyId)
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
            entityType = "KEY",
            onComplete = { finish() },
            onError = { finish() }
        )
    }

    private fun uploadPhotosAfterCreate(requestBody: AddKeyRequest) {
        if (capturedUris.isEmpty()) {
            finish()
            return
        }

        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = false,
            requestAction = { api -> api.getReportKeys(reportId, include_attachments = false) },
            listener = object : ApiResponseListener<ApiResponse<ArrayList<KeyItem>>> {
                override fun onSuccess(response: ApiResponse<ArrayList<KeyItem>>) {
                    val createdKeyId = response.data
                        .asReversed()
                        .firstOrNull { key ->
                            key.name == requestBody.name &&
                                    (key.no_of_keys ?: 1) == requestBody.no_of_keys &&
                                    (key.note ?: "") == requestBody.note
                        }?.id.orEmpty()

                    if (createdKeyId.isEmpty()) {
                        showToast("Key saved, but photos could not be attached")
                        finish()
                        return
                    }

                    savedKeyId = createdKeyId
                    uploadPhotosIfNeeded(savedKeyId)
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    showToast("Key saved, but photos could not be attached")
                    finish()
                }

                override fun onError(throwable: Throwable) {
                    showToast("Key saved, but photos could not be attached")
                    finish()
                }
            }
        )
    }
}
