package com.wooma.model

import android.os.Parcel
import android.os.Parcelable
import com.google.gson.annotations.SerializedName

data class ReportData(
    @SerializedName("report_id") val reportId: String,
    @SerializedName("completion_date") val completionDate: String,
    @SerializedName("completion_percentage") val completionPercentage: String,
    @SerializedName("tenant_review_expiry") val tenantReviewExpiry: String?,
    @SerializedName("extend_review_expiry") val extendReviewExpiry: String?,
    @SerializedName("report_type") val reportType: ReportType,
    val property: Property,
    val assessor: Assessor,
    val status: String,
    val rooms: ArrayList<RoomsResponse>?,
    val counts: Counts,
    val attachments: List<AttachmentRecord>? = null,
    @SerializedName("cover_image_storage_key") val coverImageStorageKey: String? = null,
    @SerializedName("pdf_url") val pdfUrl: String? = null,
    @SerializedName("blank_spaces_count") val blankSpacesCount: Int = 0
)

data class UpdateReportRequest(
    @SerializedName("cover_image_storage_key") val cover_image_storage_key: String?
)

data class ReorderRoomRequest(
    val prev_rank: String?,
    val next_rank: String?
)

data class RoomsResponse(
    val id: String? = null,
    @SerializedName("template_id")
    val templateId: String? = null,
    val name: String? = null,
    @SerializedName("display_order")
    val displayOrder: String? = null,
    var isSelected: Boolean = true,
    val items: ArrayList<RoomItem>? = null,
    val inspection: ArrayList<RoomInspection>? = null,
    val attachments: ArrayList<OtherItemsAttachment>? = null
) : Parcelable {

    constructor(parcel: Parcel) : this(
        id = parcel.readString(),
        templateId = parcel.readString(),
        name = parcel.readString(),
        displayOrder = parcel.readString(),
        isSelected = parcel.readByte() != 0.toByte(),
        items = parcel.createTypedArrayList(RoomItem.CREATOR),
        inspection = parcel.createTypedArrayList(RoomInspection.CREATOR),
        attachments = parcel.createTypedArrayList(OtherItemsAttachment.CREATOR)
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(templateId)
        parcel.writeString(name)
        parcel.writeString(displayOrder)
        parcel.writeByte(if (isSelected) 1 else 0)
        parcel.writeTypedList(items)
        parcel.writeTypedList(inspection)
        parcel.writeTypedList(attachments)
    }

    companion object CREATOR : Parcelable.Creator<RoomsResponse> {
        override fun createFromParcel(parcel: Parcel): RoomsResponse = RoomsResponse(parcel)
        override fun newArray(size: Int): Array<RoomsResponse?> = arrayOfNulls(size)
    }
}

data class RoomInspection(
    val id: String? = null,
    @SerializedName("room_id")
    val roomId: String? = null,
    @SerializedName("is_issue")
    val isIssue: Boolean? = null,
    val note: String? = null,
    val priority: String? = null,
    val attachments: List<Attachment>? = null
) : Parcelable {

    constructor(parcel: Parcel) : this(
        id = parcel.readString(),
        roomId = parcel.readString(),
        isIssue = parcel.readValue(Boolean::class.java.classLoader) as? Boolean,
        note = parcel.readString(),
        priority = parcel.readString(),
        attachments = parcel.createTypedArrayList(Attachment.CREATOR)
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(roomId)
        parcel.writeValue(isIssue)
        parcel.writeString(note)
        parcel.writeString(priority)
        parcel.writeTypedList(attachments)
    }

    companion object CREATOR : Parcelable.Creator<RoomInspection> {
        override fun createFromParcel(parcel: Parcel): RoomInspection = RoomInspection(parcel)
        override fun newArray(size: Int): Array<RoomInspection?> = arrayOfNulls(size)
    }
}

data class RoomItem(
    val id: String? = null,
    val is_active: Boolean? = null,
    val is_deleted: Boolean? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
    val room_id: String? = null,
    val name: String? = null,
    val general_condition: String? = null,
    val general_cleanliness: String? = null,
    val description: String? = null,
    val note: String? = null,
    val display_order: String? = null,
    val attachments: List<Attachment>? = null
) : Parcelable {

    constructor(parcel: Parcel) : this(
        id = parcel.readString(),
        is_active = parcel.readValue(Boolean::class.java.classLoader) as? Boolean,
        is_deleted = parcel.readValue(Boolean::class.java.classLoader) as? Boolean,
        created_at = parcel.readString(),
        updated_at = parcel.readString(),
        room_id = parcel.readString(),
        name = parcel.readString(),
        general_condition = parcel.readString(),
        general_cleanliness = parcel.readString(),
        description = parcel.readString(),
        note = parcel.readString(),
        display_order = parcel.readString(),
        attachments = parcel.createTypedArrayList(Attachment.CREATOR)
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeValue(is_active)
        parcel.writeValue(is_deleted)
        parcel.writeString(created_at)
        parcel.writeString(updated_at)
        parcel.writeString(room_id)
        parcel.writeString(name)
        parcel.writeString(general_condition)
        parcel.writeString(general_cleanliness)
        parcel.writeString(description)
        parcel.writeString(note)
        parcel.writeString(display_order)
        parcel.writeTypedList(attachments)
    }

    companion object CREATOR : Parcelable.Creator<RoomItem> {
        override fun createFromParcel(parcel: Parcel): RoomItem = RoomItem(parcel)
        override fun newArray(size: Int): Array<RoomItem?> = arrayOfNulls(size)
    }
}

data class UpdateRoomItemRequest(
    val name: String? = null,
    val general_condition: String?,
    val general_cleanliness: String?,
    val description: String?,
    val note: String?
)

data class UpsertRoomInspectionRequest(
    val room_id: String,
    val is_issue: Boolean,
    val note: String? = null,
    val priority: String? = null
)

data class Attachment(
    val id: String? = null,
    val url: String? = null,
    val storageKey: String? = null
) : Parcelable {

    constructor(parcel: Parcel) : this(
        id = parcel.readString(),
        url = parcel.readString(),
        storageKey = parcel.readString()
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(url)
        parcel.writeString(storageKey)
    }

    companion object CREATOR : Parcelable.Creator<Attachment> {
        override fun createFromParcel(parcel: Parcel): Attachment = Attachment(parcel)
        override fun newArray(size: Int): Array<Attachment?> = arrayOfNulls(size)
    }
}

data class Inspection(
    val id: String? = null
)

data class ConditionDAO(
    var icon: Int,
    var name: String
)

data class CountItem(
    val label: String,
    val value: Int
)

data class Counts(
    val meters: Int,
    val keys: Int,
    val detectors: Int,
    val rooms: Int,
    @SerializedName("active_checklists") val activeChecklists: Int
)

data class AddNewRoomsRequest(
    val rooms: List<String>
)

data class UpdateRoomNameRequest(
    val name: String
)

data class AddNewRoomItemsRequest(
    val room_items: List<String>
)

fun Counts.toCountItemList(): ArrayList<CountItem> {
    return arrayListOf(
        CountItem("Meters", meters),
        CountItem("Keys", keys),
        CountItem("Detectors", detectors),
//        CountItem("Rooms", rooms),
        CountItem("Checklist", activeChecklists)
    )
}

data class TenantReview(
    val id: String,
    val is_active: Boolean,
    val is_deleted: Boolean,
    val created_at: String,
    val updated_at: String,
    val first_name: String,
    val last_name: String,
    val email_address: String,
    val mobile_number: String?,
    val report_id: String,
    val token: String,
    val lookup_key: String,
    val is_used: Boolean,
    val metadata: Any?,
    val full_name: String?,
    val is_submitted: Boolean,
    val submitted_at: String?,
    val room_answers: List<Any>,
    val keys_answers: List<Any>,
    val detecter_answers: List<Any>,
    val checklist_answers: List<Any>,
    val meter_answers: List<Any>
)

data class ExtendTimeRequest(
    var new_expiry_date: String
)

data class AssessorUsers(
    val id: String,
    val user_id: String,
    val email: String,
    val first_name: String,
    val last_name: String,
    val role: String,
    val role_id: String,
    val created_at: String,
    val is_active: Boolean,
    val is_invited: Boolean,
    val is_accepted_invitation: Boolean,
    val invitation_link_expires_at: String?
)
