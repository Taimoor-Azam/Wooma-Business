package com.wooma.model

import android.os.Parcel
import android.os.Parcelable

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
) : Parcelable {

    constructor(parcel: Parcel) : this(
        id = parcel.readString() ?: "",
        is_active = parcel.readByte() != 0.toByte(),
        is_deleted = parcel.readByte() != 0.toByte(),
        created_at = parcel.readString() ?: "",
        updated_at = parcel.readString() ?: "",
        report_id = parcel.readString() ?: "",
        name = parcel.readString() ?: "",
        reading = parcel.readString(),
        location = parcel.readString(),
        serial_number = parcel.readString(),
        display_order = parcel.readString() ?: "",
        attachments = parcel.createTypedArrayList(OtherItemsAttachment.CREATOR) ?: emptyList()
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeByte(if (is_active) 1 else 0)
        parcel.writeByte(if (is_deleted) 1 else 0)
        parcel.writeString(created_at)
        parcel.writeString(updated_at)
        parcel.writeString(report_id)
        parcel.writeString(name)
        parcel.writeString(reading)
        parcel.writeString(location)
        parcel.writeString(serial_number)
        parcel.writeString(display_order)
        parcel.writeTypedList(attachments)
    }

    companion object CREATOR : Parcelable.Creator<Meter> {
        override fun createFromParcel(parcel: Parcel): Meter = Meter(parcel)
        override fun newArray(size: Int): Array<Meter?> = arrayOfNulls(size)
    }
}

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
) : Parcelable {

    constructor(parcel: Parcel) : this(
        id = parcel.readString() ?: "",
        is_active = parcel.readByte() != 0.toByte(),
        is_deleted = parcel.readByte() != 0.toByte(),
        created_at = parcel.readString() ?: "",
        updated_at = parcel.readString() ?: "",
        entityId = parcel.readString() ?: "",
        entityType = parcel.readString() ?: "",
        originalName = parcel.readString() ?: "",
        storageKey = parcel.readString() ?: "",
        link = parcel.readString(),
        mimeType = parcel.readString() ?: "",
        fileSize = parcel.readString() ?: ""
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeByte(if (is_active) 1 else 0)
        parcel.writeByte(if (is_deleted) 1 else 0)
        parcel.writeString(created_at)
        parcel.writeString(updated_at)
        parcel.writeString(entityId)
        parcel.writeString(entityType)
        parcel.writeString(originalName)
        parcel.writeString(storageKey)
        parcel.writeString(link)
        parcel.writeString(mimeType)
        parcel.writeString(fileSize)
    }

    companion object CREATOR : Parcelable.Creator<OtherItemsAttachment> {
        override fun createFromParcel(parcel: Parcel): OtherItemsAttachment = OtherItemsAttachment(parcel)
        override fun newArray(size: Int): Array<OtherItemsAttachment?> = arrayOfNulls(size)
    }
}


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

data class KeyItem(
    val id: String,
    val name: String,
    val report_id: String,
    val no_of_keys: Int? = 1,
    val note: String?,
    val display_order: String,
    val is_deleted: Boolean,
    val created_at: String,
    val updated_at: String,
    val attachments: List<OtherItemsAttachment>
) : Parcelable {

    constructor(parcel: Parcel) : this(
        id = parcel.readString() ?: "",
        name = parcel.readString() ?: "",
        report_id = parcel.readString() ?: "",
        no_of_keys = parcel.readValue(Int::class.java.classLoader) as? Int,
        note = parcel.readString(),
        display_order = parcel.readString() ?: "",
        is_deleted = parcel.readByte() != 0.toByte(),
        created_at = parcel.readString() ?: "",
        updated_at = parcel.readString() ?: "",
        attachments = parcel.createTypedArrayList(OtherItemsAttachment.CREATOR) ?: emptyList()
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(name)
        parcel.writeString(report_id)
        parcel.writeValue(no_of_keys)
        parcel.writeString(note)
        parcel.writeString(display_order)
        parcel.writeByte(if (is_deleted) 1 else 0)
        parcel.writeString(created_at)
        parcel.writeString(updated_at)
        parcel.writeTypedList(attachments)
    }

    companion object CREATOR : Parcelable.Creator<KeyItem> {
        override fun createFromParcel(parcel: Parcel): KeyItem = KeyItem(parcel)
        override fun newArray(size: Int): Array<KeyItem?> = arrayOfNulls(size)
    }
}


data class DetectorItem(
    val id: String,
    val name: String,
    val report_id: String,
    val location: String?,
    val note: String?,
    val display_order: String,
    val is_deleted: Boolean,
    val created_at: String,
    val updated_at: String,
    val attachments: List<OtherItemsAttachment>
) : Parcelable {

    constructor(parcel: Parcel) : this(
        id = parcel.readString() ?: "",
        name = parcel.readString() ?: "",
        report_id = parcel.readString() ?: "",
        location = parcel.readString(),
        note = parcel.readString(),
        display_order = parcel.readString() ?: "",
        is_deleted = parcel.readByte() != 0.toByte(),
        created_at = parcel.readString() ?: "",
        updated_at = parcel.readString() ?: "",
        attachments = parcel.createTypedArrayList(OtherItemsAttachment.CREATOR) ?: emptyList()
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(name)
        parcel.writeString(report_id)
        parcel.writeString(location)
        parcel.writeString(note)
        parcel.writeString(display_order)
        parcel.writeByte(if (is_deleted) 1 else 0)
        parcel.writeString(created_at)
        parcel.writeString(updated_at)
        parcel.writeTypedList(attachments)
    }

    companion object CREATOR : Parcelable.Creator<DetectorItem> {
        override fun createFromParcel(parcel: Parcel): DetectorItem = DetectorItem(parcel)
        override fun newArray(size: Int): Array<DetectorItem?> = arrayOfNulls(size)
    }
}
