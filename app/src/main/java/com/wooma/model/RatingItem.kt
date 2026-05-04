package com.wooma.model

data class RatingItem(
    val type_code: String,
    val display_name: String,
    val description: String?,
    val is_default: Boolean,
    val display_order: String?
)

data class RatingsResponse(
    val condition: List<RatingItem>,
    val cleanliness: List<RatingItem>
)
