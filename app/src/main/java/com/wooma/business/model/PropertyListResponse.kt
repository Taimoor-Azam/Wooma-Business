package com.wooma.business.model

import android.os.Parcel
import android.os.Parcelable
import com.google.gson.annotations.SerializedName

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
) : Parcelable {

    constructor(parcel: Parcel) : this(
        id = parcel.readString(),
        createdBy = parcel.readString(),
        address = parcel.readString(),
        addressLine2 = parcel.readString(),
        city = parcel.readString(),
        postcode = parcel.readString(),
        country = parcel.readString(),
        propertyType = parcel.readString(),
        isActive = parcel.readValue(Boolean::class.java.classLoader) as? Boolean,
        noOfReports = parcel.readValue(Int::class.java.classLoader) as? Int,
        lastActivity = parcel.readString(),
        createdAt = parcel.readString(),
        updatedAt = parcel.readString()
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(createdBy)
        parcel.writeString(address)
        parcel.writeString(addressLine2)
        parcel.writeString(city)
        parcel.writeString(postcode)
        parcel.writeString(country)
        parcel.writeString(propertyType)
        parcel.writeValue(isActive)
        parcel.writeValue(noOfReports)
        parcel.writeString(lastActivity)
        parcel.writeString(createdAt)
        parcel.writeString(updatedAt)
    }

    companion object CREATOR : Parcelable.Creator<Property> {
        override fun createFromParcel(parcel: Parcel): Property = Property(parcel)
        override fun newArray(size: Int): Array<Property?> = arrayOfNulls(size)
    }
}
