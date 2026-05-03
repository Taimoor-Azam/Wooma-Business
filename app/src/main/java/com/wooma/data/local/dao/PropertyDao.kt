package com.wooma.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wooma.data.local.entity.PropertyEntity
import com.wooma.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface PropertyDao {

    @Query("SELECT * FROM properties WHERE isActive = 1 ORDER BY createdAt DESC")
    fun observeActiveProperties(): Flow<List<PropertyEntity>>

    @Query("SELECT * FROM properties WHERE isActive = 0 ORDER BY createdAt DESC")
    fun observeArchivedProperties(): Flow<List<PropertyEntity>>

    @Query("SELECT * FROM properties WHERE id = :id")
    fun observeById(id: String): Flow<PropertyEntity?>

    @Query("SELECT * FROM properties WHERE id = :id")
    suspend fun getById(id: String): PropertyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(property: PropertyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(properties: List<PropertyEntity>)

    @Query("UPDATE properties SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    @Query("UPDATE properties SET serverId = :newServerId, syncStatus = 'SYNCED' WHERE id = :localId")
    suspend fun promoteLocalId(localId: String, newServerId: String)

    @Query("SELECT * FROM properties WHERE syncStatus != 'SYNCED'")
    suspend fun getPendingSyncProperties(): List<PropertyEntity>

    @Query("DELETE FROM properties WHERE id = :id")
    suspend fun deleteById(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(property: PropertyEntity): Long

    @Query("UPDATE properties SET noOfReports = noOfReports + 1 WHERE id = :propertyId")
    suspend fun incrementReportCount(propertyId: String)

    @Query("UPDATE properties SET address = :address, addressLine2 = :addressLine2, city = :city, postcode = :postcode, country = :country, propertyType = :propertyType, isActive = :isActive, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFromServer(
        id: String,
        address: String,
        addressLine2: String?,
        city: String,
        postcode: String,
        country: String?,
        propertyType: String?,
        isActive: Boolean,
        updatedAt: String
    ): Int
}
