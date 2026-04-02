package com.wooma.business.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

data class PropertyDetailResponse(
    val id: String,
    val created_by: String,
    val address: String,
    val address_line_2: String?,
    val city: String,
    val postcode: String,
    val country: String,
    val property_type: String,
    val is_active: Boolean,
    val reports: ArrayList<Report>,
    val created_at: String,
    val updated_at: String
)

data class Report(
    val id: String,
    val report_type: PropertyReportType?,
    val status: String,
    val is_active: Boolean,
    val is_deleted: Boolean,
    val assessor: Assessor,
    val completion_date: String?,
    val created_at: String,
    val updated_at: String,
)

@Parcelize
data class PropertyReportType(
    val id: String,
    val display_name: String?,
    val type_code: String
): Parcelable

@Parcelize
data class Assessor(
    val id: String?,
    val first_name: String?,
    val last_name: String?,
    val email: String?
) : Parcelable