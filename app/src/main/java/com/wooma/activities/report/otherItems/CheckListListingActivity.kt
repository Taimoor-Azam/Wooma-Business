package com.wooma.activities.report.otherItems

import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.wooma.activities.BaseActivity
import com.wooma.adapter.CheckListAdapter
import com.wooma.data.network.ApiResponseListener
import com.wooma.data.network.MyApi
import com.wooma.data.network.makeApiRequest
import com.wooma.data.network.showToast
import com.wooma.databinding.ActivityCheckListListingBinding
import com.wooma.model.ApiResponse
import com.wooma.model.CheckListActiveStatus
import com.wooma.model.Checklist
import com.wooma.model.ChecklistStatusRequest
import com.wooma.model.ErrorResponse
import com.wooma.model.enums.TenantReportStatus

class CheckListListingActivity : BaseActivity() {
    private lateinit var binding: ActivityCheckListListingBinding
    private lateinit var adapter: CheckListAdapter
    private val checklistItems = mutableListOf<Checklist>()
    private var reportId = ""
    private var reportStatus = ""
    private var isReadOnly = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCheckListListingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        reportId = intent.getStringExtra("reportId") ?: ""
        reportStatus = intent.getStringExtra("reportStatus") ?: ""
        isReadOnly = reportStatus == TenantReportStatus.COMPLETED.value ||
                reportStatus == TenantReportStatus.HISTORICAL.value ||
                reportStatus == TenantReportStatus.TENANT_REVIEW.value

        adapter = CheckListAdapter(
            list = checklistItems,
            isReadOnly = isReadOnly,
            onToggle = { id, isActive ->
                updateChecklistStatusApi(id, isActive)
            },
            onClick = { checklist ->
                val intent = Intent(this, CheckListDetailActivity::class.java)
                intent.putExtra("reportId", reportId)
                intent.putExtra("checklistId", checklist.id)
                intent.putExtra("checklistName", checklist.name)
                intent.putExtra("reportStatus", reportStatus)
                startActivity(intent)
            }
        )

        binding.rvChecklists.adapter = adapter
        binding.ivBack.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        getReportCheckListStatusApi()
    }

    private fun getReportCheckListStatusApi() {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.getReportCheckListStatus(reportId) },
            listener = object : ApiResponseListener<ApiResponse<CheckListActiveStatus>> {
                override fun onSuccess(response: ApiResponse<CheckListActiveStatus>) {
                    if (response.success && !response.data.checklists.isNullOrEmpty()) {
                        checklistItems.clear()
                        checklistItems.addAll(response.data.checklists)
                        adapter.updateList(checklistItems)
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

    private fun updateChecklistStatusApi(id: String, isActive: Boolean) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = false,
            requestAction = { apiService ->
                apiService.updateChecklistStatus(id, ChecklistStatusRequest(isActive))
            },
            listener = object : ApiResponseListener<ApiResponse<Any>> {
                override fun onSuccess(response: ApiResponse<Any>) {
                    adapter.updateItem(id, isActive)
                }
                override fun onFailure(errorMessage: ErrorResponse?) {
                    Log.e("API", errorMessage?.error?.message ?: "")
                    showToast(errorMessage?.error?.message ?: "Failed to update checklist status")
                    // Revert switch state on failure
                    getReportCheckListStatusApi()
                }
                override fun onError(throwable: Throwable) {
                    Log.e("API", "Error: ${throwable.message}")
                    showToast("Error: ${throwable.message}")
                    getReportCheckListStatusApi()
                }
            }
        )
    }
}
