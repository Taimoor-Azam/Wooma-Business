package com.wooma.business.activities.report.otherItems

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.OnBackPressedCallback
import com.wooma.business.activities.BaseActivity
import com.wooma.business.activities.report.CameraActivity
import com.wooma.business.adapter.CheckListInfoAdapter
import com.wooma.business.adapter.InventoryCheckListQuestionAdapter
import com.wooma.business.customs.AttachmentUploadHelper
import com.wooma.business.data.network.ApiResponseListener
import com.wooma.business.data.network.MyApi
import com.wooma.business.data.network.makeApiRequest
import com.wooma.business.data.network.showToast
import com.wooma.business.databinding.ActivityCheckListDetailBinding
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.ChecklistAnswerAttachmentRequest
import com.wooma.business.model.ChecklistAnswerAttachmentResponse
import com.wooma.business.model.ChecklistData
import com.wooma.business.model.ErrorResponse
import com.wooma.business.model.InfoField
import com.wooma.business.model.Question
import com.wooma.business.model.UpsertFieldAnswerRequest
import com.wooma.business.model.UpsertQuestionAnswerRequest
import com.wooma.business.model.enums.TenantReportStatus

class CheckListDetailActivity : BaseActivity() {
    private lateinit var infoAdapter: CheckListInfoAdapter
    private lateinit var questionAdapter: InventoryCheckListQuestionAdapter
    private val checkListInfoItems = mutableListOf<InfoField>()
    private val checkListQuestionItems = mutableListOf<Question>()
    private lateinit var binding: ActivityCheckListDetailBinding

    var reportId = ""
    private var checklistId = ""
    private var checklistName = ""
    private var isReadOnly = false

    private val CAMERA_REQUEST = 1001
    private var pendingCameraQuestionId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCheckListDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        reportId = intent.getStringExtra("reportId") ?: ""
        checklistId = intent.getStringExtra("checklistId") ?: ""
        checklistName = intent.getStringExtra("checklistName") ?: ""
        val reportStatus = intent.getStringExtra("reportStatus") ?: ""

        isReadOnly = reportStatus == TenantReportStatus.COMPLETED.value ||
                reportStatus == TenantReportStatus.HISTORICAL.value ||
                reportStatus == TenantReportStatus.TENANT_REVIEW.value

        binding.tvChecklistTitle.text = checklistName

        infoAdapter = CheckListInfoAdapter(
            context = this,
            originalList = checkListInfoItems,
            reportId = reportId,
            isReadOnly = isReadOnly,
            onFieldAnswerChanged = { fieldId, answerText, showLoading ->
                if (fieldId != null) {
                    val index = checkListInfoItems.indexOfFirst { it.checklist_field_id == fieldId }
                    if (index != -1) {
                        checkListInfoItems[index].answer_text = answerText
                        upsertFieldAnswerApi(fieldId, answerText, showLoading)
                    }
                }
            }
        )

        questionAdapter = InventoryCheckListQuestionAdapter(
            context = this,
            originalList = checkListQuestionItems,
            reportId = reportId,
            isReadOnly = isReadOnly,
            onAnswerSelected = { question, answerOption ->
                val index = checkListQuestionItems.indexOfFirst { it.checklist_question_id == question.checklist_question_id }
                if (index != -1) {
                    checkListQuestionItems[index].answer_option = answerOption
                    upsertQuestionAnswerApi(checkListQuestionItems[index], answerOption, question.note, false)
                }
            },
            onNoteChanged = { question, note, showLoading ->
                val index = checkListQuestionItems.indexOfFirst { it.checklist_question_id == question.checklist_question_id }
                if (index != -1) {
                    checkListQuestionItems[index].note = note
                    upsertQuestionAnswerApi(checkListQuestionItems[index], question.answer_option, note, showLoading)
                }
            },
            onCameraClick = { questionId ->
                pendingCameraQuestionId = questionId
                CameraActivity.pendingUris.clear()
                startActivityForResult(Intent(this, CameraActivity::class.java), CAMERA_REQUEST)
            }
        )

        binding.rvInfo.adapter = infoAdapter
        binding.rvQuestions.adapter = questionAdapter

        binding.ivBack.setOnClickListener {
            backPressed()
        }

        binding.btnSave.setOnClickListener {
            saveUnsavedDataAndExit()
        }

        getChecklistDetailApi(checklistId)

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isReadOnly) {
                    saveUnsavedDataAndExit()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }

        onBackPressedDispatcher.addCallback(this, callback)
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

    fun backPressed() {
        if (!isReadOnly) {
            saveUnsavedDataAndExit()
        } else {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun saveUnsavedDataAndExit() {
        val unsavedInfoFields = checkListInfoItems.filter { it.checklist_field_id != null && it.answer_text != it.original_answer_text }
        val unsavedQuestions = checkListQuestionItems.filter { it.note != it.original_note }

        if (unsavedInfoFields.isEmpty() && unsavedQuestions.isEmpty()) {
            onBackPressedDispatcher.onBackPressed()
            return
        }

        var totalRequests = unsavedInfoFields.size + unsavedQuestions.size
        
        val onTaskComplete = {
            totalRequests--
            if (totalRequests <= 0) {
                onBackPressedDispatcher.onBackPressed()
            }
        }

        for (field in unsavedInfoFields) {
            upsertFieldAnswerApi(field.checklist_field_id!!, field.answer_text ?: "", false) {
                field.original_answer_text = field.answer_text
                onTaskComplete()
            }
        }

        for (question in unsavedQuestions) {
            upsertQuestionAnswerApi(question, question.answer_option, question.note, false) {
                question.original_note = question.note
                onTaskComplete()
            }
        }
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
                        activity = this@CheckListDetailActivity,
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

    private fun upsertQuestionAnswerApi(question: Question, answerOption: String?, note: String?, showLoading: Boolean, onComplete: (() -> Unit)? = null) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = showLoading,
            requestAction = { api ->
                api.upsertQuestionAnswer(
                    UpsertQuestionAnswerRequest(
                        report_checklist_id = checklistId,
                        checklist_question_id = question.checklist_question_id ?: "",
                        answer_option = answerOption,
                        note = note
                    )
                )
            },
            listener = object : ApiResponseListener<ApiResponse<Any>> {
                override fun onSuccess(response: ApiResponse<Any>) {
                    question.original_note = note // Sync local original value
                    onComplete?.invoke()
                }
                override fun onFailure(errorMessage: ErrorResponse?) {
                    if (showLoading) showToast(errorMessage?.error?.message ?: "Failed to save answer")
                    onComplete?.invoke()
                }
                override fun onError(throwable: Throwable) {
                    Log.e("Checklist", "upsertQuestionAnswer error: ${throwable.message}")
                    onComplete?.invoke()
                }
            }
        )
    }

    private fun upsertFieldAnswerApi(fieldId: String, answerText: String, showLoading: Boolean, onComplete: (() -> Unit)? = null) {
        makeApiRequest(
            apiServiceClass = MyApi::class.java,
            context = this,
            showLoading = showLoading,
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
                override fun onSuccess(response: ApiResponse<Any>) {
                    // Sync original_answer_text for this field
                    val field = checkListInfoItems.find { it.checklist_field_id == fieldId }
                    field?.original_answer_text = answerText
                    onComplete?.invoke()
                }
                override fun onFailure(errorMessage: ErrorResponse?) {
                    if (showLoading) showToast(errorMessage?.error?.message ?: "Failed to save field answer")
                    onComplete?.invoke()
                }
                override fun onError(throwable: Throwable) {
                    Log.e("Checklist", "upsertFieldAnswer error: ${throwable.message}")
                    onComplete?.invoke()
                }
            }
        )
    }

    private fun getChecklistDetailApi(id: String) {
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
                        
                        response.data.info_fields.forEach { it.original_answer_text = it.answer_text }
                        response.data.questions.forEach { it.original_note = it.note }
                        
                        checkListInfoItems.addAll(response.data.info_fields)
                        checkListQuestionItems.addAll(response.data.questions)

                        infoAdapter.updateList(checkListInfoItems)
                        questionAdapter.updateList(checkListQuestionItems)

                        val hasInfo = checkListInfoItems.isNotEmpty()
                        binding.tvInfo.visibility = if (hasInfo) View.VISIBLE else View.GONE
                        binding.rvInfo.visibility = if (hasInfo) View.VISIBLE else View.GONE

                        val hasQuestions = checkListQuestionItems.isNotEmpty()
                        binding.tvQuestion.visibility = if (hasQuestions) View.VISIBLE else View.GONE
                        binding.rvQuestions.visibility = if (hasQuestions) View.VISIBLE else View.GONE
                    }
                }
                override fun onFailure(errorMessage: ErrorResponse?) {
                    showToast(errorMessage?.error?.message ?: "")
                }
                override fun onError(throwable: Throwable) {
                    showToast("Error: ${throwable.message}")
                }
            }
        )
    }

}
