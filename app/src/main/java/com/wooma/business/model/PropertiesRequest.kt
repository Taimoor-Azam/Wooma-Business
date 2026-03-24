package com.wooma.business.model

data class PropertiesRequest(
    var address: String,
    var address_line_2: String,
    var city: String,
    var postcode: String,
    var created_by: String?,
)