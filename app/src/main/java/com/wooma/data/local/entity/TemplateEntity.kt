package com.wooma.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val id: String,
    val name: String = "",
    val description: String? = null,
    val isActive: Boolean = true,
    val isDeleted: Boolean = false
)
