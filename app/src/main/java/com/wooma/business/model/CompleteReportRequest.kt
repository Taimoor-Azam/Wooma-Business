package com.wooma.business.model

data class CompleteReportRequest(
    var blank_spaces_count: Int
)

data class TenantsRequest(
    val tenants: ArrayList<Tenant>
)

data class Tenant(
    var first_name: String,
    var last_name: String,
    var mobile_number: String,
    var email_address: String
)

data class UpdateTenantReviewRequest(
    val first_name: String,
    val last_name: String,
    val mobile_number: String
)
