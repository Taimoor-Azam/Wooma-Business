package com.wooma.data.repository

import android.content.Context
import com.wooma.data.local.WoomaDatabase
import com.wooma.data.local.dao.PropertyDao
import com.wooma.data.local.dao.ReportDao
import com.wooma.data.local.entity.ReportEntity
import com.wooma.data.local.mapper.toEntity
import com.wooma.data.local.mapper.toPropertyEntity
import com.wooma.data.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ReportRepository(context: Context) {

    private val reportDao: ReportDao = WoomaDatabase.getInstance(context).reportDao()
    private val propertyDao: PropertyDao = WoomaDatabase.getInstance(context).propertyDao()
    private val api = RetrofitClient.getApi(context)

    fun observeByProperty(propertyId: String): Flow<List<ReportEntity>> =
        reportDao.observeByProperty(propertyId)

    suspend fun hasData(propertyId: String): Boolean =
        withContext(Dispatchers.IO) { reportDao.countByProperty(propertyId) > 0 }

    suspend fun refreshByProperty(propertyId: String) = withContext(Dispatchers.IO) {
        val r = api.getPropertyById(propertyId).execute()
        if (r.isSuccessful) {
            val data = r.body()?.data ?: return@withContext
            propertyDao.upsert(data.toPropertyEntity())
            reportDao.upsertAll(data.reports.map { it.toEntity(propertyId) })
        }
    }
}
