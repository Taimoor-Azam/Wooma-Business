package com.wooma.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wooma.data.local.entity.ReportEntity
import com.wooma.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportDao {

    @Query("SELECT * FROM reports WHERE propertyId = :propertyId AND isDeleted = 0 ORDER BY createdAt DESC")
    fun observeByProperty(propertyId: String): Flow<List<ReportEntity>>

    @Query("SELECT COUNT(*) FROM reports WHERE propertyId = :propertyId AND isDeleted = 0")
    suspend fun countByProperty(propertyId: String): Int

    @Query("SELECT * FROM reports WHERE id = :id")
    fun observeById(id: String): Flow<ReportEntity?>

    @Query("SELECT * FROM reports WHERE id = :id")
    suspend fun getById(id: String): ReportEntity?

    @Query("SELECT * FROM reports WHERE propertyId = :propertyId AND isDeleted = 0 ORDER BY createdAt DESC")
    suspend fun getByProperty(propertyId: String): List<ReportEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(report: ReportEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(reports: List<ReportEntity>)

    @Query("UPDATE reports SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    @Query("UPDATE reports SET serverId = :newServerId, syncStatus = 'SYNCED' WHERE id = :localId")
    suspend fun promoteLocalId(localId: String, newServerId: String)

    @Query("UPDATE reports SET countRooms = countRooms + :delta WHERE id = :reportId")
    suspend fun incrementRoomCount(reportId: String, delta: Int)

    @Query("UPDATE reports SET countMeters = countMeters + :delta WHERE id = :reportId")
    suspend fun incrementMeterCount(reportId: String, delta: Int)

    @Query("UPDATE reports SET countKeys = countKeys + :delta WHERE id = :reportId")
    suspend fun incrementKeyCount(reportId: String, delta: Int)

    @Query("UPDATE reports SET countDetectors = countDetectors + :delta WHERE id = :reportId")
    suspend fun incrementDetectorCount(reportId: String, delta: Int)

    @Query("UPDATE reports SET status = :status WHERE id = :reportId")
    suspend fun updateStatus(reportId: String, status: String)

    @Query("UPDATE reports SET coverImageStorageKey = :key WHERE id = :reportId")
    suspend fun updateCoverImage(reportId: String, key: String?)

    @Query("SELECT * FROM reports WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSyncReports(): List<ReportEntity>

    @Query("DELETE FROM reports WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE reports SET assessorId = :assessorId, assessorFirstName = :firstName, assessorLastName = :lastName WHERE id = :reportId")
    suspend fun updateAssessor(reportId: String, assessorId: String, firstName: String, lastName: String)

    @Query("UPDATE reports SET completionDate = :date WHERE id = :reportId")
    suspend fun updateCompletionDate(reportId: String, date: String)

    @Query("UPDATE reports SET reportTypeId = :typeId, reportTypeDisplayName = :typeName, reportTypeCode = :typeCode WHERE id = :reportId")
    suspend fun updateReportType(reportId: String, typeId: String, typeName: String, typeCode: String)

    @Query("UPDATE reports SET countMeters = :meters, countKeys = :keys, countDetectors = :detectors, countChecklists = :checklists WHERE id = :reportId")
    suspend fun updateCounts(reportId: String, meters: Int, keys: Int, detectors: Int, checklists: Int)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(report: ReportEntity): Long

    @Query("UPDATE reports SET isDeleted = 1 WHERE id = :reportId")
    suspend fun setDeletedAndSynced(reportId: String)

    @Query("UPDATE reports SET isDeleted = 1, syncStatus = 'PENDING_UPDATE' WHERE id = :reportId")
    suspend fun archiveLocal(reportId: String)

    @Query("""UPDATE reports SET
        reportTypeId = :reportTypeId,
        reportTypeCode = :reportTypeCode,
        reportTypeDisplayName = :reportTypeDisplayName,
        status = :status,
        assessorId = :assessorId,
        assessorFirstName = :assessorFirstName,
        assessorLastName = :assessorLastName,
        assessorEmail = :assessorEmail,
        completionDate = :completionDate,
        isActive = :isActive,
        isDeleted = :isDeleted,
        updatedAt = :updatedAt
        WHERE id = :id AND syncStatus = 'SYNCED'""")
    suspend fun updateFromServer(
        id: String,
        reportTypeId: String,
        reportTypeCode: String,
        reportTypeDisplayName: String,
        status: String,
        assessorId: String?,
        assessorFirstName: String?,
        assessorLastName: String?,
        assessorEmail: String?,
        completionDate: String,
        isActive: Boolean,
        isDeleted: Boolean,
        updatedAt: String
    ): Int
}
