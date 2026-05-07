package com.wooma.data.repository

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.wooma.data.local.WoomaDatabase
import com.wooma.data.local.entity.*
import com.wooma.data.local.mapper.toEntity
import com.wooma.data.local.mapper.toQuestion
import com.wooma.data.network.RetrofitClient
import com.wooma.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

class ChecklistRepository(private val ctx: Context) {

    companion object {
        fun localAnswerAttachmentPlaceholder(checklistId: String, questionId: String) =
            "${checklistId}_local_${questionId}"
    }

    private val db = WoomaDatabase.getInstance(ctx)
    private val api by lazy { RetrofitClient.getApi(ctx) }
    private val gson = Gson()

    fun observeChecklists(reportId: String): Flow<List<ChecklistEntity>> =
        db.checklistDao().observeByReport(reportId)

    suspend fun refreshChecklistStatuses(reportId: String) = withContext(Dispatchers.IO) {
        val reportServerId = db.reportDao().getById(reportId)?.serverId ?: reportId
        val pendingIds = db.checklistDao().getPendingSyncByReport(reportId).map { it.id }.toSet()
        val resp = api.getReportCheckListStatus(reportServerId).execute()
        resp.body()?.data?.checklists?.let { list ->
            val serverEntities = list.map { it.toEntity(reportId) }
            val safeEntities = serverEntities.filter { it.id !in pendingIds }
            db.checklistDao().upsertAll(safeEntities)
        }
    }

    suspend fun saveAnswerAttachmentOffline(checklistId: String, questionId: String, uris: List<Uri>) = withContext(Dispatchers.IO) {
        val distinctUris = uris.distinctBy { it.toString() }
        if (distinctUris.isEmpty()) return@withContext
        val q = db.checklistQuestionDao().getByQuestionId(checklistId, questionId) ?: return@withContext
        val placeholder = localAnswerAttachmentPlaceholder(checklistId, questionId)
        if (q.answerAttachmentId.isNullOrBlank()) {
            db.checklistQuestionDao().updateAnswerAttachmentId(checklistId, questionId, placeholder)
        }
        val attachmentEntityLocalId = q.answerAttachmentId?.takeIf { it.isNotBlank() } ?: placeholder
        val attachmentServerId = q.answerAttachmentId?.takeIf { it != placeholder }

        val reqBody = ChecklistAnswerAttachmentRequest(
            report_checklist_id = checklistId,
            checklist_question_id = questionId
        )
        if (attachmentServerId == null && !db.syncQueueDao().hasPendingChecklistAttachmentCreate(attachmentEntityLocalId)) {
            db.syncQueueDao().enqueue(
                SyncQueueEntity(
                    entityType = "CHECKLIST_ANSWER_ATTACHMENT",
                    operationType = "CREATE",
                    localEntityId = attachmentEntityLocalId,
                    payload = gson.toJson(reqBody)
                )
            )
        }

        val attachmentRepo = AttachmentRepository(ctx)
        distinctUris.forEach { uri ->
            try {
                attachmentRepo.saveLocalAttachment(
                    uri = uri,
                    entityLocalId = attachmentEntityLocalId,
                    entityServerId = attachmentServerId,
                    entityType = "CHECKLIST_ANSWER_ATTACHMENT"
                )
            } catch (e: Exception) {
                android.util.Log.e("ChecklistRepo", "Failed to save attachment for $uri: ${e.message}")
            }
        }
    }

    suspend fun deleteQuestionAnswerAttachment(
        reportChecklistId: String,
        questionId: String,
        item: ImageItem
    ) = withContext(Dispatchers.IO) {
        val q = db.checklistQuestionDao().getByQuestionId(reportChecklistId, questionId)
            ?: return@withContext
        val containerId = q.answerAttachmentId?.takeIf { it.isNotBlank() }
            ?: return@withContext
        AttachmentRepository(ctx).deleteChecklistAnswerImage(containerId, item)
    }

    fun observeChecklistQuestions(reportChecklistId: String): Flow<List<ChecklistQuestionEntity>> =
        db.checklistQuestionDao().observeByChecklist(reportChecklistId)

    /** Questions with [Question.checklist_question_answer_attachment] filled from Room `attachments` table. */
    fun observeChecklistQuestionsForUi(reportChecklistId: String): Flow<List<Question>> = combine(
        observeChecklistQuestions(reportChecklistId),
        db.attachmentDao().observeByEntityType("CHECKLIST_ANSWER_ATTACHMENT")
    ) { questions, attRows ->
        questions.map { it.toQuestion(attRows) }
    }

    fun observeChecklistInfoFields(reportChecklistId: String): Flow<List<ChecklistInfoFieldEntity>> =
        db.checklistInfoFieldDao().observeByChecklist(reportChecklistId)

    suspend fun refreshChecklist(reportChecklistId: String, reportIdHint: String? = null) = withContext(Dispatchers.IO) {
        val resp = api.getReportCheckList(reportChecklistId, include_attachments = true).execute()
        resp.body()?.data?.let { data ->
            val reportId = reportIdHint?.takeIf { it.isNotBlank() }
                ?: db.checklistDao().getById(reportChecklistId)?.reportId
                ?: return@let
            val existingHeader = db.checklistDao().getById(reportChecklistId)
            db.checklistDao().upsert(
                ChecklistEntity(
                    id = data.id,
                    checklistTemplateId = data.checklist_id,
                    reportId = reportId,
                    name = existingHeader?.name ?: "",
                    isActive = existingHeader?.isActive ?: true,
                    syncStatus = when (existingHeader?.syncStatus) {
                        SyncStatus.PENDING_UPDATE -> SyncStatus.PENDING_UPDATE
                        else -> SyncStatus.SYNCED
                    }
                )
            )

            val pendingQuestionIds = db.checklistQuestionDao()
                .getPendingByChecklist(reportChecklistId)
                .map { it.checklistQuestionId }
                .toSet()
            val pendingFieldIds = db.checklistInfoFieldDao()
                .getPendingByChecklist(reportChecklistId)
                .map { it.fieldId }
                .toSet()

            // Answer attachments: drop server-backed rows the API no longer returns, then upsert current.
            // (Otherwise a deleted image stays in Room and reappears in the checklist detail list.)
            for (q in data.questions) {
                val qId = q.checklist_question_id ?: continue
                if (qId in pendingQuestionIds) continue

                val existingQ = db.checklistQuestionDao().getByQuestionId(reportChecklistId, qId)
                val serverAtt = q.checklist_question_answer_attachment
                val containerId = serverAtt?.id?.takeIf { it.isNotBlank() }

                if (containerId != null) {
                    val toSave = serverAtt.attachments.orEmpty().mapNotNull { att ->
                        if (att.storageKey.isNullOrEmpty()) null
                        else AttachmentEntity(
                            id = att.id ?: return@mapNotNull null,
                            serverId = att.id,
                            entityId = containerId,
                            entityType = "CHECKLIST_ANSWER_ATTACHMENT",
                            storageKey = att.storageKey,
                            link = att.url,
                            localUri = null,
                            isUploaded = true
                        )
                    }
                    val serverIds = toSave.map { it.id }.toSet()
                    val existingRows =
                        db.attachmentDao().getByEntity(containerId, "CHECKLIST_ANSWER_ATTACHMENT")
                    existingRows
                        .filter { it.isUploaded && it.id !in serverIds }
                        .forEach { db.attachmentDao().deleteById(it.id) }
                    if (toSave.isNotEmpty()) db.attachmentDao().upsertAll(toSave)
                } else {
                    val oldContainer = existingQ?.answerAttachmentId?.takeIf { it.isNotBlank() }
                    if (!oldContainer.isNullOrBlank()) {
                        val rows =
                            db.attachmentDao().getByEntity(oldContainer, "CHECKLIST_ANSWER_ATTACHMENT")
                        rows.filter { it.isUploaded }.forEach { db.attachmentDao().deleteById(it.id) }
                    }
                }
            }

            // Update Questions
            val questionEntities = data.questions
                .filter { q ->
                    val qId = q.checklist_question_id ?: return@filter false
                    qId !in pendingQuestionIds
                }
                .map { q ->
                    val qId = q.checklist_question_id ?: ""
                    val existingRow = db.checklistQuestionDao().getByQuestionId(reportChecklistId, qId)
                    ChecklistQuestionEntity(
                        localId = existingRow?.localId ?: 0,
                        reportChecklistId = reportChecklistId,
                        checklistQuestionId = qId,
                        text = q.text,
                        type = q.type,
                        displayOrder = q.displayOrder,
                        isRequired = q.is_required,
                        answerId = q.checklist_question_answer_id,
                        answerOption = q.answer_option,
                        answerText = q.answer_text,
                        note = q.note,
                        originalNote = q.original_note,
                        answerAttachmentId = q.checklist_question_answer_attachment?.id,
                        syncStatus = SyncStatus.SYNCED
                    )
                }
            // Clear old ones first to handle removals if any (or just upsert)
            db.checklistQuestionDao().upsertAll(questionEntities)

            // Update Info Fields
            val fieldEntities = data.info_fields
                .filter { f ->
                    val fId = f.checklist_field_id ?: return@filter false
                    fId !in pendingFieldIds
                }
                .map { f ->
                ChecklistInfoFieldEntity(
                    reportChecklistId = reportChecklistId,
                    fieldId = f.checklist_field_id ?: "",
                    label = f.label,
                    type = f.type,
                    isRequired = f.is_required,
                    answerId = f.checklist_info_field_answer_id,
                    answerText = f.answer_text,
                    originalAnswerText = f.original_answer_text,
                    syncStatus = SyncStatus.SYNCED
                )
            }
            db.checklistInfoFieldDao().upsertAll(fieldEntities)
        }
    }

    suspend fun upsertQuestionAnswer(reportChecklistId: String, questionId: String, answer: String?, note: String?) = withContext(Dispatchers.IO) {
        val existing = db.checklistQuestionDao().getByQuestionId(reportChecklistId, questionId)
        val mergedAnswer = answer ?: existing?.answerOption
        val mergedNote = note ?: existing?.note
        val entity = ChecklistQuestionEntity(
            localId = existing?.localId ?: 0,
            reportChecklistId = reportChecklistId,
            checklistQuestionId = questionId,
            text = existing?.text ?: "",
            type = existing?.type ?: "",
            displayOrder = existing?.displayOrder ?: 0,
            isRequired = existing?.isRequired ?: false,
            answerId = existing?.answerId,
            answerOption = mergedAnswer,
            answerText = existing?.answerText,
            note = mergedNote,
            originalNote = mergedNote,
            answerAttachmentId = existing?.answerAttachmentId,
            syncStatus = SyncStatus.PENDING_UPDATE
        )
        db.checklistQuestionDao().upsert(entity)

        val req = UpsertQuestionAnswerRequest(
            reportChecklistId,
            questionId,
            mergedAnswer,
            mergedNote
        )
        val queueKey = "${reportChecklistId}_${questionId}"

        db.syncQueueDao().deletePendingByEntityOperation(
            entityType = "CHECKLIST_QUESTION",
            operationType = "UPSERT",
            localEntityId = queueKey
        )
        db.syncQueueDao().enqueue(
            SyncQueueEntity(
                entityType = "CHECKLIST_QUESTION",
                operationType = "UPSERT",
                localEntityId = queueKey,
                payload = gson.toJson(req)
            )
        )
    }

    suspend fun upsertFieldAnswer(reportChecklistId: String, fieldId: String, answer: String) = withContext(Dispatchers.IO) {
        val existing = db.checklistInfoFieldDao().getByFieldId(reportChecklistId, fieldId)
        val entity = ChecklistInfoFieldEntity(
            localId = existing?.localId ?: 0,
            reportChecklistId = reportChecklistId,
            fieldId = fieldId,
            label = existing?.label ?: "",
            type = existing?.type ?: "",
            isRequired = existing?.isRequired ?: false,
            answerId = existing?.answerId,
            answerText = answer,
            syncStatus = SyncStatus.PENDING_UPDATE
        )
        db.checklistInfoFieldDao().upsert(entity)

        val req = UpsertFieldAnswerRequest(reportChecklistId, fieldId, answer)
        val queueKey = "${reportChecklistId}_${fieldId}"

        db.syncQueueDao().deletePendingByEntityOperation(
            entityType = "CHECKLIST_INFO_FIELD",
            operationType = "UPSERT",
            localEntityId = queueKey
        )
        db.syncQueueDao().enqueue(
            SyncQueueEntity(
                entityType = "CHECKLIST_INFO_FIELD",
                operationType = "UPSERT",
                localEntityId = queueKey,
                payload = gson.toJson(req)
            )
        )
    }

    suspend fun updateChecklistStatus(checklistId: String, isActive: Boolean): Boolean = withContext(Dispatchers.IO) {
        val existing = db.checklistDao().getById(checklistId) ?: return@withContext false

        // No effective change.
        if (existing.isActive == isActive && existing.syncStatus == SyncStatus.SYNCED) {
            return@withContext false
        }

        // If there is one unsynced toggle and user flips back, cancel pending sync.
        if (existing.syncStatus == SyncStatus.PENDING_UPDATE && isActive == !existing.isActive) {
            db.checklistDao().updateActiveStatus(checklistId, isActive, SyncStatus.SYNCED)
            db.syncQueueDao().deletePendingByEntityOperation(
                entityType = "CHECKLIST_STATUS",
                operationType = "UPDATE",
                localEntityId = checklistId
            )
            return@withContext false
        }

        db.checklistDao().updateActiveStatus(checklistId, isActive, SyncStatus.PENDING_UPDATE)
        db.syncQueueDao().deletePendingByEntityOperation(
            entityType = "CHECKLIST_STATUS",
            operationType = "UPDATE",
            localEntityId = checklistId
        )
        db.syncQueueDao().enqueue(
            SyncQueueEntity(
                entityType = "CHECKLIST_STATUS",
                operationType = "UPDATE",
                localEntityId = checklistId,
                payload = gson.toJson(ChecklistStatusRequest(isActive))
            )
        )
        true
    }
}
