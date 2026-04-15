package com.wooma.business.model

import com.google.gson.annotations.SerializedName

data class SendOtpResponse(
    val success: Boolean,
    val data: SendOtpData
)

data class SendOtpData(
    val message: String,
    val email: String,
    @SerializedName("user_exists")
    val userExists: Boolean
)

data class Metadata(
    @SerializedName("timestamp")
    val timestamp: String,
    @SerializedName("path")
    val path: String,
    @SerializedName("method")
    val method: String,
    @SerializedName("requestId")
    val requestId: String
)

data class SendOtpRequest(
    @SerializedName("email")
    val email: String
)

data class VerifyOTPRequest(
    val email: String,
    val code: String
)

data class VerifyOtpResponse(
    val success: Boolean,
    val data: VerifyOtpData,
    val metadata: Metadata
)

data class VerifyOtpData(
    val user: VerifyUser,
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("refresh_token")
    val refreshToken: String,
    @SerializedName("token_type")
    val tokenType: String,
    @SerializedName("expires_in")
    val expiresIn: Long
)

data class VerifyUser(
    val id: String,
    val email: String,
    @SerializedName("first_name")
    val firstName: String,
    @SerializedName("last_name")
    val lastName: String,
    @SerializedName("is_new_user")
    val isNewUser: Boolean,
    @SerializedName("is_onboarded")
    val isOnboarded: Boolean,

    @SerializedName("role")
    val role: String,
)

data class UserOnBoardRequest(
    val first_name: String,
    val last_name: String,
    val company_name: String,
    val phone_number: String
)