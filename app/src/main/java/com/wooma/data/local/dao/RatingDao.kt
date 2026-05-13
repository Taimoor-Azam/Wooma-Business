package com.wooma.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.wooma.data.local.entity.RatingEntity

@Dao
interface RatingDao {
    @Query("SELECT * FROM ratings WHERE category = :category")
    suspend fun getByCategory(category: String): List<RatingEntity>

    @Query("DELETE FROM ratings")
    suspend fun deleteAll()

    @Upsert
    suspend fun upsertAll(ratings: List<RatingEntity>)
}
