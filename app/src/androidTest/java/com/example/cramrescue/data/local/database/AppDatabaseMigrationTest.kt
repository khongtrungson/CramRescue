package com.example.cramrescue.data.local.database

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cramrescue.data.local.entity.OcrResultEntity
import com.example.cramrescue.data.local.entity.OcrTextBlockEntity
import com.example.cramrescue.data.local.entity.SourceDocumentEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies MIGRATION_1_2 preserves existing Milestone 2/3 Campaign data while adding the new
 * source_documents table, rather than relying on a destructive migration.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private lateinit var context: Context
    private val databaseName = "test-migration-cramrescue.db"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun migratingFromVersion1PreservesExistingCampaignAndAddsSourceDocumentsTable() {
        // Bootstrap a version-1 database file exactly as the Milestone 2/3 app would have left it.
        val v1Db = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        v1Db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `campaigns` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `subjectArchetype` TEXT NOT NULL,
                `examDateTimeEpochMillis` INTEGER NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        v1Db.execSQL(
            "INSERT INTO campaigns (id, name, subjectArchetype, examDateTimeEpochMillis, createdAtEpochMillis) " +
                "VALUES (1, 'Existing Campaign', 'SOCIAL_THEORY', 1000000, 500000)"
        )
        v1Db.version = 1
        v1Db.close()

        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()

        val campaign = runBlocking { migratedDb.campaignDao().getById(1L) }
        assertNotNull(campaign)
        assertEquals("Existing Campaign", campaign?.name)

        val documentId = runBlocking {
            migratedDb.sourceDocumentDao().insert(
                SourceDocumentEntity(
                    campaignId = 1L,
                    sourceType = "GALLERY",
                    localPath = "/data/test/image.jpg",
                    createdAtEpochMillis = 999L
                )
            )
        }
        val documents = runBlocking { migratedDb.sourceDocumentDao().getForCampaign(1L) }

        assertEquals(1, documents.size)
        assertEquals(documentId, documents[0].id)
        assertEquals("/data/test/image.jpg", documents[0].localPath)

        migratedDb.close()
    }

    /**
     * Verifies MIGRATION_2_3 preserves existing Milestone 2-4 Campaign/SourceDocument data while
     * adding the new ocr_results/ocr_text_blocks tables.
     */
    @Test
    fun migratingFromVersion2PreservesExistingDataAndAddsOcrTables() {
        // Bootstrap a version-2 database file exactly as the Milestone 4 app would have left it.
        val v2Db = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        v2Db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `campaigns` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `subjectArchetype` TEXT NOT NULL,
                `examDateTimeEpochMillis` INTEGER NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        v2Db.execSQL(
            "INSERT INTO campaigns (id, name, subjectArchetype, examDateTimeEpochMillis, createdAtEpochMillis) " +
                "VALUES (1, 'Existing Campaign', 'SOCIAL_THEORY', 1000000, 500000)"
        )
        v2Db.execSQL(
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
        v2Db.execSQL("CREATE INDEX IF NOT EXISTS `index_source_documents_campaignId` ON `source_documents` (`campaignId`)")
        v2Db.execSQL(
            "INSERT INTO source_documents (id, campaignId, sourceType, localPath, createdAtEpochMillis) " +
                "VALUES (1, 1, 'GALLERY', '/data/test/existing.jpg', 12345)"
        )
        v2Db.version = 2
        v2Db.close()

        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()

        val campaign = runBlocking { migratedDb.campaignDao().getById(1L) }
        val document = runBlocking { migratedDb.sourceDocumentDao().getById(1L) }
        assertNotNull(campaign)
        assertNotNull(document)
        assertEquals("/data/test/existing.jpg", document?.localPath)

        runBlocking {
            migratedDb.ocrDao().replaceOcrResult(
                OcrResultEntity(
                    documentId = 1L,
                    sourceWidthPx = 1000,
                    sourceHeightPx = 2000,
                    characterCount = 42,
                    isLowText = false,
                    processedAtEpochMillis = 999L
                ),
                listOf(
                    OcrTextBlockEntity(
                        documentId = 1L,
                        pageIndex = 0,
                        orderIndex = 0,
                        text = "Hello",
                        left = 1,
                        top = 2,
                        right = 3,
                        bottom = 4
                    )
                )
            )
        }
        val blocks = runBlocking { migratedDb.ocrDao().getBlocksForDocument(1L) }

        assertEquals(1, blocks.size)
        assertEquals("Hello", blocks[0].text)

        migratedDb.close()
    }
}
