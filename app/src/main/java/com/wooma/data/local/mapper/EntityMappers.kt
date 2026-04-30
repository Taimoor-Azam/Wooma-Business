package com.wooma.data.local.mapper

import com.wooma.data.local.entity.AssessorEntity
import com.wooma.data.local.entity.ChecklistEntity
import com.wooma.data.local.entity.ChecklistInfoFieldEntity
import com.wooma.data.local.entity.ChecklistQuestionEntity
import com.wooma.data.local.entity.DetectorEntity
import com.wooma.data.local.entity.KeyEntity
import com.wooma.data.local.entity.MeterEntity
import com.wooma.data.local.entity.PropertyEntity
import com.wooma.data.local.entity.ReportEntity
import com.wooma.data.local.entity.ReportTypeEntity
import com.wooma.data.local.entity.RoomItemEntity
import com.wooma.data.local.entity.SyncStatus
import com.wooma.data.local.entity.TemplateEntity
import com.wooma.model.Assessor
import com.wooma.model.AssessorUsers
import com.wooma.model.Checklist
import com.wooma.model.DetectorItem
import com.wooma.model.InfoField
import com.wooma.model.KeyItem
import com.wooma.model.Meter
import com.wooma.model.Property
import com.wooma.model.PropertyDetailResponse
import com.wooma.model.PropertyReportType
import com.wooma.model.Question
import com.wooma.model.Report
import com.wooma.model.ReportType
import com.wooma.model.RoomItem
import com.wooma.model.Template

// ── Property ──────────────────────────────────────────────────────────────────

fun Property.toEntity(): PropertyEntity = PropertyEntity(
    id = id ?: "",
    serverId = id,
    createdBy = createdBy,
    address = address ?: "",
    addressLine2 = addressLine2,
    city = city ?: "",
    postcode = postcode ?: "",
    country = country,
    propertyType = propertyType,
    isActive = isActive ?: true,
    noOfReports = noOfReports ?: 0,
    lastActivity = lastActivity,
    createdAt = createdAt ?: "",
    updatedAt = updatedAt ?: "",
    syncStatus = SyncStatus.SYNCED
)

fun PropertyEntity.toProperty(): Property = Property(
    id = serverId ?: id,
    createdBy = createdBy,
    address = address,
    addressLine2 = addressLine2,
    city = city,
    postcode = postcode,
    country = country,
    propertyType = propertyType,
    isActive = isActive,
    noOfReports = noOfReports,
    lastActivity = lastActivity,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun PropertyDetailResponse.toPropertyEntity(): PropertyEntity = PropertyEntity(
    id = id,
    serverId = id,
    createdBy = created_by,
    address = address,
    addressLine2 = address_line_2,
    city = city,
    postcode = postcode,
    country = country,
    propertyType = property_type,
    isActive = is_active,
    noOfReports = 0,
    lastActivity = null,
    createdAt = created_at,
    updatedAt = updated_at,
    syncStatus = SyncStatus.SYNCED
)

// ── Report ────────────────────────────────────────────────────────────────────

fun Report.toEntity(propertyId: String): ReportEntity = ReportEntity(
    id = id,
    serverId = id,
    propertyId = propertyId,
    reportTypeId = report_type?.id ?: "",
    reportTypeCode = report_type?.type_code ?: "",
    reportTypeDisplayName = report_type?.display_name ?: "",
    status = status,
    assessorId = assessor.id,
    assessorFirstName = assessor.first_name,
    assessorLastName = assessor.last_name,
    assessorEmail = assessor.email,
    completionDate = completion_date ?: "",
    isActive = is_active,
    isDeleted = is_deleted,
    createdAt = created_at,
    updatedAt = updated_at,
    syncStatus = SyncStatus.SYNCED
)

// ── Meter ─────────────────────────────────────────────────────────────────────

fun Meter.toEntity(reportId: String): MeterEntity = MeterEntity(
    id = id,
    serverId = id,
    reportId = reportId,
    name = name,
    reading = reading,
    location = location,
    serialNumber = serial_number,
    displayOrder = display_order,
    isDeleted = is_deleted,
    createdAt = created_at,
    updatedAt = updated_at,
    syncStatus = SyncStatus.SYNCED
)

fun MeterEntity.toMeter(): Meter = Meter(
    id = serverId ?: id,
    is_active = !isDeleted,
    is_deleted = isDeleted,
    created_at = createdAt,
    updated_at = updatedAt,
    report_id = reportId,
    name = name,
    reading = reading,
    location = location,
    serial_number = serialNumber,
    display_order = displayOrder ?: "",
    attachments = emptyList()
)

// ── Key ───────────────────────────────────────────────────────────────────────

fun KeyItem.toEntity(reportId: String): KeyEntity = KeyEntity(
    id = id,
    serverId = id,
    reportId = reportId,
    name = name,
    noOfKeys = no_of_keys,
    note = note,
    displayOrder = display_order,
    isDeleted = is_deleted,
    createdAt = created_at,
    updatedAt = updated_at,
    syncStatus = SyncStatus.SYNCED
)

fun KeyEntity.toKeyItem(): KeyItem = KeyItem(
    id = serverId ?: id,
    name = name,
    report_id = reportId,
    no_of_keys = noOfKeys,
    note = note,
    display_order = displayOrder ?: "",
    is_deleted = isDeleted,
    created_at = createdAt,
    updated_at = updatedAt,
    attachments = emptyList()
)

// ── Detector ──────────────────────────────────────────────────────────────────

fun DetectorItem.toEntity(reportId: String): DetectorEntity = DetectorEntity(
    id = id,
    serverId = id,
    reportId = reportId,
    name = name,
    location = location,
    note = note,
    displayOrder = display_order,
    isDeleted = is_deleted,
    createdAt = created_at,
    updatedAt = updated_at,
    syncStatus = SyncStatus.SYNCED
)

fun DetectorEntity.toDetectorItem(): DetectorItem = DetectorItem(
    id = serverId ?: id,
    name = name,
    report_id = reportId,
    location = location,
    note = note,
    display_order = displayOrder ?: "",
    is_deleted = isDeleted,
    created_at = createdAt,
    updated_at = updatedAt,
    attachments = emptyList()
)

// ── Report ────────────────────────────────────────────────────────────────────

fun ReportEntity.toReport(): Report = Report(
    id = serverId ?: id,
    report_type = if (reportTypeId.isNotEmpty()) PropertyReportType(
        id = reportTypeId,
        display_name = reportTypeDisplayName.ifEmpty { null },
        type_code = reportTypeCode
    ) else null,
    status = status,
    is_active = isActive,
    is_deleted = isDeleted,
    assessor = Assessor(
        id = assessorId,
        first_name = assessorFirstName,
        last_name = assessorLastName,
        email = assessorEmail
    ),
    completion_date = completionDate.ifEmpty { null },
    created_at = createdAt,
    updated_at = updatedAt
)

// ── Room Item ─────────────────────────────────────────────────────────────────

fun RoomItem.toEntity(roomId: String): RoomItemEntity = RoomItemEntity(
    id = id ?: "",
    serverId = id,
    roomId = roomId,
    name = name ?: "",
    generalCondition = general_condition,
    generalCleanliness = general_cleanliness,
    description = description,
    note = note,
    displayOrder = display_order,
    isActive = is_active ?: true,
    isDeleted = is_deleted ?: false,
    createdAt = created_at ?: "",
    updatedAt = updated_at ?: "",
    syncStatus = SyncStatus.SYNCED
)

fun RoomItemEntity.toRoomItem(): RoomItem = RoomItem(
    id = serverId ?: id,
    is_active = isActive,
    is_deleted = isDeleted,
    created_at = createdAt,
    updated_at = updatedAt,
    room_id = roomId,
    name = name,
    general_condition = generalCondition,
    general_cleanliness = generalCleanliness,
    description = description,
    note = note,
    display_order = displayOrder,
    attachments = emptyList()
)

// ── Checklist ─────────────────────────────────────────────────────────────────

fun Checklist.toEntity(reportId: String): ChecklistEntity = ChecklistEntity(
    id = id,
    reportId = reportId,
    name = name,
    isActive = is_active,
    syncStatus = SyncStatus.SYNCED
)

// ── ChecklistQuestionEntity → Question ────────────────────────────────────────

fun ChecklistQuestionEntity.toQuestion(): Question = Question(
    checklist_question_id = checklistQuestionId,
    text = text,
    type = type,
    displayOrder = displayOrder,
    is_required = isRequired,
    checklist_question_answer_id = answerId,
    answer_option = answerOption,
    answer_text = answerText,
    note = note,
    original_note = note,
    checklist_question_answer_attachment = null
)

// ── ChecklistInfoFieldEntity → InfoField ──────────────────────────────────────

fun ChecklistInfoFieldEntity.toInfoField(): InfoField = InfoField(
    checklist_field_id = fieldId,
    label = label,
    type = type,
    is_required = isRequired,
    checklist_info_field_answer_id = answerId,
    answer_text = answerText,
    original_answer_text = answerText
)

// ── AssessorUsers → AssessorEntity ────────────────────────────────────────────

fun AssessorUsers.toEntity(): AssessorEntity = AssessorEntity(
    id = id,
    userId = user_id,
    email = email,
    firstName = first_name,
    lastName = last_name,
    role = role,
    isActive = true
)

// ── ReportType → ReportTypeEntity ────────────────────────────────────────────

fun ReportType.toEntity(): ReportTypeEntity = ReportTypeEntity(
    id = id,
    typeCode = type_code,
    displayName = display_name,
    description = description,
    createdAt = created_at,
    updatedAt = updated_at
)

// ── Template → TemplateEntity ─────────────────────────────────────────────────

fun Template.toEntity(): TemplateEntity = TemplateEntity(
    id = id,
    name = name,
    description = description,
    isActive = isActive,
    isDeleted = isDeleted
)
