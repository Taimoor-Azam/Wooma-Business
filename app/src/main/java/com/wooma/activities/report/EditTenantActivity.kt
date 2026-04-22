package com.wooma.activities.report

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import com.wooma.activities.BaseActivity
import com.wooma.R
import com.wooma.customs.Utils
import com.wooma.data.network.ApiResponseListener
import com.wooma.data.network.MyApi
import com.wooma.data.network.makeApiRequest
import com.wooma.data.network.showToast
import com.wooma.databinding.ActivityEditTenantBinding
import com.wooma.model.ApiResponse
import com.wooma.model.ReportData
import com.wooma.model.ErrorResponse
import com.wooma.model.Tenant
import com.wooma.model.TenantReview
import com.wooma.model.TenantsRequest
import com.wooma.model.UpdateTenantReviewRequest

class EditTenantActivity : BaseActivity() {
    private lateinit var binding: ActivityEditTenantBinding

    private var isEditMode = false
    private var reportId = ""
    private var tenantReviewId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityEditTenantBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        isEditMode = intent.getBooleanExtra("isEditMode", false)
        reportId = intent.getStringExtra("reportId") ?: ""
        tenantReviewId = intent.getStringExtra("tenantReviewId") ?: ""
        val tenantCount = intent.getIntExtra("tenantCount", 0)

        if (isEditMode) {
            // Pre-fill fields
            binding.etFirstName.setText(intent.getStringExtra("firstName") ?: "")
            binding.etLastName.setText(intent.getStringExtra("lastName") ?: "")
            binding.etEmail.setText(intent.getStringExtra("email") ?: "")
            binding.etPhone.setText(intent.getStringExtra("mobileNumber") ?: "")

            // Email not editable in edit mode
            binding.etEmail.isEnabled = false
            binding.etEmail.isFocusable = false
            binding.etEmail.isFocusableInTouchMode = false
            binding.etEmail.setBackgroundResource(R.drawable.bg_edittext_disabled)
            binding.etEmail.setTextColor(ContextCompat.getColor(this, R.color.light_grey))

            // Show delete icon only if more than one tenant
            binding.ivTenantDelete.visibility = if (tenantCount > 1) View.VISIBLE else View.GONE
            binding.ivTenantDelete.setOnClickListener {
                Utils.showDialogBox(
                    this,
                    "Delete Tenant",
                    "Are you sure you want to remove this tenant?"
                ) {
                    deleteTenantApi()
                }
            }
        } else {
            binding.ivTenantDelete.visibility = View.GONE
        }

        binding.btnResend.setOnClickListener {
            val firstName = binding.etFirstName.text.toString().trim()
            val lastName = binding.etLastName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val mobile = binding.etPhone.text.toString().trim()

            if (firstName.isEmpty()) { showToast("Please enter first name"); return@setOnClickListener }
            if (lastName.isEmpty()) { showToast("Please enter last name"); return@setOnClickListener }
            if (email.isEmpty()) { showToast("Please enter email"); return@setOnClickListener }

            if (isEditMode) {
                updateTenantApi(firstName, lastName, mobile)
            } else {
                addTenantApi(firstName, lastName, email, mobile)
            }
        }

        binding.ivBack.setOnClickListener { finish() }
    }

    private fun addTenantApi(firstName: String, lastName: String, email: String, mobile: String) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { api ->
                api.sendReportForApproval(
                    id = reportId,
                    request = TenantsRequest(
                        arrayListOf(
                            Tenant(
                                first_name = firstName,
                                last_name = lastName,
                                mobile_number = mobile,
                                email_address = email
                            )
                        )
                    )
                )
            },
            listener = object : ApiResponseListener<ApiResponse<ArrayList<ReportData>>> {
                override fun onSuccess(response: ApiResponse<ArrayList<ReportData>>) {
                    showToast("Tenant added successfully")
                    setResult(RESULT_OK)
                    finish()
                }
                override fun onFailure(errorMessage: ErrorResponse?) {
                    showToast(errorMessage?.error?.message ?: "Failed to add tenant")
                }
                override fun onError(throwable: Throwable) {
                    showToast("Error: ${throwable.message}")
                }
            }
        )
    }

    private fun updateTenantApi(firstName: String, lastName: String, mobile: String) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { api ->
                api.updateTenantReview(
                    reportId = reportId,
                    tenantReviewId = tenantReviewId,
                    request = UpdateTenantReviewRequest(
                        first_name = firstName,
                        last_name = lastName,
                        mobile_number = mobile
                    )
                )
            },
            listener = object : ApiResponseListener<ApiResponse<TenantReview>> {
                override fun onSuccess(response: ApiResponse<TenantReview>) {
                    showToast("Tenant updated successfully")
                    setResult(RESULT_OK)
                    finish()
                }
                override fun onFailure(errorMessage: ErrorResponse?) {
                    showToast(errorMessage?.error?.message ?: "Failed to update tenant")
                }
                override fun onError(throwable: Throwable) {
                    showToast("Error: ${throwable.message}")
                }
            }
        )
    }

    private fun deleteTenantApi() {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { api ->
                api.deleteTenantReview(
                    reportId = reportId,
                    tenantReviewId = tenantReviewId
                )
            },
            listener = object : ApiResponseListener<ApiResponse<Any>> {
                override fun onSuccess(response: ApiResponse<Any>) {
                    showToast("Tenant deleted successfully")
                    setResult(RESULT_OK)
                    finish()
                }
                override fun onFailure(errorMessage: ErrorResponse?) {
                    showToast(errorMessage?.error?.message ?: "Failed to delete tenant")
                }
                override fun onError(throwable: Throwable) {
                    showToast("Error: ${throwable.message}")
                }
            }
        )
    }
}
