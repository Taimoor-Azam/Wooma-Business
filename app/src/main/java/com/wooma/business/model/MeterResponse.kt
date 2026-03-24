package com.wooma.business.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Meter(
    val id: String,
    val is_active: Boolean,
    val is_deleted: Boolean,
    val created_at: String,
    val updated_at: String,
    val report_id: String,
    val name: String,
    val reading: String?,
    val location: String?,
    val serial_number: String?,
    val display_order: String,
    val attachments: List<OtherItemsAttachment>
) : Parcelable

@Parcelize
data class OtherItemsAttachment(
    val id: String,
    val is_active: Boolean,
    val is_deleted: Boolean,
    val created_at: String,
    val updated_at: String,
    val entityId: String,
    val entityType: String,
    val originalName: String,
    val storageKey: String,
    val link: String?,
    val mimeType: String,
    val fileSize: String
) : Parcelable


data class AddMeterRequest(
    var name: String,
    var reading: String,
    var location: String,
    var serial_number: String
)


data class AddKeyRequest(
    var name: String,
    var no_of_keys: Int,
    var note: String
)

data class AddDetectorRequest(
    var name: String,
    var location: String,
    var note: String
)

@Parcelize
data class KeyItem(
    val id: String,
    val name: String,
    val report_id: String,
    val no_of_keys: Int? = 1,
    val note: String?,
    val display_order: String,
    val is_deleted: Boolean,
    val created_at: String,
    val updated_at: String
) : Parcelable


@Parcelize
data class DetectorItem(
    val id: String,
    val name: String,
    val report_id: String,
    val location: String?,
    val note: String?,
    val display_order: String,
    val is_deleted: Boolean,
    val created_at: String,
    val updated_at: String
) : Parcelable

