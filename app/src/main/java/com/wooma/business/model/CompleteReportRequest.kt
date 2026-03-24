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
