package com.example.cramrescue.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cramrescue.data.local.database.AppDatabase
import com.example.cramrescue.data.local.entity.CampaignEntity
import com.example.cramrescue.data.local.entity.DocumentTextBlockEntity
import com.example.cramrescue.data.local.entity.SourceDocumentEntity
import com.example.cramrescue.data.repository.DocumentTextRepositoryImpl
import com.example.cramrescue.domain.model.SourceDocument
import com.example.cramrescue.domain.model.SourceType
import com.example.cramrescue.domain.model.TextBlockKind
import com.example.cramrescue.domain.word.ExtractedTextBlock
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Word text storage (M13) against the real v9 schema: atomic import, ordering, deletion, cascade. */
@RunWith(AndroidJUnit4::class)
class DocumentTextDaoTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var texts: DocumentTextRepositoryImpl
    private val databaseName = "test-document-text-cramrescue.db"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(databaseName)
        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
        texts = DocumentTextRepositoryImpl(database.documentTextDao())
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    private fun campaign(name: String): Long = runBlocking {
        database.campaignDao().insert(CampaignEntity(name = name, subjectArchetype = "SOCIAL_THEORY", examDateTimeEpochMillis = 1_000_000L, createdAtEpochMillis = 500_000L))
    }

    private fun importWord(campaignId: Long, vararg paragraphs: String, at: Long = 20_000L): Long = runBlocking {
        texts.saveWordDocument(
            SourceDocument(campaignId = campaignId, sourceType = SourceType.WORD, localPath = "/w/$campaignId.docx", createdAt = Instant.ofEpochMilli(at), pageCount = 1, displayName = "$campaignId.docx"),
            paragraphs.map { ExtractedTextBlock(TextBlockKind.PARAGRAPH, it) },
            Instant.ofEpochMilli(at)
        )
    }

    @Test
    fun paragraphsAreStoredInReadingOrderWithTheirExtractionTime() = runBlocking {
        val id = importWord(campaign("A"), "Một", "Hai", "Ba", at = 42_000L)
        val text = texts.getText(id)!!
        assertEquals(listOf(0, 1, 2), text.blocks.map { it.orderIndex })
        assertEquals(listOf("Một", "Hai", "Ba"), text.blocks.map { it.text })
        assertEquals(Instant.ofEpochMilli(42_000L), text.extractedAt)
        assertEquals("WORD", database.sourceDocumentDao().getById(id)?.sourceType)
        assertEquals(1, database.sourceDocumentDao().getById(id)?.pageCount)
    }

    @Test
    fun aFailedTextInsertLeavesNoDocumentRowBehind() = runBlocking {
        val campaignId = campaign("A")
        val dao = database.documentTextDao()
        // Duplicate (documentId, orderIndex) violates the primary key mid-transaction.
        val duplicate = listOf(
            DocumentTextBlockEntity(0, 0, "PARAGRAPH", "x", 1L),
            DocumentTextBlockEntity(0, 0, "PARAGRAPH", "y", 1L)
        )
        try {
            dao.insertWordDocument(SourceDocumentEntity(campaignId = campaignId, sourceType = "WORD", localPath = "/w/x.docx", createdAtEpochMillis = 1L, pageCount = 1), duplicate)
            fail("expected a constraint failure")
        } catch (expected: Exception) {
            // rolled back
        }
        assertTrue(database.sourceDocumentDao().getForCampaign(campaignId).isEmpty())
    }

    @Test
    fun deletingAWordMaterialRemovesItsTextOnlyAndOtherDocumentsSurvive() = runBlocking {
        val campaignId = campaign("A")
        val keep = importWord(campaignId, "Giữ lại")
        val remove = importWord(campaignId, "Xóa", "đi")

        assertEquals(remove, database.sourceDocumentDao().deleteDocumentGraph(campaignId, remove)?.id)

        assertNull(texts.getText(remove))
        assertEquals(listOf("Giữ lại"), texts.getText(keep)?.blocks?.map { it.text })
        assertEquals(listOf(keep), database.sourceDocumentDao().getForCampaign(campaignId).map { it.id })
    }

    @Test
    fun anotherCampaignCannotDeleteTheWordMaterial() = runBlocking {
        val a = campaign("A")
        val b = campaign("B")
        val id = importWord(a, "Của A")
        assertNull(database.sourceDocumentDao().deleteDocumentGraph(b, id))
        assertEquals(listOf("Của A"), texts.getText(id)?.blocks?.map { it.text })
    }

    @Test
    fun deletingTheCampaignCascadesToWordText() = runBlocking {
        val a = campaign("A")
        val b = campaign("B")
        val idA = importWord(a, "A1", "A2")
        val idB = importWord(b, "B1")

        database.openHelper.writableDatabase.execSQL("DELETE FROM campaigns WHERE id = $a")

        assertNull(texts.getText(idA))
        assertEquals(listOf("B1"), texts.getText(idB)?.blocks?.map { it.text })
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM document_text_blocks WHERE documentId = $idA").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
    }
}
