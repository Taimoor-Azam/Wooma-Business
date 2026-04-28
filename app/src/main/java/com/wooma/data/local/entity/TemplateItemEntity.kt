package com.wooma.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "template_items",
    foreignKeys = [
        ForeignKey(
            entity = TemplateRoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateRoomId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("templateRoomId")]
)
data class TemplateItemEntity(
    @PrimaryKey val id: String,
    val templateRoomId: String,
    val name: String = "",
    val displayOrder: String = ""
)
