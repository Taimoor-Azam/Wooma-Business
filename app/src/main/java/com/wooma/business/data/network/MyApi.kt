package com.wooma.business.data.network

import com.wooma.business.model.AddDetectorRequest
import com.wooma.business.model.AddKeyRequest
import com.wooma.business.model.AddMeterRequest
import com.wooma.business.model.AddNewRoomItemsRequest
import com.wooma.business.model.AddNewRoomsRequest
import com.wooma.business.model.AddReportResponse
import com.wooma.business.model.ApiResponse
import com.wooma.business.model.AssessorUsers
import com.wooma.business.model.AttachmentRecord
import com.wooma.business.model.ChangeAssessor
import com.wooma.business.model.ChangeDateRequest
import com.wooma.business.model.CheckListActiveStatus
import com.wooma.business.model.ChecklistData
import com.wooma.business.model.ChecklistAnswerAttachmentRequest
import com.wooma.business.model.ChecklistAnswerAttachmentResponse
import com.wooma.business.model.ChecklistStatusRequest
import com.wooma.business.model.UpsertFieldAnswerRequest
import com.wooma.business.model.UpsertQuestionAnswerRequest
import com.wooma.business.model.CompleteReportRequest
import com.wooma.business.model.CreateAttachmentRequest
import com.wooma.business.model.CreateDuplicateReport
import com.wooma.business.model.CreateReportFromPreviousRequest
import com.wooma.business.model.CreateReportRequest
import com.wooma.business.model.DetectorItem
import com.wooma.business.model.ExtendTimeRequest
import com.wooma.business.model.KeyItem
import com.wooma.business.model.Meter
import com.wooma.business.model.OnboardingResponse
import com.wooma.business.model.PostalAddress
import com.wooma.business.model.PresignedUrlResponse
import com.wooma.business.model.PropertiesRequest
import com.wooma.business.model.Property
import com.wooma.business.model.PropertyDetailResponse
import com.wooma.business.model.ReportData
import com.wooma.business.model.ReportTypeResponse
import com.wooma.business.model.RoomsResponse
import com.wooma.business.model.SendOtpData
import com.wooma.business.model.SendOtpRequest
import com.wooma.business.model.TemplateData
import com.wooma.business.model.TenantPropertiesWrapper
import com.wooma.business.model.TenantReview
import com.wooma.business.model.TenantsRequest
import com.wooma.business.model.UpdateRoomItemRequest
import com.wooma.business.model.UpdateTenantReviewRequest
import com.wooma.business.model.UpsertRoomInspectionRequest
import com.wooma.business.model.UserOnBoardRequest
import com.wooma.business.model.VerifyOTPRequest
import com.wooma.business.model.VerifyOtpData
import com.wooma.business.model.ChangeReportDateRequest
import com.wooma.business.model.changeReportType
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface MyApi {

    @POST("/api/v1/auth/wb/send-otp")
    fun sendOTP(
        @Body request: SendOtpRequest
    ): Call<ApiResponse<SendOtpData>>

    @POST("/api/v1/auth/wb/verify-otp")
    fun verifyOTP(
        @Body request: VerifyOTPRequest
    ): Call<ApiResponse<VerifyOtpData>>

    @POST("/api/v1/auth/wb/onboard")
    fun onBoardUser(
        @Body request: UserOnBoardRequest
    ): Call<ApiResponse<OnboardingResponse>>

    @GET("/api/v1/tenant-properties")
    fun getPropertiesList(
        @QueryMap queryMap: Map<String, @JvmSuppressWildcards Any>
    ): Call<ApiResponse<TenantPropertiesWrapper>>

    @GET("/api/v1/report-types")
    fun getReportTypes(): Call<ApiResponse<ReportTypeResponse>>

    @GET("/api/v1/report-templates")
    fun getReportTemplates(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
        @Query("is_active") is_active: Boolean = true
    ): Call<ApiResponse<TemplateData>>

    @GET("/api/v1/tenant-properties/{id}")
    fun getPropertyById(
        @Path("id") id: String
    ): Call<ApiResponse<PropertyDetailResponse>>

    @GET("/api/v1/tenant-reports/{id}")
    fun getReportById(
        @Path("id") id: String,
        @Query("include_rooms") include_rooms: Boolean,
        @Query("include_counts") include_counts: Boolean,
    ): Call<ApiResponse<ReportData>>

    @GET("/api/v1/postcodes/{postcode}")
    fun getPostCodes(
        @Path("postcode") postcode: String,
    ): Call<ApiResponse<ArrayList<PostalAddress>>>

    @DELETE("/api/v1/tenant-reports/{id}")
    fun archiveReport(
        @Path("id") id: String,
    ): Call<ApiResponse<ReportData>>

    @GET("/api/v1/tenant-reports/{report_id}/tenant-reviews")
    fun getTenantsForReportReview(
        @Path("report_id") report_id: String,
        @Query("is_agreed") is_agreed: Boolean = false,
    ): Call<ApiResponse<ArrayList<TenantReview>>>

    @GET("/api/v1/tenant-users")
    fun getAssessors(
        @Query("is_active") is_active: Boolean = true,
    ): Call<ApiResponse<ArrayList<AssessorUsers>>>

    @GET("/api/v1/tenant-report/{report_id}/rooms/{id}")
    fun getRoomById(
        @Path("id") id: String,
        @Path("report_id") report_id: String,
        @Query("include_items") include_items: Boolean,
        @Query("include_room_inspections") include_room_inspections: Boolean,
        @Query("include_attachments") include_attachments: Boolean = false,
    ): Call<ApiResponse<RoomsResponse>>


    @GET("/api/v1/tenant-report/{report_id}/meters")
    fun getReportMeters(
        @Path("report_id") report_id: String,
        @Query("include_attachments") include_attachments: Boolean,
    ): Call<ApiResponse<ArrayList<Meter>>>


    @GET("/api/v1/tenant-report/{report_id}/keys")
    fun getReportKeys(
        @Path("report_id") report_id: String,
        @Query("include_attachments") include_attachments: Boolean,
    ): Call<ApiResponse<ArrayList<KeyItem>>>

    @GET("/api/v1/report-checklists/{id}")
    fun getReportCheckList(
        @Path("id") id: String,
        @Query("include_answer_attachments") include_attachments: Boolean,
        @Query("schema") schema: String = "wooma",
    ): Call<ApiResponse<ChecklistData>>

    @GET("/api/v1/tenant-reports/{report_id}/report_checklist")
    fun getReportCheckListStatus(
        @Path("report_id") report_id: String
    ): Call<ApiResponse<CheckListActiveStatus>>

    @GET("/api/v1/tenant-report/{report_id}/detector")
    fun getReportDetector(
        @Path("report_id") report_id: String,
        @Query("include_attachments") include_attachments: Boolean,
    ): Call<ApiResponse<ArrayList<DetectorItem>>>

    @POST("/api/v1/tenant-report/{id}/rooms/bulk")
    fun addRomToReport(
        @Path("id") id: String,
        @Body request: AddNewRoomsRequest
    ): Call<ApiResponse<ReportData>>

    @PATCH("/api/v1/tenant-reports/{id}/change-report-type")
    fun changeReportType(
        @Path("id") id: String,
        @Body request: changeReportType
    ): Call<ApiResponse<ReportData>>

    @PATCH("/api/v1/tenant-reports/{id}/change-report-date")
    fun changeReportDate(
        @Path("id") id: String,
        @Body request: ChangeReportDateRequest
    ): Call<ApiResponse<ReportData>>

    @PATCH("/api/v1/tenant-reports/{id}/change-assessor")
    fun changeAssessor(
        @Path("id") id: String,
        @Body request: ChangeAssessor
    ): Call<ApiResponse<ReportData>>

    @PATCH("/api/v1/tenant-reports/{id}")
    fun changeDate(
        @Path("id") id: String,
        @Body request: ChangeDateRequest
    ): Call<ApiResponse<ReportData>>

    @POST("/api/v1/tenant-reports/{id}/extend-expiry")
    fun extendReviewTime(
        @Path("id") id: String,
        @Body request: ExtendTimeRequest
    ): Call<ApiResponse<ReportData>>

    @POST("/api/v1/tenant-report/{report_id}/meters")
    fun addNewMeter(
        @Path("report_id") report_id: String,
        @Body request: AddMeterRequest
    ): Call<ApiResponse<ReportData>>

    @POST("/api/v1/tenant-reports/{id}/complete-report")
    fun completeReport(
        @Path("id") id: String,
        @Body request: CompleteReportRequest
    ): Call<ApiResponse<ReportData>>

    @POST("/api/v1/tenant-reports/{id}/send-to-tenants")
    fun sendReportForApproval(
        @Path("id") id: String,
        @Body request: TenantsRequest
    ): Call<ApiResponse<ArrayList<ReportData>>>

    @POST("/api/v1/tenant-report/{report_id}/keys")
    fun addNewKey(
        @Path("report_id") report_id: String,
        @Body request: AddKeyRequest
    ): Call<ApiResponse<ReportData>>

    @POST("/api/v1/tenant-report/{report_id}/detector")
    fun addNewDetector(
        @Path("report_id") report_id: String,
        @Body request: AddDetectorRequest
    ): Call<ApiResponse<ReportData>>

    @PUT("/api/v1/tenant-report/{report_id}/meters/{id}")
    fun updateMeter(
        @Path("report_id") report_id: String,
        @Path("id") id: String,
        @Body request: AddMeterRequest
    ): Call<ApiResponse<ReportData>>

    @PUT("/api/v1/tenant-report/{report_id}/keys/{id}")
    fun updateKey(
        @Path("report_id") report_id: String,
        @Path("id") id: String,
        @Body request: AddKeyRequest
    ): Call<ApiResponse<ReportData>>

    @PUT("/api/v1/tenant-report/{report_id}/detector/{id}")
    fun updateDetector(
        @Path("report_id") report_id: String,
        @Path("id") id: String,
        @Body request: AddDetectorRequest
    ): Call<ApiResponse<ReportData>>

    @DELETE("/api/v1/tenant-report/{report_id}/meters/{id}")
    fun deleteMeter(
        @Path("report_id") report_id: String,
        @Path("id") id: String
    ): Call<ApiResponse<ReportData>>

    @DELETE("/api/v1/tenant-report/{report_id}/keys/{id}")
    fun deleteKey(
        @Path("report_id") report_id: String,
        @Path("id") id: String
    ): Call<ApiResponse<ReportData>>

    @DELETE("/api/v1/tenant-report/{report_id}/detector/{id}")
    fun deleteDetector(
        @Path("report_id") report_id: String,
        @Path("id") id: String
    ): Call<ApiResponse<ReportData>>

    @POST("/api/v1/tenant-report/{report_id}/room/{room_id}/room-items/bulk")
    fun addRoomItemsToReport(
        @Path("report_id") report_id: String,
        @Path("room_id") room_id: String,
        @Body request: AddNewRoomItemsRequest
    ): Call<ApiResponse<ReportData>>

    @DELETE("/api/v1/tenant-report/{report_id}/room/{room_id}/room-items/{id}")
    fun deleteRoomItem(
        @Path("report_id") report_id: String,
        @Path("room_id") room_id: String,
        @Path("id") id: String,
    ): Call<ApiResponse<ReportData>>

    @PUT("/api/v1/tenant-report/{report_id}/room/{room_id}/room-items/{id}")
    fun updateRoomItem(
        @Path("report_id") report_id: String,
        @Path("room_id") room_id: String,
        @Path("id") id: String,
        @Body request: UpdateRoomItemRequest
    ): Call<ApiResponse<ReportData>>

    @POST("/api/v1/tenant-report/{id}/room/{roomId}/room-items/bulk")
    fun addItemToRoom(
        @Path("id") id: String,
        @Path("roomId") roomId: String,
        @Body request: AddNewRoomsRequest
    ): Call<ApiResponse<ReportData>>

    @POST("/api/v1/tenant-properties")
    fun createProperty(
        @Body request: PropertiesRequest
    ): Call<ApiResponse<Property>>

    @POST("/api/v1/tenant-reports")
    fun createReport(
        @Body request: CreateReportRequest?
    ): Call<ApiResponse<AddReportResponse>>

    @POST("/api/v1/tenant-reports")
    fun createReport(
        @Body request: CreateReportFromPreviousRequest
    ): Call<ApiResponse<AddReportResponse>>

    @POST("/api/v1/tenant-reports")
    fun createDuplicateReport(
        @Body request: CreateDuplicateReport
    ): Call<ApiResponse<AddReportResponse>>

    @PUT("/api/v1/tenant-properties/{id}")
    fun updateProperty(
        @Path("id") id: String,
        @Body request: PropertiesRequest
    ): Call<ApiResponse<Property>>

    @PATCH("/api/v1/tenant-properties/{id}/activate")
    fun restoreProperty(
        @Path("id") id: String
    ): Call<ApiResponse<Property>>

    @PATCH("/api/v1/tenant-properties/{id}/deactivate")
    fun archiveProperty(
        @Path("id") id: String
    ): Call<ApiResponse<Property>>

    // Attachments
    @GET("/api/v1/attachments/presigned-url")
    fun getPresignedUrl(
        @Query("filename") filename: String,
        @Query("mimeType") mimeType: String
    ): Call<ApiResponse<PresignedUrlResponse>>

    @POST("/api/v1/attachments")
    fun createAttachment(
        @Body request: CreateAttachmentRequest
    ): Call<ApiResponse<AttachmentRecord>>

    @DELETE("/api/v1/attachments/{id}")
    fun deleteAttachment(
        @Path("id") id: String
    ): Call<ApiResponse<Any>>

    // Room management
    @DELETE("/api/v1/tenant-report/{report_id}/rooms/{id}")
    fun deleteRoom(
        @Path("report_id") reportId: String,
        @Path("id") roomId: String
    ): Call<ApiResponse<Any>>

    // Checklist status
    @PATCH("/api/v1/report-checklists/{id}/status")
    fun updateChecklistStatus(
        @Path("id") id: String,
        @Body request: ChecklistStatusRequest
    ): Call<ApiResponse<Any>>

    @PATCH("/api/v1/tenant-reports/{id}/tenant-reviews/{tenant_review_id}")
    fun updateTenantReview(
        @Path("id") reportId: String,
        @Path("tenant_review_id") tenantReviewId: String,
        @Body request: UpdateTenantReviewRequest
    ): Call<ApiResponse<TenantReview>>

    @DELETE("/api/v1/tenant-reports/{id}/tenant-reviews/{tenant_review_id}")
    fun deleteTenantReview(
        @Path("id") reportId: String,
        @Path("tenant_review_id") tenantReviewId: String
    ): Call<ApiResponse<Any>>

    @POST("/api/v1/room-inspections/upsert")
    fun upsertRoomInspection(
        @Body request: UpsertRoomInspectionRequest
    ): Call<ApiResponse<Any>>

    @POST("/api/v1/report-checklist-question-answer")
    fun upsertQuestionAnswer(
        @Body request: UpsertQuestionAnswerRequest
    ): Call<ApiResponse<Any>>

    @POST("/api/v1/report-checklist-field-answer")
    fun upsertFieldAnswer(
        @Body request: UpsertFieldAnswerRequest
    ): Call<ApiResponse<Any>>

    @POST("/api/v1/report-checklist-question-answer-attachment")
    fun findOrCreateAnswerAttachment(
        @Body request: ChecklistAnswerAttachmentRequest
    ): Call<ApiResponse<ChecklistAnswerAttachmentResponse>>
}