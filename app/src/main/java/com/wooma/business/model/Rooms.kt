package com.wooma.business.model

import android.os.Parcel
import android.os.Parcelable

data class Rooms(val address: String) : Parcelable {

    constructor(parcel: Parcel) : this(
        address = parcel.readString() ?: ""
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(address)
    }

    companion object CREATOR : Parcelable.Creator<Rooms> {
        override fun createFromParcel(parcel: Parcel): Rooms = Rooms(parcel)
        override fun newArray(size: Int): Array<Rooms?> = arrayOfNulls(size)
    }
}
