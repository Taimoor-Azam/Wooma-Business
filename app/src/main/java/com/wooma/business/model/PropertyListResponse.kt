package com.wooma.business.model

import android.os.Parcel
import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

data class TenantPropertiesResponse(
    val success: Boolean,
    val data: TenantPropertiesWrapper,
    val metadata: Metadata
)

data class TenantPropertiesWrapper(
    val data: ArrayList<Property>,
    val total: Int,
    val schema: String,
    val page: Int,
    val limit: Int
)

@Parcelize
data class Property(
    val id: String?,
    @SerializedName("created_by")
    val createdBy: String?,
    val address: String?,
    @SerializedName("address_line_2")
    val addressLine2: String?,
    val city: String?,
    val postcode: String?,
    val country: String?,
    @SerializedName("property_type")
    val propertyType: String?,
    @SerializedName("is_active")
    val isActive: Boolean?,
    @SerializedName("no_of_reports")
    val noOfReports: Int?,
    @SerializedName("last_activity")
    val lastActivity: String?,
    @SerializedName("created_at")
    val createdAt: String?,
    @SerializedName("updated_at")
    val updatedAt: String?
) : Parcelable
