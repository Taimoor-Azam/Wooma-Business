package com.wooma.model

data class TenantResponse(
    val id: String?,
    val name: String?,
    val address: String?,
    val city: String?,
    val postcode: String?,
    val logo: String?,
    val delete_after: String?,
    val created_at: String?,
    val updated_at: String?
)
