package com.wooma.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wooma.data.local.entity.ChecklistEntity
import com.wooma.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistDao {

    @Query("SELECT * FROM checklists WHERE reportId = :reportId")
    fun observeByReport(reportId: String): Flow<List<ChecklistEntity>>

    @Query("SELECT * FROM checklists WHERE reportId = :reportId")
    suspend fun getByReport(reportId: String): List<ChecklistEntity>

    @Query("SELECT * FROM checklists WHERE id = :id")
    suspend fun getById(id: String): ChecklistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(checklist: ChecklistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(checklists: List<ChecklistEntity>)

    @Query("UPDATE checklists SET isActive = :isActive, syncStatus = :status WHERE id = :id")
    suspend fun updateActiveStatus(id: String, isActive: Boolean, status: SyncStatus)

    @Query("UPDATE checklists SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)
}
