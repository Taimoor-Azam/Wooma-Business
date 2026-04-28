package com.wooma.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tenant_reviews",
    foreignKeys = [
        ForeignKey(
            entity = ReportEntity::class,
            parentColumns = ["id"],
            childColumns = ["reportId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("reportId")]
)
data class TenantReviewEntity(
    @PrimaryKey val id: String,
    val reportId: String,
    val firstName: String = "",
    val lastName: String = "",
    val emailAddress: String = "",
    val mobileNumber: String? = null,
    val isSubmitted: Boolean = false,
    val submittedAt: String? = null,
    val isActive: Boolean = true,
    val isDeleted: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = ""
)
