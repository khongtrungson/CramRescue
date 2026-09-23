package com.example.cramrescue.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cramrescue.data.local.database.AppDatabase
import com.example.cramrescue.data.local.entity.CampaignEntity
import com.example.cramrescue.data.local.entity.SourceDocumentEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceDocumentDaoTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private val databaseName = "test-source-documents-cramrescue.db"

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
            CampaignEntity(
                name = name,
                subjectArchetype = "SOCIAL_THEORY",
                examDateTimeEpochMillis = 1_000_000L,
                createdAtEpochMillis = 500_000L
            )
        )
    }

    @Test
    fun documentsAreScopedToTheirOwningCampaign() = runBlocking {
        val campaignAId = insertCampaign("Campaign A")
        val campaignBId = insertCampaign("Campaign B")

        val doc1Id = database.sourceDocumentDao().insert(
            SourceDocumentEntity(campaignId = campaignAId, sourceType = "GALLERY", localPath = "/a/1.jpg", createdAtEpochMillis = 1L)
        )
        val doc2Id = database.sourceDocumentDao().insert(
            SourceDocumentEntity(campaignId = campaignAId, sourceType = "CAMERA", localPath = "/a/2.jpg", createdAtEpochMillis = 2L)
        )
        database.sourceDocumentDao().insert(
            SourceDocumentEntity(campaignId = campaignBId, sourceType = "GALLERY", localPath = "/b/1.jpg", createdAtEpochMillis = 3L)
        )

        val campaignADocs = database.sourceDocumentDao().getForCampaign(campaignAId)
        val campaignBDocs = database.sourceDocumentDao().getForCampaign(campaignBId)

        assertEquals(2, campaignADocs.size)
        assertTrue(campaignADocs.any { it.id == doc1Id })
        assertTrue(campaignADocs.any { it.id == doc2Id })
        assertTrue(campaignADocs.none { it.campaignId == campaignBId })

        assertEquals(1, campaignBDocs.size)
        assertEquals("/b/1.jpg", campaignBDocs[0].localPath)
    }

    @Test
    fun campaignWithNoDocumentsReturnsEmptyList() = runBlocking {
        val campaignId = insertCampaign("Empty Campaign")

        val documents = database.sourceDocumentDao().getForCampaign(campaignId)

        assertTrue(documents.isEmpty())
    }
}
