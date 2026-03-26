package com.wooma.business.activities.report.otherItems

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import com.wooma.business.activities.BaseActivity
import com.wooma.business.activities.report.CameraActivity
import com.wooma.business.adapter.CheckListInfoAdapter
import com.wooma.business.adapter.InventoryCheckListQuestionAdapter
import com.wooma.business.customs.AttachmentUploadHelper
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.data.network.showToast
import com.wooma.business.databinding.ActivityInventoryChecklistBinding
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.CheckListActiveStatus
import com.wooma.business.model.ChecklistAnswerAttachmentRequest
import com.wooma.business.model.ChecklistAnswerAttachmentResponse
import com.wooma.business.model.ChecklistData
import com.wooma.business.model.ChecklistStatusRequest
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.InfoField
import com.wooma.business.model.Question
import com.wooma.business.model.UpsertFieldAnswerRequest
import com.wooma.business.model.UpsertQuestionAnswerRequest

class CheckListListingActivity : BaseActivity() {
    private lateinit var infoAdapter: CheckListInfoAdapter
    private lateinit var questionAdapter: InventoryCheckListQuestionAdapter
    private val checkListInfoItems = mutableListOf<InfoField>()
    private val checkListQuestionItems = mutableListOf<Question>()
    private lateinit var binding: ActivityInventoryChecklistBinding
    var reportId = ""
    private var checklistId = ""
    private var isInitializingSwitch = false

    private val CAMERA_REQUEST = 1001
    private var pendingCameraQuestionId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityInventoryChecklistBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        reportId = intent.getStringExtra("reportId") ?: ""

        infoAdapter = CheckListInfoAdapter(
            context = this,
            originalList = checkListInfoItems,
            reportId = reportId,
            onFieldAnswerChanged = { fieldId, answerText ->
                upsertFieldAnswerApi(fieldId, answerText)
            }
        )

        questionAdapter = InventoryCheckListQuestionAdapter(
            context = this,
            originalList = checkListQuestionItems,
            reportId = reportId,
            onAnswerSelected = { question, answerOption ->
                upsertQuestionAnswerApi(question, answerOption, question.note)
            },
            onNoteChanged = { question, note ->
                upsertQuestionAnswerApi(question, question.answer_option, note)
            },
            onCameraClick = { questionId ->
                pendingCameraQuestionId = questionId
                CameraActivity.pendingUris.clear()
                startActivityForResult(Intent(this, CameraActivity::class.java), CAMERA_REQUEST)
            }
        )

        binding.rvInfo.adapter = infoAdapter
        binding.rvQuestions.adapter = questionAdapter

        binding.ivBack.setOnClickListener { finish() }

        binding.switchButton.setOnCheckedChangeListener { _, isChecked ->
            if (isInitializingSwitch) return@setOnCheckedChangeListener
            if (checklistId.isNotEmpty()) {
                updateChecklistStatusApi(checklistId, isChecked)
            }
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK) {
            val uris = CameraActivity.pendingUris.toList()
            if (uris.isNotEmpty() && pendingCameraQuestionId.isNotEmpty()) {
                questionAdapter.deliverPhotos(pendingCameraQuestionId, uris)
                uploadQuestionPhotos(pendingCameraQuestionId, uris)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        getReportCheckListStatusApi()
    }

    private fun uploadQuestionPhotos(questionId: String, uris: List<Uri>) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = false,
            requestAction = { api ->
                api.findOrCreateAnswerAttachment(
                    ChecklistAnswerAttachmentRequest(
                        report_checklist_id = checklistId,
                        checklist_question_id = questionId
                    )
                )
            },
            listener = object : ApiResponseListener<ApiResponse<ChecklistAnswerAttachmentResponse>> {
                override fun onSuccess(response: ApiResponse<ChecklistAnswerAttachmentResponse>) {
                    AttachmentUploadHelper.uploadImages(
                        activity = this@CheckListListingActivity,
                        imageUris = uris,
                        entityId = response.data.id,
                        entityType = "CHECKLIST_ANSWER_ATTACHMENT",
                        onComplete = {},
                        onError = { msg ->
                            Log.e("Checklist", "Photo upload failed: $msg")
                        }
                    )
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    Log.e("Checklist", "findOrCreate failed: ${errorMessage?.error?.message}")
                }

                override fun onError(throwable: Throwable) {
                    Log.e("Checklist", "Network error: ${throwable.message}")
                }
            }
        )
    }

    private fun upsertQuestionAnswerApi(question: Question, answerOption: String?, note: String?) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = false,
            requestAction = { api ->
                api.upsertQuestionAnswer(
                    UpsertQuestionAnswerRequest(
                        report_checklist_id = checklistId,
                        checklist_question_id = question.checklistQuestionId,
                        answer_option = answerOption,
                        note = note
                    )
                )
            },
            listener = object : ApiResponseListener<ApiResponse<Any>> {
                override fun onSuccess(response: ApiResponse<Any>) {}
                override fun onFailure(errorMessage: ErrorResponse?) {
                    showToast(errorMessage?.error?.message ?: "Failed to save answer")
                }
                override fun onError(throwable: Throwable) {
                    Log.e("Checklist", "upsertQuestionAnswer error: ${throwable.message}")
                }
            }
        )
    }

    private fun upsertFieldAnswerApi(fieldId: String, answerText: String) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = false,
            requestAction = { api ->
                api.upsertFieldAnswer(
                    UpsertFieldAnswerRequest(
                        report_checklist_id = checklistId,
                        checklist_field_id = fieldId,
                        answer_text = answerText
                    )
                )
            },
            listener = object : ApiResponseListener<ApiResponse<Any>> {
                override fun onSuccess(response: ApiResponse<Any>) {}
                override fun onFailure(errorMessage: ErrorResponse?) {
                    showToast(errorMessage?.error?.message ?: "Failed to save field answer")
                }
                override fun onError(throwable: Throwable) {
                    Log.e("Checklist", "upsertFieldAnswer error: ${throwable.message}")
                }
            }
        )
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
                    if (isActive) {
                        getReportByIdApi(id)
                        binding.tvInfo.visibility = View.VISIBLE
                        binding.tvQuestion.visibility = View.VISIBLE
                    } else {
                        binding.tvInfo.visibility = View.GONE
                        binding.tvQuestion.visibility = View.GONE
                    }
                }

                override fun onFailure(errorMessage: ErrorResponse?) {
                    Log.e("API", errorMessage?.error?.message ?: "")
                    showToast(errorMessage?.error?.message ?: "Failed to update checklist status")
                }

                override fun onError(throwable: Throwable) {
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
                    if (response.success && !response.data.checklists.isNullOrEmpty()) {
                        checklistId = response.data.checklists[0].id
                        val isActive = response.data.checklists[0].is_active

                        isInitializingSwitch = true
                        binding.switchButton.isChecked = isActive
                        isInitializingSwitch = false

                        if (isActive) {
                            getReportByIdApi(checklistId)
                            binding.tvInfo.visibility = View.VISIBLE
                            binding.tvQuestion.visibility = View.VISIBLE
                        } else {
                            binding.tvInfo.visibility = View.GONE
                            binding.tvQuestion.visibility = View.GONE
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
}
