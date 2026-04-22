package com.wooma.model

data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val errors: String,
    val data: T,
)
