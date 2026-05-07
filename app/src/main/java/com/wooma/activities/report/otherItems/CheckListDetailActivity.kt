package com.wooma.activities.report.otherItems

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wooma.activities.BaseActivity
import com.wooma.activities.report.CameraActivity
import com.wooma.adapter.CheckListInfoAdapter
import com.wooma.adapter.InventoryCheckListQuestionAdapter
import com.wooma.customs.Utils
import com.wooma.data.local.mapper.toInfoField
import com.wooma.data.network.showToast
import com.wooma.data.repository.ChecklistRepository
import com.wooma.databinding.ActivityCheckListDetailBinding
import com.wooma.model.ImageItem
import com.wooma.model.InfoField
import com.wooma.model.Question
import com.wooma.model.enums.TenantReportStatus
import com.wooma.sync.SyncScheduler
import kotlinx.coroutines.launch

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
    private var showTimestamp = true

    private lateinit var checklistRepo: ChecklistRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCheckListDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsetsToBinding(binding.root)

        reportId = intent.getStringExtra("reportId") ?: ""
        checklistId = intent.getStringExtra("checklistId") ?: ""
        checklistName = intent.getStringExtra("checklistName") ?: ""
        val reportStatus = intent.getStringExtra("reportStatus") ?: ""
        showTimestamp = intent.getBooleanExtra("showTimestamp", true)

        isReadOnly = reportStatus == TenantReportStatus.COMPLETED.value ||
                reportStatus == TenantReportStatus.HISTORICAL.value ||
                reportStatus == TenantReportStatus.TENANT_REVIEW.value

        checklistRepo = ChecklistRepository(this)

        binding.tvChecklistTitle.text = checklistName

        lifecycleScope.launch {
            if (!Utils.isOnline(this@CheckListDetailActivity)) {
                showToast("Please connect to internet to continue.")
                finish()
                return@launch
            }
        }

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
                    }
                    upsertFieldAnswer(fieldId, answerText)
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
                if (index != -1) checkListQuestionItems[index].answer_option = answerOption
                upsertQuestionAnswer(question, answerOption, question.note)
            },
            onNoteChanged = { question, note, showLoading ->
                val index = checkListQuestionItems.indexOfFirst { it.checklist_question_id == question.checklist_question_id }
                if (index != -1) checkListQuestionItems[index].note = note
                upsertQuestionAnswer(question, question.answer_option, note)
            },
            onCameraClick = { questionId ->
                pendingCameraQuestionId = questionId
                CameraActivity.Companion.pendingUris.clear()
                startActivityForResult(
                    Intent(this, CameraActivity::class.java)
                        .putExtra("showTimestamp", showTimestamp),
                    CAMERA_REQUEST
                )
            },
            onChecklistImageDelete = if (!isReadOnly) { questionId, item, onComplete ->
                lifecycleScope.launch {
                    try {
                        checklistRepo.deleteQuestionAnswerAttachment(checklistId, questionId, item)
                        onComplete()
                    } catch (e: Exception) {
                        showToast(e.message ?: "Failed to delete image")
                    }
                }
            } else null
        )

        binding.rvInfo.adapter = infoAdapter
        binding.rvQuestions.adapter = questionAdapter

        binding.ivBack.setOnClickListener { backPressed() }
        binding.btnSave.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    checklistRepo.observeChecklistInfoFields(checklistId).collect { entities ->
                        checkListInfoItems.clear()
                        checkListInfoItems.addAll(entities.map { it.toInfoField() })
                        infoAdapter.updateList(checkListInfoItems)
                        binding.tvInfo.visibility = if (checkListInfoItems.isNotEmpty()) View.VISIBLE else View.GONE
                        binding.rvInfo.visibility = binding.tvInfo.visibility
                    }
                }
                launch {
                    checklistRepo.observeChecklistQuestionsForUi(checklistId).collect { questions ->
                        checkListQuestionItems.clear()
                        checkListQuestionItems.addAll(questions)
                        questionAdapter.updateList(checkListQuestionItems)
                        binding.tvQuestion.visibility = if (checkListQuestionItems.isNotEmpty()) View.VISIBLE else View.GONE
                        binding.rvQuestions.visibility = binding.tvQuestion.visibility
                    }
                }
            }
        }

        if (Utils.isOnline(this)) {
            lifecycleScope.launch {
                try { checklistRepo.refreshChecklist(checklistId, reportId) } catch (_: Exception) {}
            }
        }

        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isReadOnly) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
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
        if (requestCode == CAMERA_REQUEST && resultCode == RESULT_OK) {
            val uris = CameraActivity.Companion.pendingUris
                .map { it }
                .distinctBy { it.toString() }
                .also { CameraActivity.Companion.pendingUris.clear() }
            val qId = pendingCameraQuestionId
            if (uris.isNotEmpty() && qId.isNotEmpty()) {
                questionAdapter.deliverPhotos(qId, uris)
                saveAnswerAttachmentOffline(qId, uris)
            }
        }
    }

    fun backPressed() {
        onBackPressedDispatcher.onBackPressed()
    }

    private fun upsertQuestionAnswer(question: Question, answerOption: String?, note: String?) {
        lifecycleScope.launch {
            checklistRepo.upsertQuestionAnswer(
                checklistId,
                question.checklist_question_id ?: "",
                answerOption,
                note
            )
            SyncScheduler.scheduleImmediateSync(this@CheckListDetailActivity)
        }
    }

    private fun upsertFieldAnswer(fieldId: String, answerText: String) {
        lifecycleScope.launch {
            checklistRepo.upsertFieldAnswer(checklistId, fieldId, answerText)
            SyncScheduler.scheduleImmediateSync(this@CheckListDetailActivity)
        }
    }

    private fun saveAnswerAttachmentOffline(questionId: String, uris: List<Uri>) {
        lifecycleScope.launch {
            checklistRepo.saveAnswerAttachmentOffline(checklistId, questionId, uris)
            questionAdapter.clearLocalPhotos(questionId)
            SyncScheduler.scheduleImmediateSync(this@CheckListDetailActivity)
        }
    }
}
