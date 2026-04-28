package com.wooma.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wooma.data.local.entity.AssessorEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AssessorDao {

    @Query("SELECT * FROM assessors WHERE isActive = 1 ORDER BY firstName ASC")
    fun observeActive(): Flow<List<AssessorEntity>>

    @Query("SELECT * FROM assessors WHERE isActive = 1")
    suspend fun getActive(): List<AssessorEntity>

    @Query("SELECT * FROM assessors WHERE id = :id")
    suspend fun getById(id: String): AssessorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(assessor: AssessorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(assessors: List<AssessorEntity>)
}
