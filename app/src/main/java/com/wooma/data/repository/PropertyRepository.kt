package com.wooma.data.repository

import android.content.Context
import com.google.gson.Gson
import com.wooma.data.local.WoomaDatabase
import com.wooma.data.local.dao.PropertyDao
import com.wooma.data.local.entity.PropertyEntity
import com.wooma.data.local.entity.SyncQueueEntity
import com.wooma.data.local.entity.SyncStatus
import com.wooma.data.local.mapper.toEntity
import com.wooma.data.local.mapper.toProperty
import com.wooma.data.network.RetrofitClient
import com.wooma.model.PropertiesRequest
import com.wooma.model.Property
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PropertyRepository(private val context: Context) {

    private val db = WoomaDatabase.getInstance(context)
    private val dao: PropertyDao = db.propertyDao()
    private val api = RetrofitClient.getApi(context)
    private val gson = Gson()

    fun observeActiveProperties(): Flow<List<PropertyEntity>> = dao.observeActiveProperties()

    fun observeActivePropertiesAsModels(): Flow<List<Property>> =
        dao.observeActiveProperties().map { entities -> entities.map { it.toProperty() } }

    fun observeArchivedProperties(): Flow<List<PropertyEntity>> = dao.observeArchivedProperties()

    fun observeById(id: String): Flow<PropertyEntity?> = dao.observeById(id)

    suspend fun getById(id: String): PropertyEntity? = dao.getById(id)

    suspend fun refreshActiveProperties() = withContext(Dispatchers.IO) {
        val r = api.getPropertiesList(
            mapOf("page" to 1, "limit" to 100, "is_active" to true)
        ).execute()
        if (r.isSuccessful) {
            val pendingIds = dao.getPendingSyncProperties().map { it.id }.toSet()
            r.body()?.data?.data
                ?.filter { (it.id ?: "").isNotEmpty() && it.id !in pendingIds }
                ?.forEach { prop ->
                    val entity = prop.toEntity()
                    val updated = dao.updateFromListServer(
                        id = entity.id,
                        address = entity.address,
                        addressLine2 = entity.addressLine2,
                        city = entity.city,
                        postcode = entity.postcode,
                        country = entity.country,
                        propertyType = entity.propertyType,
                        isActive = entity.isActive,
                        noOfReports = entity.noOfReports,
                        lastActivity = entity.lastActivity,
                        updatedAt = entity.updatedAt
                    )
                    if (updated == 0) dao.insertIgnore(entity)
                }
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

    suspend fun updateProperty(localId: String, request: PropertiesRequest) = withContext(Dispatchers.IO) {
        val existing = dao.getById(localId) ?: return@withContext
        dao.upsert(existing.copy(
            address = request.address,
            addressLine2 = request.address_line_2,
            city = request.city,
            postcode = request.postcode,
            syncStatus = if (existing.syncStatus == SyncStatus.SYNCED) SyncStatus.PENDING_UPDATE else existing.syncStatus
        ))
        if (existing.syncStatus == SyncStatus.SYNCED) {
            db.syncQueueDao().enqueue(
                SyncQueueEntity(
                    entityType = "PROPERTY", operationType = "UPDATE",
                    localEntityId = localId, payload = gson.toJson(request)
                )
            )
        }
    }

    suspend fun archiveProperty(localId: String) = withContext(Dispatchers.IO) {
        val existing = dao.getById(localId) ?: return@withContext
        dao.upsert(existing.copy(isActive = false, syncStatus = SyncStatus.PENDING_UPDATE))
        if (existing.serverId != null) {
            db.syncQueueDao().enqueue(
                SyncQueueEntity(
                    entityType = "PROPERTY", operationType = "ARCHIVE",
                    localEntityId = localId, payload = "{}"
                )
            )
        }
    }
}
