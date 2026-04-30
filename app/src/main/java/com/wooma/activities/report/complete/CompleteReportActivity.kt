package com.wooma.activities.report.complete

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.wooma.activities.BaseActivity
import com.wooma.adapter.AddTenantsAdapter
import com.wooma.R
import com.wooma.data.local.WoomaDatabase
import com.wooma.data.network.ApiResponseListener
import com.wooma.data.network.MyApi
import com.wooma.data.network.makeApiRequest
import com.wooma.data.network.showToast
import com.wooma.databinding.ActivityCompleteReportBinding
import com.wooma.model.ApiResponse
import com.wooma.model.CompleteReportRequest
import com.wooma.model.ErrorResponse
import com.wooma.customs.Utils
import com.wooma.model.ReportData
import com.wooma.model.Tenant
import com.wooma.model.TenantsRequest
import com.wooma.model.Users
import com.wooma.sync.ConnectivityObserver
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class CompleteReportActivity : BaseActivity() {
    private lateinit var adapter: AddTenantsAdapter
    private val tenantsList = mutableListOf<Users>()

    var count = 1

    var reportId = ""

    private lateinit var binding: ActivityCompleteReportBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCompleteReportBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        reportId = intent.getStringExtra("reportId") ?: ""

        adapter = AddTenantsAdapter(this)
        binding.rvTenants.adapter = adapter

        binding.ivBack.setOnClickListener { finish() }

        val user = Tenant("", "", "", "")
        adapter.updateItem(user)

        binding.addAnotherTenantLayout.setOnClickListener {
            adapter.updateItem(Tenant("", "", "", ""))
        }

        binding.checkTenantSignature.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                binding.manualSignatureLayout.visibility = View.GONE
                binding.rvTenants.visibility = View.VISIBLE
                binding.addAnotherTenantLayout.visibility = View.VISIBLE
                binding.btnSendReview.text = getString(R.string.send_for_review_signature)
            } else {
                binding.manualSignatureLayout.visibility = View.VISIBLE

                binding.rvTenants.visibility = View.GONE
                binding.addAnotherTenantLayout.visibility = View.GONE
                binding.btnSendReview.text = getString(R.string.generate_document)
            }
        }

        binding.btnSendReview.setOnClickListener {
            if (binding.checkTenantSignature.isChecked) {
                val users: ArrayList<Tenant> = adapter.getItems() // your adapter function
                val hasEmptyFields = users.any { user ->
                    user.first_name.isEmpty() ||
                            user.last_name.isEmpty() ||
                            user.email_address.isEmpty()
                }
                if (hasEmptyFields) {
                    showToast("Please fill all tenants first name, last name and email")
                } else {
                    val body = TenantsRequest(users)
                    sendReportForApproval(body)
                }
            } else {
                updateReportStatusToComplete()
            }
        }

        observeSyncGate()

        binding.ivMinus.setOnClickListener {
            if (count > 1)
                count--
            binding.tvTotalSigns.text = "$count"
        }

        binding.ivPlus.setOnClickListener {
            count++
            binding.tvTotalSigns.text = "$count"
        }
    }

    private fun observeSyncGate() {
        val db = WoomaDatabase.getInstance(this)
        val connectivity = ConnectivityObserver(this)
        lifecycleScope.launch {
            combine(
                db.syncQueueDao().countPending(),
                db.pendingUploadDao().countPending(),
                connectivity.observeConnectivity()
            ) { pendingSync, pendingUploads, isOnline ->
                Triple(pendingSync, pendingUploads, isOnline)
            }.collect { (pendingSync, pendingUploads, isOnline) ->
                val hasPending = pendingSync > 0 || pendingUploads > 0
                val canComplete = isOnline && !hasPending
                binding.btnSendReview.isEnabled = canComplete
                binding.btnSendReview.alpha = if (canComplete) 1f else 0.5f
                binding.tvSyncStatus.isVisible = !canComplete
                binding.tvSyncStatus.text = when {
                    hasPending -> "Syncing changes… please wait"
                    !isOnline  -> "Internet connection required"
                    else       -> ""
                }
            }
        }
    }

    private fun sendReportForApproval(body: TenantsRequest) {

        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.sendReportForApproval(reportId, body) },
            listener = object : ApiResponseListener<ApiResponse<ArrayList<ReportData>>> {
                override fun onSuccess(response: ApiResponse<ArrayList<ReportData>>) {
                    if (response.success) {
                        Utils.showReportCompletedDialog(
                            context = this@CompleteReportActivity,
                            title = "Sent for review",
                            message = "The report has been sent to tenant successfully"
                        ) { finish() }
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

    private fun updateReportStatusToComplete() {
        val body = CompleteReportRequest(binding.tvTotalSigns.text.toString().toInt())
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.completeReport(reportId, body) },
            listener = object : ApiResponseListener<ApiResponse<ReportData>> {
                override fun onSuccess(response: ApiResponse<ReportData>) {
                    if (response.success) {
                        Utils.showReportCompletedDialog(this@CompleteReportActivity) { finish() }
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