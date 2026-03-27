package com.wooma.business.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

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
    val counts: Counts
)

@Parcelize
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
) : Parcelable

@Parcelize
data class RoomInspection(
    val id: String? = null,
    @SerializedName("room_id")
    val roomId: String? = null,
    @SerializedName("is_issue")
    val isIssue: Boolean? = null,
    val note: String? = null,
    val priority: String? = null,
    val attachments: List<Attachment>? = null
) : Parcelable

@Parcelize
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
) : Parcelable

data class UpdateRoomItemRequest(
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

@Parcelize
data class Attachment(
    val id: String? = null,
    val url: String? = null,
    val storageKey: String? = null
) : Parcelable

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

data class AddNewRoomItemsRequest(
    val room_items: List<String>
)

fun Counts.toCountItemList(): ArrayList<CountItem> {
    return arrayListOf(
        CountItem("Meters", meters),
        CountItem("Keys", keys),
        CountItem("Detectors", detectors),
//        CountItem("Rooms", rooms),
        CountItem("Checklists", activeChecklists)
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
    val mobile_number: String,
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
