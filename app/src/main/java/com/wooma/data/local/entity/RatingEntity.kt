package com.wooma.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ratings")
data class RatingEntity(
    @PrimaryKey val id: String,
    val category: String,
    val typeCode: String,
    val displayName: String,
    val description: String?,
    val isDefault: Boolean,
    val displayOrder: String?
)
