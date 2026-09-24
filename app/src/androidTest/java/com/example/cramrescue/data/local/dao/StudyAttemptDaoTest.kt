package com.example.cramrescue.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cramrescue.data.local.database.AppDatabase
import com.example.cramrescue.data.local.entity.CampaignEntity
import com.example.cramrescue.data.local.entity.SourceDocumentEntity
import com.example.cramrescue.data.repository.MicroCardRepositoryImpl
import com.example.cramrescue.data.repository.StudyAttemptRepositoryImpl
import com.example.cramrescue.domain.model.MicroCard
import com.example.cramrescue.domain.model.RecallAssessment
import com.example.cramrescue.domain.model.SourceRef
import com.example.cramrescue.domain.model.StudyAttempt
import com.example.cramrescue.domain.model.TriageLevel
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyAttemptDaoTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var attempts: StudyAttemptRepositoryImpl
    private lateinit var cards: MicroCardRepositoryImpl
    private val databaseName = "test-study-attempts-cramrescue.db"

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
        attempts = StudyAttemptRepositoryImpl(database.studyAttemptDao())
        cards = MicroCardRepositoryImpl(database.microCardDao())
    }

    /** A campaign with one document and [count] persisted cards; returns (campaignId, card ids). */
    private fun campaignWithCards(name: String, count: Int): Pair<Long, List<Long>> = runBlocking {
        val campaignId = database.campaignDao().insert(
            CampaignEntity(name = name, subjectArchetype = "SOCIAL_THEORY", examDateTimeEpochMillis = 1L, createdAtEpochMillis = 1L)
        )
        val documentId = database.sourceDocumentDao().insert(
            SourceDocumentEntity(campaignId = campaignId, sourceType = "PDF", localPath = "/not-app-owned/$name.pdf", createdAtEpochMillis = 1L, pageCount = 1)
        )
        cards.replaceCards(campaignId, (0 until count).map { card(campaignId, it, SourceRef(documentId, 0, it)) })
        campaignId to cards.getCardsForCampaign(campaignId).map { it.id }
    }

    private fun card(campaignId: Long, order: Int, ref: SourceRef) = MicroCard(
        campaignId = campaignId, analysisGeneratedAt = Instant.ofEpochMilli(1L), orderIndex = order,
        triage = if (order == 0) TriageLevel.CODE_RED else TriageLevel.CODE_YELLOW, title = "Card $order",
        bullets = listOf("b"), notes = null, sourceRefs = listOf(ref), createdAt = Instant.ofEpochMilli(1L)
    )

    private fun attempt(campaignId: Long, cardId: Long, assessment: RecallAssessment, at: Long) = StudyAttempt(
        campaignId = campaignId, microCardId = cardId, triage = TriageLevel.CODE_RED, cardTitle = "Card",
        assessment = assessment, attemptedAt = Instant.ofEpochMilli(at)
    )

    @Test
    fun attemptsAreAppendedAndReadBackInOrder() = runBlocking {
        val (campaignId, cardIds) = campaignWithCards("A", 1)
        listOf(RecallAssessment.NOT_REMEMBERED, RecallAssessment.PARTIAL, RecallAssessment.MASTERED)
            .forEachIndexed { i, level -> attempts.recordAttempt(attempt(campaignId, cardIds[0], level, at = 100L + i)) }

        val history = attempts.getAttemptsForCard(campaignId, cardIds[0])
        assertEquals(
            listOf(RecallAssessment.NOT_REMEMBERED, RecallAssessment.PARTIAL, RecallAssessment.MASTERED),
            history.map { it.assessment }
        )
        assertEquals(listOf(100L, 101L, 102L), history.map { it.attemptedAt.toEpochMilli() })
    }

    @Test
    fun campaignsNeverSeeEachOthersAttempts() = runBlocking {
        val (campaignA, cardsA) = campaignWithCards("A", 1)
        val (campaignB, cardsB) = campaignWithCards("B", 1)
        attempts.recordAttempt(attempt(campaignA, cardsA[0], RecallAssessment.MASTERED, 1))
        attempts.recordAttempt(attempt(campaignB, cardsB[0], RecallAssessment.NOT_REMEMBERED, 2))

        assertEquals(listOf(RecallAssessment.MASTERED), attempts.getAttemptsForCampaign(campaignA).map { it.assessment })
        assertEquals(listOf(RecallAssessment.NOT_REMEMBERED), attempts.getAttemptsForCampaign(campaignB).map { it.assessment })
        // Campaign B can't read Campaign A's card history through its own scope.
        assertTrue(attempts.getAttemptsForCard(campaignB, cardsA[0]).isEmpty())
    }

    @Test
    fun rebuildingCardsKeepsHistoryButUnlinksItFromTheOldCards() = runBlocking {
        val (campaignId, oldCards) = campaignWithCards("A", 2)
        attempts.recordAttempt(attempt(campaignId, oldCards[0], RecallAssessment.MASTERED, 1))

        // M8 rebuild: the whole card set is replaced with new ids. It must not fail on history.
        val documentId = database.sourceDocumentDao().getForCampaign(campaignId).single().id
        cards.replaceCards(campaignId, listOf(card(campaignId, 0, SourceRef(documentId, 0, 0))))
        val newCardId = cards.getCardsForCampaign(campaignId).single().id

        val history = attempts.getAttemptsForCampaign(campaignId)
        assertEquals(1, history.size)
        assertNull("old card link is cleared, not deleted", history.single().microCardId)
        assertEquals(RecallAssessment.MASTERED, history.single().assessment)
        // The replacement card does not inherit the old result.
        assertTrue(attempts.getAttemptsForCard(campaignId, newCardId).isEmpty())
    }

    @Test
    fun deletingTheCampaignRemovesItsHistoryOnly() = runBlocking {
        val (campaignA, cardsA) = campaignWithCards("A", 1)
        val (campaignB, cardsB) = campaignWithCards("B", 1)
        attempts.recordAttempt(attempt(campaignA, cardsA[0], RecallAssessment.MASTERED, 1))
        attempts.recordAttempt(attempt(campaignB, cardsB[0], RecallAssessment.PARTIAL, 2))

        database.openHelper.writableDatabase.execSQL("DELETE FROM campaigns WHERE id = $campaignA")

        assertTrue(attempts.getAttemptsForCampaign(campaignA).isEmpty())
        assertEquals(1, attempts.getAttemptsForCampaign(campaignB).size)
    }

    @Test
    fun attemptsSurviveReopeningTheDatabase() = runBlocking {
        val (campaignId, cardIds) = campaignWithCards("A", 1)
        attempts.recordAttempt(attempt(campaignId, cardIds[0], RecallAssessment.PARTIAL, 5))

        database.close()
        open()

        assertEquals(listOf(RecallAssessment.PARTIAL), attempts.getAttemptsForCampaign(campaignId).map { it.assessment })
    }
}
