package com.example.cramrescue.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cramrescue.data.local.MaterialFileStore
import com.example.cramrescue.data.local.database.AppDatabase
import com.example.cramrescue.data.local.entity.CampaignEntity
import com.example.cramrescue.data.local.entity.SourceDocumentEntity
import com.example.cramrescue.data.repository.MicroCardRepositoryImpl
import com.example.cramrescue.data.repository.SourceDocumentRepositoryImpl
import com.example.cramrescue.domain.model.MicroCard
import com.example.cramrescue.domain.model.SourceRef
import com.example.cramrescue.domain.model.TriageLevel
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MicroCardDaoTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var cards: MicroCardRepositoryImpl
    private val databaseName = "test-microcards-cramrescue.db"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(databaseName)
        open()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    private fun open() {
        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
        cards = MicroCardRepositoryImpl(database.microCardDao())
    }

    private fun campaignWithDocuments(name: String, documentCount: Int): Pair<Long, List<Long>> = runBlocking {
        val campaignId = database.campaignDao().insert(
            CampaignEntity(name = name, subjectArchetype = "PROGRAMMING_TECH", examDateTimeEpochMillis = 1L, createdAtEpochMillis = 1L)
        )
        // "/not-app-owned/..." paths: MaterialFileStore will refuse to touch them on deletion.
        campaignId to (1..documentCount).map {
            database.sourceDocumentDao().insert(
                SourceDocumentEntity(campaignId = campaignId, sourceType = "PDF", localPath = "/not-app-owned/$name$it.pdf", createdAtEpochMillis = 1L, pageCount = 2)
            )
        }
    }

    private fun card(campaignId: Long, order: Int, title: String, vararg refs: SourceRef) = MicroCard(
        campaignId = campaignId,
        analysisGeneratedAt = Instant.ofEpochMilli(5_000L),
        orderIndex = order,
        triage = if (order == 0) TriageLevel.CODE_RED else TriageLevel.CODE_YELLOW,
        title = title,
        bullets = listOf("$title b1", "$title b2"),
        notes = if (order == 0) "overflow" else null,
        sourceRefs = refs.toList(),
        createdAt = Instant.ofEpochMilli(6_000L)
    )

    @Test
    fun cardsRoundTripWithSourcesAndNeverLeakAcrossCampaigns() = runBlocking {
        val (campaignA, docsA) = campaignWithDocuments("A", 1)
        val (campaignB, docsB) = campaignWithDocuments("B", 1)
        val cardsA = listOf(
            card(campaignA, 0, "A red", SourceRef(docsA[0], 0, 0), SourceRef(docsA[0], 1, 3)),
            card(campaignA, 1, "A yellow", SourceRef(docsA[0], 1, 0))
        )
        cards.replaceCards(campaignA, cardsA)
        cards.replaceCards(campaignB, listOf(card(campaignB, 0, "B red", SourceRef(docsB[0], 0, 0))))

        val readA = cards.getCardsForCampaign(campaignA)
        assertEquals(cardsA, readA.map { it.copy(id = 0L) })
        assertEquals(listOf("B red"), cards.getCardsForCampaign(campaignB).map { it.title })
        // A card id of campaign A is not reachable through campaign B.
        assertEquals(null, cards.getCard(campaignB, readA.first().id))
        assertEquals("A red", cards.getCard(campaignA, readA.first().id)?.title)
    }

    @Test
    fun replacementIsWholeSetAndOnlyForThatCampaign() = runBlocking {
        val (campaignA, docsA) = campaignWithDocuments("A", 1)
        val (campaignB, docsB) = campaignWithDocuments("B", 1)
        cards.replaceCards(campaignB, listOf(card(campaignB, 0, "B", SourceRef(docsB[0], 0, 0))))
        cards.replaceCards(campaignA, (0..2).map { card(campaignA, it, "Old $it", SourceRef(docsA[0], 0, it)) })

        cards.replaceCards(campaignA, listOf(card(campaignA, 0, "New", SourceRef(docsA[0], 0, 0))))

        assertEquals(listOf("New"), cards.getCardsForCampaign(campaignA).map { it.title })
        assertEquals(1, database.microCardDao().getSourceRefsForCampaign(campaignA).size)
        assertEquals(listOf("B"), cards.getCardsForCampaign(campaignB).map { it.title })
    }

    @Test
    fun deletingASourceDocumentKeepsCardsButRemovesOnlyItsRefs() = runBlocking {
        val (campaignId, docs) = campaignWithDocuments("A", 2)
        cards.replaceCards(
            campaignId,
            listOf(
                card(campaignId, 0, "Both", SourceRef(docs[0], 0, 0), SourceRef(docs[1], 0, 0)),
                card(campaignId, 1, "Only deleted", SourceRef(docs[1], 1, 0))
            )
        )

        val documents = SourceDocumentRepositoryImpl(database.sourceDocumentDao(), MaterialFileStore(context))
        assertTrue(documents.deleteDocument(campaignId, docs[1]))

        val remaining = cards.getCardsForCampaign(campaignId)
        assertEquals(listOf("Both", "Only deleted"), remaining.map { it.title })
        assertEquals(listOf(SourceRef(docs[0], 0, 0)), remaining[0].sourceRefs)
        assertTrue(remaining[1].sourceRefs.isEmpty())
    }

    @Test
    fun deletingTheCampaignCascadesToItsCards() = runBlocking {
        val (campaignId, docs) = campaignWithDocuments("A", 1)
        cards.replaceCards(campaignId, listOf(card(campaignId, 0, "X", SourceRef(docs[0], 0, 0))))

        database.openHelper.writableDatabase.execSQL("DELETE FROM campaigns WHERE id = $campaignId")

        assertTrue(cards.getCardsForCampaign(campaignId).isEmpty())
        assertTrue(database.microCardDao().getSourceRefsForCampaign(campaignId).isEmpty())
    }

    @Test
    fun cardsSurviveReopeningTheDatabase() = runBlocking {
        val (campaignId, docs) = campaignWithDocuments("A", 1)
        val saved = listOf(card(campaignId, 0, "Persisted", SourceRef(docs[0], 1, 2)))
        cards.replaceCards(campaignId, saved)

        database.close()
        open()

        assertEquals(saved, cards.getCardsForCampaign(campaignId).map { it.copy(id = 0L) })
    }
}
