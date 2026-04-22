package com.wooma.model

import android.os.Parcel
import android.os.Parcelable

data class ReportTypeResponse(
    val data: ArrayList<ReportType>,
    val total: Int,
    val schema: String
)

data class ReportType(
    val id: String,
    val type_code: String,
    val display_name: String,
    val description: String,
    val created_at: String,
    val updated_at: String
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(type_code)
        parcel.writeString(display_name)
        parcel.writeString(description)
        parcel.writeString(created_at)
        parcel.writeString(updated_at)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ReportType> {
        override fun createFromParcel(parcel: Parcel): ReportType {
            return ReportType(parcel)
        }

        override fun newArray(size: Int): Array<ReportType?> {
            return arrayOfNulls(size)
        }
    }
}

data class changeReportType(
    val report_type_id: String
)
data class ChangeAssessor(
    val assigned_to: String
)

data class ChangeReportDateRequest(
    val inspection_date: String
)
