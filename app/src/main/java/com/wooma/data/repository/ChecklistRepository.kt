package com.wooma.data.repository

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.wooma.data.local.WoomaDatabase
import com.wooma.data.local.entity.*
import com.wooma.data.local.mapper.toEntity
import com.wooma.data.network.RetrofitClient
import com.wooma.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class ChecklistRepository(private val ctx: Context) {

    private val db = WoomaDatabase.getInstance(ctx)
    private val api by lazy { RetrofitClient.getApi(ctx) }
    private val gson = Gson()

    fun observeChecklists(reportId: String): Flow<List<ChecklistEntity>> =
        db.checklistDao().observeByReport(reportId)

    suspend fun refreshChecklistStatuses(reportId: String) = withContext(Dispatchers.IO) {
        val resp = api.getReportCheckListStatus(reportId).execute()
        resp.body()?.data?.checklists?.let { list ->
            db.checklistDao().upsertAll(list.map { it.toEntity(reportId) })
        }
    }

    suspend fun saveAnswerAttachmentOffline(checklistId: String, questionId: String, uris: List<Uri>) = withContext(Dispatchers.IO) {
        val localId = "local_${UUID.randomUUID().toString().replace("-", "")}"
        db.syncQueueDao().enqueue(
            SyncQueueEntity(
                entityType = "CHECKLIST_ANSWER_ATTACHMENT",
                operationType = "CREATE",
                localEntityId = localId,
                payload = gson.toJson(ChecklistAnswerAttachmentRequest(
                    report_checklist_id = checklistId,
                    checklist_question_id = questionId
                ))
            )
        )
        val attachmentRepo = AttachmentRepository(ctx)
        uris.forEach { uri ->
            try {
                attachmentRepo.saveLocalAttachment(
                    uri = uri,
                    entityLocalId = localId,
                    entityServerId = null,
                    entityType = "CHECKLIST_ANSWER_ATTACHMENT"
                )
            } catch (e: Exception) {
                android.util.Log.e("ChecklistRepo", "Failed to save attachment for $uri: ${e.message}")
            }
        }
    }

    fun observeChecklistQuestions(reportChecklistId: String): Flow<List<ChecklistQuestionEntity>> =
        db.checklistQuestionDao().observeByChecklist(reportChecklistId)

    fun observeChecklistInfoFields(reportChecklistId: String): Flow<List<ChecklistInfoFieldEntity>> =
        db.checklistInfoFieldDao().observeByChecklist(reportChecklistId)

    suspend fun refreshChecklist(reportChecklistId: String) = withContext(Dispatchers.IO) {
        val resp = api.getReportCheckList(reportChecklistId, include_attachments = true).execute()
        resp.body()?.data?.let { data ->
            // Save answer attachments to local DB
            data.questions.forEach { q ->
                val answerAtt = q.checklist_question_answer_attachment ?: return@forEach
                val toSave = answerAtt.attachments?.mapNotNull { att ->
                    if (att.storageKey.isNullOrEmpty()) null
                    else AttachmentEntity(
                        id = att.id ?: return@mapNotNull null,
                        serverId = att.id,
                        entityId = answerAtt.id,
                        entityType = "CHECKLIST_ANSWER",
                        storageKey = att.storageKey,
                        link = att.url,
                        localUri = null,
                        isUploaded = true
                    )
                } ?: return@forEach
                if (toSave.isNotEmpty()) db.attachmentDao().upsertAll(toSave)
            }

            // Update Questions
            val questionEntities = data.questions.map { q ->
                ChecklistQuestionEntity(
                    reportChecklistId = reportChecklistId,
                    checklistQuestionId = q.checklist_question_id ?: "",
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
            val fieldEntities = data.info_fields.map { f ->
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
        val entity = ChecklistQuestionEntity(
            localId = existing?.localId ?: 0,
            reportChecklistId = reportChecklistId,
            checklistQuestionId = questionId,
            text = existing?.text ?: "",
            type = existing?.type ?: "",
            displayOrder = existing?.displayOrder ?: 0,
            isRequired = existing?.isRequired ?: false,
            answerId = existing?.answerId,
            answerOption = answer,
            note = note,
            syncStatus = SyncStatus.PENDING_UPDATE
        )
        db.checklistQuestionDao().upsert(entity)

        db.syncQueueDao().enqueue(
            SyncQueueEntity(
                entityType = "CHECKLIST_QUESTION",
                operationType = "UPSERT",
                localEntityId = "${reportChecklistId}_${questionId}",
                payload = gson.toJson(UpsertQuestionAnswerRequest(reportChecklistId, questionId, answer, note))
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

        db.syncQueueDao().enqueue(
            SyncQueueEntity(
                entityType = "CHECKLIST_INFO_FIELD",
                operationType = "UPSERT",
                localEntityId = "${reportChecklistId}_${fieldId}",
                payload = gson.toJson(UpsertFieldAnswerRequest(reportChecklistId, fieldId, answer))
            )
        )
    }

    suspend fun updateChecklistStatus(checklistId: String, isActive: Boolean) = withContext(Dispatchers.IO) {
        db.checklistDao().updateActiveStatus(checklistId, isActive, SyncStatus.PENDING_UPDATE)
        db.syncQueueDao().enqueue(
            SyncQueueEntity(
                entityType = "CHECKLIST_STATUS",
                operationType = "UPDATE",
                localEntityId = checklistId,
                payload = gson.toJson(ChecklistStatusRequest(isActive))
            )
        )
    }
}
