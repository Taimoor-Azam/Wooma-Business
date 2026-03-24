package com.wooma.business.activities.report.otherItems

import android.os.Bundle
import android.util.Log
import android.view.View
import com.wooma.business.activities.BaseActivity
import com.wooma.business.adapter.CheckListInfoAdapter
import com.wooma.business.adapter.InventoryCheckListQuestionAdapter
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.data.network.showToast
import com.wooma.business.databinding.ActivityInventoryChecklistBinding
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.CheckListActiveStatus
import com.wooma.business.model.ChecklistData
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.InfoField
import com.wooma.business.model.Question

class CheckListListingActivity : BaseActivity() {
    private lateinit var infoAdapter: CheckListInfoAdapter
    private lateinit var questionAdapter: InventoryCheckListQuestionAdapter
    private val checkListInfoItems = mutableListOf<InfoField>()
    private val checkListQuestionItems = mutableListOf<Question>()
    private lateinit var binding: ActivityInventoryChecklistBinding
    var reportId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityInventoryChecklistBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        reportId = intent.getStringExtra("reportId") ?: ""

        infoAdapter = CheckListInfoAdapter(this, checkListInfoItems, reportId)
        questionAdapter = InventoryCheckListQuestionAdapter(this, checkListQuestionItems, reportId)

        binding.rvInfo.adapter = infoAdapter
        binding.rvQuestions.adapter = questionAdapter

        binding.ivBack.setOnClickListener { finish() }

        binding.switchButton.setOnCheckedChangeListener { view, isChecked ->
            if (isChecked) {

            }
        }
    }

    override fun onResume() {
        super.onResume()
        getReportCheckListStatusApi()
    }

    private fun getReportByIdApi(id: String) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.getReportCheckList(id, true) },
            listener = object : ApiResponseListener<ApiResponse<ChecklistData>> {
                override fun onSuccess(response: ApiResponse<ChecklistData>) {
                    if (response.success) {
                        checkListInfoItems.clear()
                        checkListQuestionItems.clear()

                        checkListInfoItems.addAll(response.data.info_fields)
                        checkListQuestionItems.addAll(response.data.questions)

                        infoAdapter.updateList(checkListInfoItems)
                        questionAdapter.updateList(checkListQuestionItems)
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

    private fun getReportCheckListStatusApi() {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = true,
            requestAction = { apiService -> apiService.getReportCheckListStatus(reportId) },
            listener = object : ApiResponseListener<ApiResponse<CheckListActiveStatus>> {
                override fun onSuccess(response: ApiResponse<CheckListActiveStatus>) {
                    if (response.success) {
                        if (!response.data.checklists.isNullOrEmpty()
                            && response.data.checklists[0].is_active
                        ) {
                            getReportByIdApi(response.data.checklists[0].id)
                            binding.tvInfo.visibility = View.VISIBLE
                            binding.tvQuestion.visibility = View.VISIBLE
                            binding.switchButton.isChecked = true
                        } else {
                            binding.tvInfo.visibility = View.GONE
                            binding.tvQuestion.visibility = View.GONE
                            binding.switchButton.isChecked = false

                        }
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