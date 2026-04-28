package com.wooma.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wooma.data.local.entity.ChecklistInfoFieldEntity
import com.wooma.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistInfoFieldDao {

    @Query("SELECT * FROM checklist_info_fields WHERE reportChecklistId = :checklistId")
    fun observeByChecklist(checklistId: String): Flow<List<ChecklistInfoFieldEntity>>

    @Query("SELECT * FROM checklist_info_fields WHERE reportChecklistId = :checklistId")
    suspend fun getByChecklist(checklistId: String): List<ChecklistInfoFieldEntity>

    @Query("SELECT * FROM checklist_info_fields WHERE localId = :localId")
    suspend fun getById(localId: Long): ChecklistInfoFieldEntity?

    @Query("SELECT * FROM checklist_info_fields WHERE reportChecklistId = :checklistId AND fieldId = :fieldId LIMIT 1")
    suspend fun getByFieldId(checklistId: String, fieldId: String): ChecklistInfoFieldEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(field: ChecklistInfoFieldEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(fields: List<ChecklistInfoFieldEntity>)

    @Query("UPDATE checklist_info_fields SET syncStatus = :status WHERE localId = :localId")
    suspend fun updateSyncStatus(localId: Long, status: SyncStatus)

    @Query("SELECT * FROM checklist_info_fields WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSync(): List<ChecklistInfoFieldEntity>

    @Query("DELETE FROM checklist_info_fields WHERE reportChecklistId = :checklistId")
    suspend fun deleteByChecklist(checklistId: String)
}
