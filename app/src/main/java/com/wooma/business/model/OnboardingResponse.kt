package com.wooma.business.model

data class OnboardingResponse(
    val success: Boolean,
    val message: String,
    val user: User,
    val access_token: String,
    val refresh_token: String,
    val token_type: String,
    val expires_in: Int
)

data class User(
    val id: String,
    val email: String,
    val first_name: String?,
    val last_name: String?,
    val is_onboarded: Boolean,
    val tenant_id: String,
    val company_name: String,
    var access_token: String,
    var role: String?,
    var refresh_token: String,
    val profile_image_url: String? = "",
)