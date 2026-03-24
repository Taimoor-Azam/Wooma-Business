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
import com.wooma.business.databinding.ActivityAddEditKeysBinding
import com.wooma.business.model.AddKeyRequest
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.KeyItem
import com.wooma.business.model.ReportData

class AddEditKeysActivity : BaseActivity() {
    private lateinit var binding: ActivityAddEditKeysBinding
    var keyItem: KeyItem? = null
    var count = 1

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
        applyWindowInsetsToBinding(binding.root)
        keyItem = intent.getParcelableExtra("keyItem")

        reportId = intent.getStringExtra("reportId") ?: ""
        isEdit = intent.getBooleanExtra("isEdit", false)

        binding.btnSave.setOnClickListener {
            if (isValid()) {
                addNewMeterApi()
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

    private fun setMeterData() {
        if (keyItem != null) {
            binding.etType.setText(keyItem?.name)
            count = keyItem?.no_of_keys ?: 1

            binding.tvQty.text = (keyItem?.no_of_keys?: 0).toString()
            binding.etNote.setText(keyItem?.note?: "")
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

    private fun deleteKeyApi(meterId: String) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.deleteKey(reportId, meterId) },
            listener = object : ApiResponseListener<ApiResponse<ReportData>> {
                override fun onSuccess(response: ApiResponse<ReportData>) {
                    if (response.success) {
                        showToast("Key Deleted successfully")
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

    private fun addNewMeterApi() {
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
                            if (keyItem != null) {
                                "Key Updated successfully"
                            } else "Key Added successfully"
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