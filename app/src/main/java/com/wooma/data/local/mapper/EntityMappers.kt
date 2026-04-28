package com.wooma.data.local.mapper

import com.wooma.data.local.entity.PropertyEntity
import com.wooma.data.local.entity.ReportEntity
import com.wooma.data.local.entity.SyncStatus
import com.wooma.model.Assessor
import com.wooma.model.Property
import com.wooma.model.PropertyDetailResponse
import com.wooma.model.PropertyReportType
import com.wooma.model.Report

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
