package com.wooma.model

data class CreateReportFromPreviousRequest(
    val property_id: String,
    val report_type_id: String,
    val report_id: String
)

data class CreateReportRequest(
    val property_id: String,
    val report_type_id: String,
    val rooms: List<SendRequestRoom>
)

data class SendRequestRoom(
    val name: String,
    val items: List<String>
)

data class CreateDuplicateReport(
    val property_id: String,
    val include_images: Boolean? = null,
    val include_descriptions: Boolean?,
    val report_id: String
)