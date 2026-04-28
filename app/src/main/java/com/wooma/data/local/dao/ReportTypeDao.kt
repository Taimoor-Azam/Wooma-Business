package com.wooma.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wooma.data.local.entity.ReportTypeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReportTypeDao {

    @Query("SELECT * FROM report_types")
    fun observeAll(): Flow<List<ReportTypeEntity>>

    @Query("SELECT * FROM report_types")
    suspend fun getAll(): List<ReportTypeEntity>

    @Query("SELECT * FROM report_types WHERE id = :id")
    suspend fun getById(id: String): ReportTypeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reportType: ReportTypeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(reportTypes: List<ReportTypeEntity>)
}
