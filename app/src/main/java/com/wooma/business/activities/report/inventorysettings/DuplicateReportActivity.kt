package com.wooma.business.activities.report.inventorysettings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import com.wooma.business.activities.BaseActivity
import com.wooma.business.activities.report.SelectPropertyForReportActivity
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.data.network.showToast
import com.wooma.business.databinding.ActivityDuplicateReportBinding
import com.wooma.business.model.AddReportResponse
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.CreateDuplicateReport
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.Property

class DuplicateReportActivity : BaseActivity() {
    private lateinit var binding: ActivityDuplicateReportBinding

    var property: Property? = null
    var reportId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDuplicateReportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        reportId = intent.getStringExtra("reportId") ?: ""

        binding.propertyLayout.setOnClickListener {
            val intent = Intent(this, SelectPropertyForReportActivity::class.java)
            getResult.launch(intent)
        }

        binding.ivBack.setOnClickListener { finish() }
        binding.btnAddReport.setOnClickListener {
            if (property != null)
                duplicateReportApi() else showToast("Please select property first.")
        }
    }

    private fun duplicateReportApi() {
        val item = CreateDuplicateReport(
            property_id = property?.id ?: "",
            include_images = if (binding.cbCopyPhoto.isChecked) true else null,
            include_descriptions = true,
            report_id = reportId
        )

        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.createDuplicateReport(item) },
            listener = object : ApiResponseListener<ApiResponse<AddReportResponse>> {
                override fun onSuccess(response: ApiResponse<AddReportResponse>) {
                    if (response.success) {
                        showToast("Report duplicated successfully")
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

    private val getResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data

            // Use IntentCompat for a more type-safe approach (AndroidX)
            property = data?.let {
                IntentCompat.getParcelableExtra(it, "propertyItem", Property::class.java)
            }
            binding.tvProperty.text = property?.address ?: ""
        }
    }
}