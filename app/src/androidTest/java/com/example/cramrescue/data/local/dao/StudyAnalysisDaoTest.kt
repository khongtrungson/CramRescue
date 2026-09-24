package com.example.cramrescue.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cramrescue.data.local.database.AppDatabase
import com.example.cramrescue.data.local.entity.CampaignEntity
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyAnalysisDaoTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: StudyAnalysisRepositoryImpl
    private val databaseName = "test-analysis-cramrescue.db"

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(databaseName)
        database = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
        repository = StudyAnalysisRepositoryImpl(database.studyAnalysisDao())
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    private fun insertCampaignWithDocument(name: String): Pair<Long, Long> = runBlocking {
        val campaignId = database.campaignDao().insert(
            CampaignEntity(name = name, subjectArchetype = "SOCIAL_THEORY", examDateTimeEpochMillis = 1L, createdAtEpochMillis = 1L)
        )
        val documentId = database.sourceDocumentDao().insert(
            SourceDocumentEntity(campaignId = campaignId, sourceType = "PDF", localPath = "/$name.pdf", createdAtEpochMillis = 1L, pageCount = 2)
        )
        campaignId to documentId
    }

    private fun analysis(campaignId: Long, documentId: Long, vararg titles: String) = StudyAnalysis(
        campaignId = campaignId,
        summary = "Summary for $campaignId",
        priorities = titles.mapIndexed { index, title ->
            StudyPriority(
                orderIndex = index,
                title = title,
                triage = if (index == 0) TriageLevel.CODE_RED else TriageLevel.CODE_YELLOW,
                bullets = listOf("$title bullet 1", "$title bullet 2"),
                notes = if (index == 0) "overflow" else null,
                sourceRefs = listOf(SourceRef(documentId, 0, index), SourceRef(documentId, 1, 0))
            )
        },
        discardedGreenCount = 3,
        analyzedPageCount = 2,
        lowTextPageCount = 1,
        sourceFingerprint = "fp-$campaignId",
        modelId = "model",
        generatedAt = Instant.ofEpochMilli(1234L)
    )

    @Test
    fun analysisIsScopedToItsCampaignAndRoundTrips() = runBlocking {
        val (campaignA, documentA) = insertCampaignWithDocument("A")
        val (campaignB, documentB) = insertCampaignWithDocument("B")
        val analysisA = analysis(campaignA, documentA, "A red", "A yellow")
        val analysisB = analysis(campaignB, documentB, "B red")

        repository.replaceAnalysis(analysisA)
        repository.replaceAnalysis(analysisB)

        assertEquals(analysisA, repository.getAnalysisForCampaign(campaignA))
        assertEquals(analysisB, repository.getAnalysisForCampaign(campaignB))
        assertNull(repository.getAnalysisForCampaign(999L))
    }

    @Test
    fun regenerationReplacesPrioritiesAndRefsWithoutDuplicates() = runBlocking {
        val (campaignId, documentId) = insertCampaignWithDocument("A")
        val (otherCampaign, otherDocument) = insertCampaignWithDocument("B")
        repository.replaceAnalysis(analysis(otherCampaign, otherDocument, "Other"))

        repository.replaceAnalysis(analysis(campaignId, documentId, "Old 1", "Old 2", "Old 3"))
        repository.replaceAnalysis(analysis(campaignId, documentId, "New 1"))

        val dao = database.studyAnalysisDao()
        assertEquals(listOf("New 1"), dao.getPrioritiesForCampaign(campaignId).map { it.title })
        assertEquals(2, dao.getSourceRefsForCampaign(campaignId).size)
        // The other campaign's analysis is untouched by this campaign's regeneration.
        assertEquals(listOf("Other"), dao.getPrioritiesForCampaign(otherCampaign).map { it.title })
        assertEquals(2, dao.getSourceRefsForCampaign(otherCampaign).size)
    }
}
