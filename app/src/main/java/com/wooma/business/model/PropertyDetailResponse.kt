package com.wooma.business.model

import android.os.Parcel
import android.os.Parcelable

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

data class PropertyReportType(
    val id: String,
    val display_name: String?,
    val type_code: String
) : Parcelable {

    constructor(parcel: Parcel) : this(
        id = parcel.readString() ?: "",
        display_name = parcel.readString(),
        type_code = parcel.readString() ?: ""
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(display_name)
        parcel.writeString(type_code)
    }

    companion object CREATOR : Parcelable.Creator<PropertyReportType> {
        override fun createFromParcel(parcel: Parcel): PropertyReportType = PropertyReportType(parcel)
        override fun newArray(size: Int): Array<PropertyReportType?> = arrayOfNulls(size)
    }
}

data class Assessor(
    val id: String?,
    val first_name: String?,
    val last_name: String?,
    val email: String?
) : Parcelable {

    constructor(parcel: Parcel) : this(
        id = parcel.readString(),
        first_name = parcel.readString(),
        last_name = parcel.readString(),
        email = parcel.readString()
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(first_name)
        parcel.writeString(last_name)
        parcel.writeString(email)
    }

    companion object CREATOR : Parcelable.Creator<Assessor> {
        override fun createFromParcel(parcel: Parcel): Assessor = Assessor(parcel)
        override fun newArray(size: Int): Array<Assessor?> = arrayOfNulls(size)
    }
}
