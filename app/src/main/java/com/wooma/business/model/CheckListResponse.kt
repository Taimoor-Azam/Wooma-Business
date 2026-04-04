package com.wooma.business.model

import android.os.Parcel
import android.os.Parcelable

data class ChecklistData(
    val id: String,
    val checklist_id: String,
    val info_fields: ArrayList<InfoField>,
    val questions: ArrayList<Question>
)

data class InfoField(
    val checklist_field_id: String?,
    val label: String,
    val type: String,
    val is_required: Boolean,
    val checklist_info_field_answer_id: String?,
    val answer_text: String?
)

data class Question(
    val checklist_question_id: String?,
    val text: String,
    val type: String,
    val displayOrder: Int,
    val is_required: Boolean,
    val checklist_question_answer_id: String?,
    val answer_option: String?,
    val answer_text: String?,
    val note: String?,
    val checklist_question_answer_attachment: AnswerAttachment?
) : Parcelable {

    constructor(parcel: Parcel) : this(
        checklist_question_id = parcel.readString(),
        text = parcel.readString() ?: "",
        type = parcel.readString() ?: "",
        displayOrder = parcel.readInt(),
        is_required = parcel.readByte() != 0.toByte(),
        checklist_question_answer_id = parcel.readString(),
        answer_option = parcel.readString(),
        answer_text = parcel.readString(),
        note = parcel.readString(),
        checklist_question_answer_attachment = parcel.readParcelable(AnswerAttachment::class.java.classLoader)
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(checklist_question_id)
        parcel.writeString(text)
        parcel.writeString(type)
        parcel.writeInt(displayOrder)
        parcel.writeByte(if (is_required) 1 else 0)
        parcel.writeString(checklist_question_answer_id)
        parcel.writeString(answer_option)
        parcel.writeString(answer_text)
        parcel.writeString(note)
        parcel.writeParcelable(checklist_question_answer_attachment, flags)
    }

    companion object CREATOR : Parcelable.Creator<Question> {
        override fun createFromParcel(parcel: Parcel): Question = Question(parcel)
        override fun newArray(size: Int): Array<Question?> = arrayOfNulls(size)
    }
}

data class AnswerAttachment(
    val id: String,
    val attachments: ArrayList<Attachment>?
) : Parcelable {

    constructor(parcel: Parcel) : this(
        id = parcel.readString() ?: "",
        attachments = parcel.createTypedArrayList(Attachment.CREATOR)
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeTypedList(attachments)
    }

    companion object CREATOR : Parcelable.Creator<AnswerAttachment> {
        override fun createFromParcel(parcel: Parcel): AnswerAttachment = AnswerAttachment(parcel)
        override fun newArray(size: Int): Array<AnswerAttachment?> = arrayOfNulls(size)
    }
}

data class CheckListActiveStatus(
    val reportId: String,
    val checklists: ArrayList<Checklist>?
)

data class Checklist(
    val id: String,
    val name: String,
    val is_active: Boolean
)
