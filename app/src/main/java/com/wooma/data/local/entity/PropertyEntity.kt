package com.wooma.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "properties",
    indices = [Index("isActive"), Index("syncStatus")]
)
data class PropertyEntity(
    @PrimaryKey val id: String,
    val serverId: String? = null,
    val createdBy: String? = null,
    val address: String = "",
    val addressLine2: String? = null,
    val city: String = "",
    val postcode: String = "",
    val country: String? = null,
    val propertyType: String? = null,
    val isActive: Boolean = true,
    val noOfReports: Int = 0,
    val lastActivity: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)
