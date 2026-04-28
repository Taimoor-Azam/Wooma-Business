package com.wooma.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.wooma.data.local.entity.TemplateEntity
import com.wooma.data.local.entity.TemplateItemEntity
import com.wooma.data.local.entity.TemplateRoomEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TemplateDao {

    @Query("SELECT * FROM templates WHERE isActive = 1 AND isDeleted = 0")
    fun observeActive(): Flow<List<TemplateEntity>>

    @Query("SELECT * FROM templates WHERE isActive = 1 AND isDeleted = 0")
    suspend fun getActive(): List<TemplateEntity>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun getById(id: String): TemplateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplate(template: TemplateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplates(templates: List<TemplateEntity>)

    @Query("SELECT * FROM template_rooms WHERE templateId = :templateId ORDER BY displayOrder ASC")
    suspend fun getRoomsByTemplate(templateId: String): List<TemplateRoomEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplateRoom(room: TemplateRoomEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplateRooms(rooms: List<TemplateRoomEntity>)

    @Query("SELECT * FROM template_items WHERE templateRoomId = :roomId ORDER BY displayOrder ASC")
    suspend fun getItemsByRoom(roomId: String): List<TemplateItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplateItem(item: TemplateItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplateItems(items: List<TemplateItemEntity>)

    @Query("DELETE FROM templates")
    suspend fun clearAll()
}
