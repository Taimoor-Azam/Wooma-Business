package com.wooma.business.model

data class ErrorResponse(
    val status: Boolean,
    val error: ErrorMessage
)

data class ErrorMessage(
    val message: String,
)

/*data class OtpErrorResponse(
    val success: Boolean,
    val error: ErrorResponse,
    val timestamp: String,
    val path: String,
    val method: String
)

data class ErrorResponse(
    val code: String,
    val message: String,
    val details: ErrorDetails
)

data class ErrorDetails(
    val phone_number: String
)*/
