package com.wooma.business.activities.report.otherItems

import android.os.Bundle
import android.util.Log
import android.view.View
import com.wooma.business.activities.BaseActivity
import com.wooma.business.adapter.SuggestionsAdapter
import com.wooma.business.customs.Utils
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.data.network.showToast
import com.wooma.business.databinding.ActivityAddEditDetectorBinding
import com.wooma.business.databinding.ActivityAddEditKeysBinding
import com.wooma.business.model.AddDetectorRequest
import com.wooma.business.model.AddKeyRequest
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.DetectorItem
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.ReportData

class AddEditDetectorActivity : BaseActivity() {
    private lateinit var binding: ActivityAddEditDetectorBinding
    var detectorItem: DetectorItem? = null

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
        applyWindowInsetsToBinding(binding.root)
        detectorItem = intent.getParcelableExtra("detectorItem")

        reportId = intent.getStringExtra("reportId") ?: ""
        isEdit = intent.getBooleanExtra("isEdit", false)

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
                deleteKeyApi(detectorItem?.id ?: "")
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

    private fun setMeterData() {
        if (detectorItem != null) {
            binding.etType.setText(detectorItem?.name)
            binding.etLocation.setText(detectorItem?.location ?: "")
            binding.etTestResult.setText(detectorItem?.note ?: "")
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

    private fun deleteKeyApi(meterId: String) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.deleteDetector(reportId, meterId) },
            listener = object : ApiResponseListener<ApiResponse<ReportData>> {
                override fun onSuccess(response: ApiResponse<ReportData>) {
                    if (response.success) {
                        showToast("Detector Deleted successfully")
                        finish()
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
                            if (detectorItem != null) {
                                "Detector Updated successfully"
                            } else "Detector Added successfully"
                        )

                        finish()
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