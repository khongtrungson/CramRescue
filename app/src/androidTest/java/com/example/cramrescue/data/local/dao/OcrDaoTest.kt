package com.example.cramrescue.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cramrescue.data.local.database.AppDatabase
import com.example.cramrescue.data.local.entity.CampaignEntity
import com.example.cramrescue.data.local.entity.OcrResultEntity
import com.example.cramrescue.data.local.entity.OcrTextBlockEntity
import com.example.cramrescue.data.local.entity.SourceDocumentEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OcrDaoTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private val databaseName = "test-ocr-cramrescue.db"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(databaseName)
        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    private fun insertCampaign(name: String): Long = runBlocking {
        database.campaignDao().insert(
            CampaignEntity(name = name, subjectArchetype = "SOCIAL_THEORY", examDateTimeEpochMillis = 1L, createdAtEpochMillis = 1L)
        )
    }

    private fun insertDocument(campaignId: Long, path: String): Long = runBlocking {
        database.sourceDocumentDao().insert(
            SourceDocumentEntity(campaignId = campaignId, sourceType = "GALLERY", localPath = path, createdAtEpochMillis = 1L)
        )
    }

    private fun block(documentId: Long, orderIndex: Int, text: String) = OcrTextBlockEntity(
        documentId = documentId,
        pageIndex = 0,
        orderIndex = orderIndex,
        text = text,
        left = 0,
        top = 0,
        right = 1,
        bottom = 1
    )

    @Test
    fun ocrBlocksAreScopedToTheirOwningDocument() = runBlocking {
        val campaignA = insertCampaign("Campaign A")
        val campaignB = insertCampaign("Campaign B")
        val documentA = insertDocument(campaignA, "/a.jpg")
        val documentB = insertDocument(campaignB, "/b.jpg")

        database.ocrDao().replaceOcrResult(
            OcrResultEntity(documentA, 100, 200, 20, isLowText = true, processedAtEpochMillis = 1L),
            listOf(block(documentA, 0, "A1"), block(documentA, 1, "A2"))
        )
        database.ocrDao().replaceOcrResult(
            OcrResultEntity(documentB, 100, 200, 2, isLowText = true, processedAtEpochMillis = 1L),
            listOf(block(documentB, 0, "B1"))
        )

        val blocksA = database.ocrDao().getBlocksForDocument(documentA)
        val blocksB = database.ocrDao().getBlocksForDocument(documentB)

        assertEquals(2, blocksA.size)
        assertTrue(blocksA.all { it.documentId == documentA })
        assertEquals(1, blocksB.size)
        assertEquals("B1", blocksB[0].text)
    }

    @Test
    fun reprocessingReplacesRatherThanDuplicatesBlocks() = runBlocking {
        val campaignId = insertCampaign("Campaign")
        val documentId = insertDocument(campaignId, "/a.jpg")

        database.ocrDao().replaceOcrResult(
            OcrResultEntity(documentId, 100, 200, 10, isLowText = true, processedAtEpochMillis = 1L),
            listOf(block(documentId, 0, "first pass"))
        )
        database.ocrDao().replaceOcrResult(
            OcrResultEntity(documentId, 100, 200, 11, isLowText = true, processedAtEpochMillis = 2L),
            listOf(block(documentId, 0, "second pass"))
        )

        val blocks = database.ocrDao().getBlocksForDocument(documentId)
        val result = database.ocrDao().getResult(documentId)

        assertEquals(1, blocks.size)
        assertEquals("second pass", blocks[0].text)
        assertEquals(11, result?.characterCount)
    }

    @Test
    fun blockOrderIsPreserved() = runBlocking {
        val campaignId = insertCampaign("Campaign")
        val documentId = insertDocument(campaignId, "/a.jpg")

        database.ocrDao().replaceOcrResult(
            OcrResultEntity(documentId, 100, 200, 30, isLowText = false, processedAtEpochMillis = 1L),
            listOf(block(documentId, 2, "third"), block(documentId, 0, "first"), block(documentId, 1, "second"))
        )

        val blocks = database.ocrDao().getBlocksForDocument(documentId)

        assertEquals(listOf("first", "second", "third"), blocks.map { it.text })
    }
}
