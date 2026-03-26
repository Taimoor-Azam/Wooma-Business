package com.wooma.business.activities.report.otherItems

import android.app.Activity
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
import com.wooma.business.databinding.ActivityAddEditKeysBinding
import com.wooma.business.databinding.AddImageLayoutBinding
import com.wooma.business.model.AddKeyRequest
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.KeyItem
import com.wooma.business.model.ReportData

class AddEditKeysActivity : BaseActivity() {
    private lateinit var binding: ActivityAddEditKeysBinding
    private lateinit var cameraBinding: AddImageLayoutBinding
    var keyItem: KeyItem? = null
    var savedKeyId = ""
    var count = 1

    private val capturedUris = mutableListOf<Any>()
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

    var isEdit = false
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
            CameraActivity.pendingUris.clear()
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

        binding.rvSuggestions.adapter = SuggestionsAdapter(
            this,
            suggestionList,
            object : SuggestionsAdapter.OnItemClickInterface {
                override fun onItemClick(item: String) {
                    binding.etType.setText(item)
                }
            })

        binding.ivBack.setOnClickListener { finish() }

        binding.ivMinus.setOnClickListener {
            if (count > 1)
                count--
            binding.tvQty.text = "$count"
        }

        binding.ivPlus.setOnClickListener {
            count++
            binding.tvQty.text = "$count"
        }
        setMeterData()
    }

    private fun setupCapturedImagesRecycler() {
        cameraBinding.rvRoomItems.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        cameraBinding.rvRoomItems.adapter = ImageAdapter(capturedUris)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK) {
            capturedUris.addAll(CameraActivity.pendingUris)
            cameraBinding.rvRoomItems.adapter?.notifyDataSetChanged()
        }
    }

    private fun setMeterData() {
        if (keyItem != null) {
            binding.etType.setText(keyItem?.name)
            count = keyItem?.no_of_keys ?: 1
            binding.tvQty.text = (keyItem?.no_of_keys ?: 0).toString()
            binding.etNote.setText(keyItem?.note ?: "")
        }
    }

    private fun isValid(): Boolean {
        if (binding.etType.text.toString().isEmpty()) {
            showToast("Please enter Key Type")
            return false
        } else if (binding.etNote.text.toString().isEmpty()) {
            showToast("Please enter Note")
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
                        uploadPhotosIfNeeded(savedKeyId)
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
}
