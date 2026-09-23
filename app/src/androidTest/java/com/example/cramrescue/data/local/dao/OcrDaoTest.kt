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

    private fun insertDocument(campaignId: Long, path: String, type: String = "GALLERY", pages: Int = 1): Long =
        runBlocking {
            database.sourceDocumentDao().insert(
                SourceDocumentEntity(
                    campaignId = campaignId,
                    sourceType = type,
                    localPath = path,
                    createdAtEpochMillis = 1L,
                    pageCount = pages,
                    displayName = if (type == "PDF") "doc.pdf" else null
                )
            )
        }

    private fun pageResult(documentId: Long, pageIndex: Int, characterCount: Int = 100) = OcrResultEntity(
        documentId = documentId,
        pageIndex = pageIndex,
        sourceWidthPx = 1000,
        sourceHeightPx = 1400,
        characterCount = characterCount,
        isLowText = characterCount < 50,
        processedAtEpochMillis = 1L
    )

    private fun block(documentId: Long, pageIndex: Int, orderIndex: Int, text: String) = OcrTextBlockEntity(
        documentId = documentId,
        pageIndex = pageIndex,
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
            documentA,
            listOf(pageResult(documentA, 0)),
            listOf(block(documentA, 0, 0, "A1"), block(documentA, 0, 1, "A2"))
        )
        database.ocrDao().replaceOcrResult(
            documentB,
            listOf(pageResult(documentB, 0)),
            listOf(block(documentB, 0, 0, "B1"))
        )

        val blocksA = database.ocrDao().getBlocksForDocument(documentA)
        val blocksB = database.ocrDao().getBlocksForDocument(documentB)

        assertEquals(2, blocksA.size)
        assertTrue(blocksA.all { it.documentId == documentA })
        assertEquals(1, blocksB.size)
        assertEquals("B1", blocksB[0].text)
    }

    @Test
    fun multiPagePdfKeepsPerPageResultsAndBlocksSeparated() = runBlocking {
        val campaignA = insertCampaign("Campaign A")
        val campaignB = insertCampaign("Campaign B")
        val pdfA = insertDocument(campaignA, "/a.pdf", type = "PDF", pages = 2)
        val pdfB = insertDocument(campaignB, "/b.pdf", type = "PDF", pages = 1)

        database.ocrDao().replaceOcrResult(
            pdfA,
            listOf(pageResult(pdfA, 0, characterCount = 120), pageResult(pdfA, 1, characterCount = 30)),
            listOf(
                block(pdfA, 0, 0, "A0-block1"),
                block(pdfA, 0, 1, "A0-block2"),
                block(pdfA, 1, 0, "A1-block1")
            )
        )
        database.ocrDao().replaceOcrResult(
            pdfB,
            listOf(pageResult(pdfB, 0)),
            listOf(block(pdfB, 0, 0, "B0-block1"))
        )

        val pdfAPage0 = database.ocrDao().getBlocksForPage(pdfA, 0)
        val pdfAPage1 = database.ocrDao().getBlocksForPage(pdfA, 1)
        val pdfAPages = database.ocrDao().getPageResultsForDocument(pdfA)

        // Page 0 of PDF A must not leak page 1's block, nor anything from Campaign B's PDF.
        assertEquals(listOf("A0-block1", "A0-block2"), pdfAPage0.map { it.text })
        assertEquals(listOf("A1-block1"), pdfAPage1.map { it.text })
        assertTrue(database.ocrDao().getBlocksForDocument(pdfA).none { it.text.startsWith("B0") })

        // Per-page quality is preserved independently.
        assertEquals(listOf(0, 1), pdfAPages.map { it.pageIndex })
        assertEquals(false, pdfAPages[0].isLowText)
        assertEquals(true, pdfAPages[1].isLowText)

        assertEquals(1, database.ocrDao().getPageResultsForDocument(pdfB).size)
    }

    @Test
    fun reprocessingReplacesRatherThanDuplicatesPagesAndBlocks() = runBlocking {
        val campaignId = insertCampaign("Campaign")
        val pdfId = insertDocument(campaignId, "/a.pdf", type = "PDF", pages = 2)

        database.ocrDao().replaceOcrResult(
            pdfId,
            listOf(pageResult(pdfId, 0), pageResult(pdfId, 1)),
            listOf(block(pdfId, 0, 0, "first pass p0"), block(pdfId, 1, 0, "first pass p1"))
        )
        database.ocrDao().replaceOcrResult(
            pdfId,
            listOf(pageResult(pdfId, 0), pageResult(pdfId, 1)),
            listOf(block(pdfId, 0, 0, "second pass p0"), block(pdfId, 1, 0, "second pass p1"))
        )

        val pages = database.ocrDao().getPageResultsForDocument(pdfId)
        val blocks = database.ocrDao().getBlocksForDocument(pdfId)

        assertEquals(2, pages.size)
        assertEquals(2, blocks.size)
        assertEquals(listOf("second pass p0", "second pass p1"), blocks.map { it.text })
    }

    @Test
    fun blockOrderIsPreservedWithinAndAcrossPages() = runBlocking {
        val campaignId = insertCampaign("Campaign")
        val pdfId = insertDocument(campaignId, "/a.pdf", type = "PDF", pages = 2)

        database.ocrDao().replaceOcrResult(
            pdfId,
            listOf(pageResult(pdfId, 0), pageResult(pdfId, 1)),
            listOf(
                block(pdfId, 1, 1, "p1-second"),
                block(pdfId, 0, 1, "p0-second"),
                block(pdfId, 1, 0, "p1-first"),
                block(pdfId, 0, 0, "p0-first")
            )
        )

        val blocks = database.ocrDao().getBlocksForDocument(pdfId)

        assertEquals(listOf("p0-first", "p0-second", "p1-first", "p1-second"), blocks.map { it.text })
    }
}
