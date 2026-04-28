package com.wooma.data.repository

import android.content.Context
import com.wooma.data.local.WoomaDatabase
import com.wooma.data.local.dao.PropertyDao
import com.wooma.data.local.entity.PropertyEntity
import com.wooma.data.local.mapper.toEntity
import com.wooma.data.network.RetrofitClient
import com.wooma.model.Property
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PropertyRepository(context: Context) {

    private val dao: PropertyDao = WoomaDatabase.getInstance(context).propertyDao()
    private val api = RetrofitClient.getApi(context)

    fun observeActiveProperties(): Flow<List<PropertyEntity>> = dao.observeActiveProperties()

    fun observeArchivedProperties(): Flow<List<PropertyEntity>> = dao.observeArchivedProperties()

    fun observeById(id: String): Flow<PropertyEntity?> = dao.observeById(id)

    suspend fun getById(id: String): PropertyEntity? = dao.getById(id)

    suspend fun refreshActiveProperties() = withContext(Dispatchers.IO) {
        val r = api.getPropertiesList(
            mapOf("page" to 1, "limit" to 100, "is_active" to true)
        ).execute()
        if (r.isSuccessful) {
            r.body()?.data?.data?.map { it.toEntity() }?.let { dao.upsertAll(it) }
        }
    }

    suspend fun refreshArchivedProperties() = withContext(Dispatchers.IO) {
        val r = api.getPropertiesList(
            mapOf("page" to 1, "limit" to 100, "is_active" to false)
        ).execute()
        if (r.isSuccessful) {
            r.body()?.data?.data?.map { it.toEntity() }?.let { dao.upsertAll(it) }
        }
    }

    suspend fun upsertFromServer(property: Property) {
        dao.upsert(property.toEntity())
    }
}
