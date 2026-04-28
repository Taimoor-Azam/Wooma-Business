package com.wooma.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wooma.data.local.entity.TenantReviewEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TenantReviewDao {

    @Query("SELECT * FROM tenant_reviews WHERE reportId = :reportId")
    fun observeByReport(reportId: String): Flow<List<TenantReviewEntity>>

    @Query("SELECT * FROM tenant_reviews WHERE reportId = :reportId")
    suspend fun getByReport(reportId: String): List<TenantReviewEntity>

    @Query("SELECT * FROM tenant_reviews WHERE id = :id")
    suspend fun getById(id: String): TenantReviewEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(review: TenantReviewEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(reviews: List<TenantReviewEntity>)

    @Query("DELETE FROM tenant_reviews WHERE reportId = :reportId")
    suspend fun deleteByReport(reportId: String)

    @Query("DELETE FROM tenant_reviews WHERE id = :id")
    suspend fun deleteById(id: String)
}
