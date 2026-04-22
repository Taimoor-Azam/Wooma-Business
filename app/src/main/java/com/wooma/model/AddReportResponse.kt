package com.wooma.model

import android.os.Parcel
import android.os.Parcelable

data class AddReportResponse(
    val report_id: String,
    val completion_date: String,
    val completion_percentage: String,
    val tenant_review_expiry: String?,
    val extend_review_expiry: String?,
    val report_type: ReportType,
    val property: Property,
    val assessor: Assessor,
    val status: String
) : Parcelable {
    constructor(parcel: Parcel) : this(
        report_id = parcel.readString() ?: "",
        completion_date = parcel.readString() ?: "",
        completion_percentage = parcel.readString() ?: "",
        tenant_review_expiry = parcel.readString(),
        extend_review_expiry = parcel.readString(),
        report_type = parcel.readParcelable(ReportType::class.java.classLoader)!!,
        property = parcel.readParcelable(Property::class.java.classLoader)!!,
        assessor = parcel.readParcelable(Assessor::class.java.classLoader)!!,
        status = parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(report_id)
        parcel.writeString(completion_date)
        parcel.writeString(completion_percentage)
        parcel.writeString(tenant_review_expiry)
        parcel.writeString(extend_review_expiry)
        parcel.writeParcelable(report_type, flags)
        parcel.writeParcelable(property, flags)
        parcel.writeParcelable(assessor, flags)
        parcel.writeString(status)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<AddReportResponse> {
        override fun createFromParcel(parcel: Parcel): AddReportResponse = AddReportResponse(parcel)
        override fun newArray(size: Int): Array<AddReportResponse?> = arrayOfNulls(size)
    }
}

data class ChangeDateRequest(
    val completion_date: String
)
