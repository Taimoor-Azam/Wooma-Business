package com.wooma.business.model

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
)