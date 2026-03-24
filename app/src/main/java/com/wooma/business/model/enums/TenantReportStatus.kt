package com.wooma.business.model.enums

enum class TenantReportStatus(val value: String) {
    IN_PROGRESS("in_progress"),
    COMPLETED("completed"),
    HISTORICAL("historical"),
    TENANT_REVIEW("tenant_review")
}