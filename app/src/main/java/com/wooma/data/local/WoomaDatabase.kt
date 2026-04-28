package com.wooma.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.wooma.data.local.dao.*
import com.wooma.data.local.entity.*

@Database(
    entities = [
        PropertyEntity::class,
        ReportEntity::class,
        ReportTypeEntity::class,
        RoomEntity::class,
        RoomItemEntity::class,
        RoomInspectionEntity::class,
        MeterEntity::class,
        KeyEntity::class,
        DetectorEntity::class,
        AttachmentEntity::class,
        ChecklistEntity::class,
        ChecklistQuestionEntity::class,
        ChecklistInfoFieldEntity::class,
        TenantReviewEntity::class,
        AssessorEntity::class,
        TemplateEntity::class,
        TemplateRoomEntity::class,
        TemplateItemEntity::class,
        SyncQueueEntity::class,
        PendingUploadEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class WoomaDatabase : RoomDatabase() {

    abstract fun propertyDao(): PropertyDao
    abstract fun reportDao(): ReportDao
    abstract fun reportTypeDao(): ReportTypeDao
    abstract fun roomDao(): RoomDao
    abstract fun roomItemDao(): RoomItemDao
    abstract fun roomInspectionDao(): RoomInspectionDao
    abstract fun meterDao(): MeterDao
    abstract fun keyDao(): KeyDao
    abstract fun detectorDao(): DetectorDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun checklistQuestionDao(): ChecklistQuestionDao
    abstract fun checklistInfoFieldDao(): ChecklistInfoFieldDao
    abstract fun tenantReviewDao(): TenantReviewDao
    abstract fun assessorDao(): AssessorDao
    abstract fun templateDao(): TemplateDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun pendingUploadDao(): PendingUploadDao

    companion object {
        @Volatile
        private var INSTANCE: WoomaDatabase? = null

        fun getInstance(context: Context): WoomaDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    WoomaDatabase::class.java,
                    "wooma_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
