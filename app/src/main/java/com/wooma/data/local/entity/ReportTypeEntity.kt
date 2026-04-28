package com.wooma.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "report_types")
data class ReportTypeEntity(
    @PrimaryKey val id: String,
    val typeCode: String = "",
    val displayName: String = "",
    val description: String = "",
    val createdAt: String = "",
    val updatedAt: String = ""
)
