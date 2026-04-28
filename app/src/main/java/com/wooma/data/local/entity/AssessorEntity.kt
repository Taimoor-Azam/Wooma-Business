package com.wooma.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "assessors")
data class AssessorEntity(
    @PrimaryKey val id: String,
    val userId: String = "",
    val email: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val role: String = "",
    val isActive: Boolean = true
)
