package com.wooma.business.model

import com.google.gson.annotations.SerializedName

data class TemplateData(
    @SerializedName("data")
    val templates: ArrayList<Template>,
    val total: Int,
    val schema: String,
    val page: Int,
    val limit: Int
)

data class Template(
    val id: String,
    val name: String,
    val description: String?,
    @SerializedName("is_active")
    val isActive: Boolean,
    @SerializedName("is_deleted")
    val isDeleted: Boolean,
    @SerializedName("created_at")
    val createdAt: String,
    @SerializedName("updated_at")
    val updatedAt: String,
    val rooms: ArrayList<Room>
)

data class Room(
    val id: String,
    @SerializedName("template_id")
    val templateId: String,
    val name: String,
    @SerializedName("display_order")
    val displayOrder: String,
    var isSelected: Boolean = true,
    val items: ArrayList<Item>
)

data class Item(
    val id: String,
    @SerializedName("template_room_id")
    val templateRoomId: String,
    val name: String,
    @SerializedName("display_order")
    val displayOrder: String
)
