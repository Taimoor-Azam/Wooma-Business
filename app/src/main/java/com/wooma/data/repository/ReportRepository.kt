package com.wooma.data.repository

import android.content.Context
import com.google.gson.Gson
import com.wooma.data.local.WoomaDatabase
import com.wooma.data.local.dao.PropertyDao
import com.wooma.data.local.dao.ReportDao
import com.wooma.data.local.entity.ReportEntity
import com.wooma.data.local.entity.SyncQueueEntity
import com.wooma.data.local.mapper.toEntity
import com.wooma.data.local.mapper.toPropertyEntity
import com.wooma.data.network.RetrofitClient
import com.wooma.model.ChangeAssessor
import com.wooma.model.ChangeDateRequest
import com.wooma.model.changeReportType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ReportRepository(private val context: Context) {

    private val db = WoomaDatabase.getInstance(context)
    private val reportDao: ReportDao = db.reportDao()
    private val propertyDao: PropertyDao = db.propertyDao()
    private val api = RetrofitClient.getApi(context)
    private val gson = Gson()

    fun observeByProperty(propertyId: String): Flow<List<ReportEntity>> =
        reportDao.observeByProperty(propertyId)

    suspend fun hasData(propertyId: String): Boolean =
        withContext(Dispatchers.IO) { reportDao.countByProperty(propertyId) > 0 }

    suspend fun refreshByProperty(propertyId: String) = withContext(Dispatchers.IO) {
        val r = api.getPropertyById(propertyId).execute()
        if (r.isSuccessful) {
            val data = r.body()?.data ?: return@withContext
            // UPDATE in-place to avoid INSERT OR REPLACE which CASCADE-deletes all reports
            val rowsUpdated = propertyDao.updateFromServer(
                id = data.id,
                address = data.address,
                addressLine2 = data.address_line_2,
                city = data.city,
                postcode = data.postcode,
                country = data.country,
                propertyType = data.property_type,
                isActive = data.is_active,
                updatedAt = data.updated_at
            )
            if (rowsUpdated == 0) propertyDao.insertIgnore(data.toPropertyEntity())
            // Guard: only sync reports when the API actually returned them
            if (data.reports.isNotEmpty()) {
                val pendingIds = db.reportDao().getPendingSyncReports()
                    .filter { it.propertyId == propertyId }
                    .map { it.id }.toSet()
                // UPDATE in-place (no REPLACE, no CASCADE-delete of rooms) for each report
                for (report in data.reports) {
                    if (report.id in pendingIds) continue
                    val entity = report.toEntity(propertyId)
                    val updated = reportDao.updateFromServer(
                        id = entity.id,
                        reportTypeId = entity.reportTypeId,
                        reportTypeCode = entity.reportTypeCode,
                        reportTypeDisplayName = entity.reportTypeDisplayName,
                        status = entity.status,
                        assessorId = entity.assessorId,
                        assessorFirstName = entity.assessorFirstName,
                        assessorLastName = entity.assessorLastName,
                        assessorEmail = entity.assessorEmail,
                        completionDate = entity.completionDate,
                        isActive = entity.isActive,
                        isDeleted = entity.isDeleted,
                        updatedAt = entity.updatedAt
                    )
                    if (updated == 0) reportDao.insertIgnore(entity)
                }
            }
        }
    }

    suspend fun updateAssessor(
        reportId: String,
        assessorUserId: String,
        firstName: String,
        lastName: String
    ) = withContext(Dispatchers.IO) {
        reportDao.updateAssessor(reportId, assessorUserId, firstName, lastName)
        reportDao.updateSyncStatus(reportId, com.wooma.data.local.entity.SyncStatus.PENDING_UPDATE)
        db.syncQueueDao().enqueue(
            SyncQueueEntity(
                entityType = "REPORT_ASSESSOR", operationType = "UPDATE",
                localEntityId = reportId, payload = gson.toJson(ChangeAssessor(assessorUserId))
            )
        )
    }

    suspend fun updateCompletionDate(reportId: String, date: String) = withContext(Dispatchers.IO) {
        reportDao.updateCompletionDate(reportId, date)
        reportDao.updateSyncStatus(reportId, com.wooma.data.local.entity.SyncStatus.PENDING_UPDATE)
        db.syncQueueDao().enqueue(
            SyncQueueEntity(
                entityType = "REPORT_DATE", operationType = "UPDATE",
                localEntityId = reportId, payload = gson.toJson(ChangeDateRequest(date))
            )
        )
    }

    suspend fun updateReportType(
        reportId: String,
        typeId: String,
        typeName: String,
        typeCode: String
    ) = withContext(Dispatchers.IO) {
        reportDao.updateReportType(reportId, typeId, typeName, typeCode)
        reportDao.updateSyncStatus(reportId, com.wooma.data.local.entity.SyncStatus.PENDING_UPDATE)
        db.syncQueueDao().enqueue(
            SyncQueueEntity(
                entityType = "REPORT_TYPE", operationType = "UPDATE",
                localEntityId = reportId, payload = gson.toJson(changeReportType(typeId))
            )
        )
    }
}
