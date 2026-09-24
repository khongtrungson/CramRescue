package com.example.cramrescue.data.local.database

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cramrescue.data.local.entity.OcrResultEntity
import com.example.cramrescue.data.local.entity.OcrTextBlockEntity
import com.example.cramrescue.data.local.entity.SourceDocumentEntity
import com.example.cramrescue.data.repository.StudyAnalysisRepositoryImpl
import com.example.cramrescue.domain.model.SourceRef
import com.example.cramrescue.domain.model.StudyAnalysis
import com.example.cramrescue.domain.model.StudyPriority
import com.example.cramrescue.domain.model.TriageLevel
import java.time.Instant
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
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
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
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
            .build()

        val campaign = runBlocking { migratedDb.campaignDao().getById(1L) }
        val document = runBlocking { migratedDb.sourceDocumentDao().getById(1L) }
        assertNotNull(campaign)
        assertNotNull(document)
        assertEquals("/data/test/existing.jpg", document?.localPath)

        runBlocking {
            migratedDb.ocrDao().replaceOcrResult(
                documentId = 1L,
                pageResults = listOf(
                    OcrResultEntity(
                        documentId = 1L,
                        pageIndex = 0,
                        sourceWidthPx = 1000,
                        sourceHeightPx = 2000,
                        characterCount = 42,
                        isLowText = false,
                        processedAtEpochMillis = 999L
                    )
                ),
                blocks = listOf(
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

    /**
     * Verifies MIGRATION_3_4 (Milestone 6) preserves Campaign, SourceDocument and existing OCR
     * data while adding pageCount/displayName and turning ocr_results into one row per page.
     * The pre-existing single-row-per-document OCR result must survive as page 0.
     */
    @Test
    fun migratingFromVersion3PreservesOcrDataAndAddsPageSupport() {
        // Bootstrap a version-3 database file exactly as the Milestone 5 app would have left it.
        val v3Db = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        v3Db.execSQL(
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
        v3Db.execSQL(
            "INSERT INTO campaigns (id, name, subjectArchetype, examDateTimeEpochMillis, createdAtEpochMillis) " +
                "VALUES (1, 'Existing Campaign', 'SOCIAL_THEORY', 1000000, 500000)"
        )
        v3Db.execSQL(
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
        v3Db.execSQL("CREATE INDEX IF NOT EXISTS `index_source_documents_campaignId` ON `source_documents` (`campaignId`)")
        v3Db.execSQL(
            "INSERT INTO source_documents (id, campaignId, sourceType, localPath, createdAtEpochMillis) " +
                "VALUES (1, 1, 'GALLERY', '/data/test/existing.jpg', 12345)"
        )
        v3Db.execSQL(
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
        v3Db.execSQL(
            "INSERT INTO ocr_results (documentId, sourceWidthPx, sourceHeightPx, characterCount, isLowText, processedAtEpochMillis) " +
                "VALUES (1, 800, 400, 85, 0, 777)"
        )
        v3Db.execSQL(
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
        v3Db.execSQL("CREATE INDEX IF NOT EXISTS `index_ocr_text_blocks_documentId` ON `ocr_text_blocks` (`documentId`)")
        v3Db.execSQL(
            "INSERT INTO ocr_text_blocks (documentId, pageIndex, orderIndex, text, `left`, `top`, `right`, `bottom`) " +
                "VALUES (1, 0, 0, 'CramRescue OCR Test', 29, 49, 498, 80)"
        )
        v3Db.version = 3
        v3Db.close()

        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5)
            .build()

        // Campaign and SourceDocument survive; the new columns default to null and read as 1 page.
        val campaign = runBlocking { migratedDb.campaignDao().getById(1L) }
        val document = runBlocking { migratedDb.sourceDocumentDao().getById(1L) }
        assertNotNull(campaign)
        assertEquals("/data/test/existing.jpg", document?.localPath)
        assertEquals(null, document?.pageCount)
        assertEquals(null, document?.displayName)

        // The Milestone 5 OCR result survives, carried over as page 0 with its box intact.
        val pages = runBlocking { migratedDb.ocrDao().getPageResultsForDocument(1L) }
        assertEquals(1, pages.size)
        assertEquals(0, pages[0].pageIndex)
        assertEquals(800, pages[0].sourceWidthPx)
        assertEquals(400, pages[0].sourceHeightPx)
        assertEquals(85, pages[0].characterCount)

        val blocks = runBlocking { migratedDb.ocrDao().getBlocksForDocument(1L) }
        assertEquals(1, blocks.size)
        assertEquals("CramRescue OCR Test", blocks[0].text)
        assertEquals(29, blocks[0].left)

        // The upgraded table now accepts a second page for the same document.
        runBlocking {
            migratedDb.ocrDao().upsertPageResults(
                listOf(
                    OcrResultEntity(
                        documentId = 1L,
                        pageIndex = 1,
                        sourceWidthPx = 1000,
                        sourceHeightPx = 1400,
                        characterCount = 10,
                        isLowText = true,
                        processedAtEpochMillis = 888L
                    )
                )
            )
        }
        assertEquals(2, runBlocking { migratedDb.ocrDao().getPageResultsForDocument(1L) }.size)

        migratedDb.close()
    }

    /**
     * Verifies MIGRATION_4_5 (Milestone 7) only adds the analysis tables: Campaign, M6 PDF
     * SourceDocument metadata and multi-page OCR (pages + blocks + boxes) all survive, and an
     * analysis referencing those OCR blocks can be stored afterwards.
     */
    @Test
    fun migratingFromVersion4PreservesPdfOcrDataAndAddsAnalysisTables() {
        // Bootstrap a version-4 database file exactly as the Milestone 6 app would have left it.
        val v4Db = context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null)
        v4Db.execSQL(
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
        v4Db.execSQL(
            "INSERT INTO campaigns (id, name, subjectArchetype, examDateTimeEpochMillis, createdAtEpochMillis) " +
                "VALUES (1, 'M6 Campaign', 'PROGRAMMING_TECH', 1000000, 500000)"
        )
        v4Db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `source_documents` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `campaignId` INTEGER NOT NULL,
                `sourceType` TEXT NOT NULL,
                `localPath` TEXT NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL,
                `pageCount` INTEGER,
                `displayName` TEXT,
                FOREIGN KEY(`campaignId`) REFERENCES `campaigns`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        v4Db.execSQL("CREATE INDEX IF NOT EXISTS `index_source_documents_campaignId` ON `source_documents` (`campaignId`)")
        v4Db.execSQL(
            "INSERT INTO source_documents (id, campaignId, sourceType, localPath, createdAtEpochMillis, pageCount, displayName) " +
                "VALUES (1, 1, 'PDF', '/data/test/notes.pdf', 12345, 2, 'notes.pdf')"
        )
        v4Db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ocr_results` (
                `documentId` INTEGER NOT NULL,
                `pageIndex` INTEGER NOT NULL,
                `sourceWidthPx` INTEGER NOT NULL,
                `sourceHeightPx` INTEGER NOT NULL,
                `characterCount` INTEGER NOT NULL,
                `isLowText` INTEGER NOT NULL,
                `processedAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`documentId`, `pageIndex`),
                FOREIGN KEY(`documentId`) REFERENCES `source_documents`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        v4Db.execSQL(
            "INSERT INTO ocr_results VALUES (1, 0, 1545, 2000, 83, 0, 777), (1, 1, 1545, 2000, 20, 1, 777)"
        )
        v4Db.execSQL(
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
        v4Db.execSQL("CREATE INDEX IF NOT EXISTS `index_ocr_text_blocks_documentId` ON `ocr_text_blocks` (`documentId`)")
        v4Db.execSQL(
            "INSERT INTO ocr_text_blocks (documentId, pageIndex, orderIndex, text, `left`, `top`, `right`, `bottom`) VALUES " +
                "(1, 0, 0, 'Page one block', 10, 20, 300, 60), (1, 1, 0, 'Page two block', 15, 25, 310, 65)"
        )
        v4Db.version = 4
        v4Db.close()

        val migratedDb = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5
            )
            .build()

        val document = runBlocking { migratedDb.sourceDocumentDao().getById(1L) }
        assertEquals("M6 Campaign", runBlocking { migratedDb.campaignDao().getById(1L) }?.name)
        assertEquals(2, document?.pageCount)
        assertEquals("notes.pdf", document?.displayName)

        val pages = runBlocking { migratedDb.ocrDao().getPageResultsForDocument(1L) }
        assertEquals(listOf(0, 1), pages.map { it.pageIndex })
        assertEquals(listOf(false, true), pages.map { it.isLowText })

        val blocks = runBlocking { migratedDb.ocrDao().getBlocksForDocument(1L) }
        assertEquals(listOf("Page one block", "Page two block"), blocks.map { it.text })
        assertEquals(listOf(10, 15), blocks.map { it.left })

        // No analysis exists after the migration; one can be stored against the preserved blocks.
        val repository = StudyAnalysisRepositoryImpl(migratedDb.studyAnalysisDao())
        assertEquals(null, runBlocking { repository.getAnalysisForCampaign(1L) })
        val analysis = StudyAnalysis(
            campaignId = 1L,
            summary = "Summary",
            priorities = listOf(
                StudyPriority(0, "Topic", TriageLevel.CODE_RED, listOf("Bullet"), null, listOf(SourceRef(1L, 1, 0)))
            ),
            discardedGreenCount = 0,
            analyzedPageCount = 2,
            lowTextPageCount = 1,
            sourceFingerprint = "fp",
            modelId = "model",
            generatedAt = Instant.ofEpochMilli(1L)
        )
        runBlocking { repository.replaceAnalysis(analysis) }
        assertEquals(analysis, runBlocking { repository.getAnalysisForCampaign(1L) })

        migratedDb.close()
    }
}
