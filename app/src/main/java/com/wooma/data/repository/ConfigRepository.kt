package com.wooma.data.repository

import android.content.Context
import com.wooma.data.local.WoomaDatabase
import com.wooma.data.local.entity.AssessorEntity
import com.wooma.data.local.entity.ReportTypeEntity
import com.wooma.data.local.entity.TemplateEntity
import com.wooma.data.local.mapper.toEntity
import com.wooma.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ConfigRepository(private val ctx: Context) {

    private val db = WoomaDatabase.getInstance(ctx)
    private val api by lazy { RetrofitClient.getApi(ctx) }

    fun observeAssessors(): Flow<List<AssessorEntity>> =
        db.assessorDao().observeActive()

    fun observeReportTypes(): Flow<List<ReportTypeEntity>> =
        db.reportTypeDao().observeAll()

    fun observeTemplates(): Flow<List<TemplateEntity>> =
        db.templateDao().observeActive()

    suspend fun seedReferenceData() = withContext(Dispatchers.IO) {
        try {
            val typesResp = api.getReportTypes().execute()
            typesResp.body()?.data?.data?.let { types ->
                db.reportTypeDao().upsertAll(types.map { it.toEntity() })
            }
        } catch (_: Exception) {}

        try {
            val assessorsResp = api.getAssessors().execute()
            assessorsResp.body()?.data?.let { list ->
                db.assessorDao().upsertAll(list.map { it.toEntity() })
            }
        } catch (_: Exception) {}

        try {
            val templatesResp = api.getReportTemplates().execute()
            templatesResp.body()?.data?.templates?.let { list ->
                db.templateDao().upsertTemplates(list.map { it.toEntity() })
            }
        } catch (_: Exception) {}
    }
}
