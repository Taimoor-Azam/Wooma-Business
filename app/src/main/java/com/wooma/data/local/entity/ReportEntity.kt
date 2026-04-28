package com.wooma.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reports",
    foreignKeys = [
        ForeignKey(
            entity = PropertyEntity::class,
            parentColumns = ["id"],
            childColumns = ["propertyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("propertyId"), Index("syncStatus"), Index("status")]
)
data class ReportEntity(
    @PrimaryKey val id: String,
    val serverId: String? = null,
    val propertyId: String,
    val reportTypeId: String = "",
    val reportTypeCode: String = "",
    val reportTypeDisplayName: String = "",
    val status: String = "",
    val assessorId: String? = null,
    val assessorFirstName: String? = null,
    val assessorLastName: String? = null,
    val assessorEmail: String? = null,
    val completionDate: String = "",
    val tenantReviewExpiry: String? = null,
    val extendReviewExpiry: String? = null,
    val completionPercentage: String = "0",
    val coverImageStorageKey: String? = null,
    val pdfUrl: String? = null,
    val blankSpacesCount: Int = 0,
    val isActive: Boolean = true,
    val isDeleted: Boolean = false,
    val createdAt: String = "",
    val updatedAt: String = "",
    val countMeters: Int = 0,
    val countKeys: Int = 0,
    val countDetectors: Int = 0,
    val countRooms: Int = 0,
    val countChecklists: Int = 0,
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)
