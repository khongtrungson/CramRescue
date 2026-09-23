package com.example.cramrescue.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.cramrescue.data.local.dao.CampaignDao
import com.example.cramrescue.data.local.dao.OcrDao
import com.example.cramrescue.data.local.dao.SourceDocumentDao
import com.example.cramrescue.data.local.entity.CampaignEntity
import com.example.cramrescue.data.local.entity.OcrResultEntity
import com.example.cramrescue.data.local.entity.OcrTextBlockEntity
import com.example.cramrescue.data.local.entity.SourceDocumentEntity

@Database(
    entities = [
        CampaignEntity::class,
        SourceDocumentEntity::class,
        OcrResultEntity::class,
        OcrTextBlockEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun campaignDao(): CampaignDao
    abstract fun sourceDocumentDao(): SourceDocumentDao
    abstract fun ocrDao(): OcrDao

    companion object {
        private const val DATABASE_NAME = "cramrescue.db"

        // Milestone 4: adds source_documents, owned by campaigns via a CASCADE foreign key.
        // Existing campaigns data is left untouched.
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `source_documents` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `campaignId` INTEGER NOT NULL,
                        `sourceType` TEXT NOT NULL,
                        `localPath` TEXT NOT NULL,
                        `createdAtEpochMillis` INTEGER NOT NULL,
                        FOREIGN KEY(`campaignId`) REFERENCES `campaigns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_source_documents_campaignId` ON `source_documents` (`campaignId`)"
                )
            }
        }

        // Milestone 5: adds ocr_results (one-to-one with source_documents) and ocr_text_blocks
        // (one-to-many), both owned by source_documents via a CASCADE foreign key. Existing
        // campaigns/source_documents data is left untouched.
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ocr_results` (
                        `documentId` INTEGER NOT NULL,
                        `sourceWidthPx` INTEGER NOT NULL,
                        `sourceHeightPx` INTEGER NOT NULL,
                        `characterCount` INTEGER NOT NULL,
                        `isLowText` INTEGER NOT NULL,
                        `processedAtEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`documentId`),
                        FOREIGN KEY(`documentId`) REFERENCES `source_documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `ocr_text_blocks` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `documentId` INTEGER NOT NULL,
                        `pageIndex` INTEGER NOT NULL,
                        `orderIndex` INTEGER NOT NULL,
                        `text` TEXT NOT NULL,
                        `left` INTEGER NOT NULL,
                        `top` INTEGER NOT NULL,
                        `right` INTEGER NOT NULL,
                        `bottom` INTEGER NOT NULL,
                        FOREIGN KEY(`documentId`) REFERENCES `source_documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ocr_text_blocks_documentId` ON `ocr_text_blocks` (`documentId`)"
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { instance = it }
            }
    }
}
