package com.wooma.business.model

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
)

data class changeReportType(
    val report_type_id: String
)
data class ChangeAssessor(
    val assigned_to: String
)

data class ChangeReportDateRequest(
    val inspection_date: String
)
