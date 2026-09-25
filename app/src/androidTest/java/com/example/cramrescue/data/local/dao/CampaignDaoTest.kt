package com.example.cramrescue.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cramrescue.data.local.database.AppDatabase
import com.example.cramrescue.data.local.entity.CampaignEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CampaignDaoTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private val databaseName = "test-cramrescue.db"

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

    @Test
    fun insertAndReadCampaign() = runBlocking {
        val entity = CampaignEntity(
            name = "Kinh Te Vi Mo",
            subjectArchetype = "SOCIAL_THEORY",
            examDateTimeEpochMillis = 1_000_000L,
            createdAtEpochMillis = 500_000L
        )
        val id = database.campaignDao().insert(entity)

        val loaded = database.campaignDao().getById(id)

        assertNotNull(loaded)
        assertEquals("Kinh Te Vi Mo", loaded?.name)
        assertEquals("SOCIAL_THEORY", loaded?.subjectArchetype)
    }

    @Test
    fun campaignSurvivesDatabaseRecreation() = runBlocking {
        val entity = CampaignEntity(
            name = "Duoc Ly",
            subjectArchetype = "NATURAL_COMPUTATION",
            examDateTimeEpochMillis = 2_000_000L,
            createdAtEpochMillis = 1_500_000L
        )
        val id = database.campaignDao().insert(entity)
        database.close()

        // Simulate an app/process restart by opening a fresh database instance against the same file.
        val recreatedDatabase = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).build()
        val reloaded = recreatedDatabase.campaignDao().getById(id)

        assertNotNull(reloaded)
        assertEquals("Duoc Ly", reloaded?.name)
        recreatedDatabase.close()
    }

    @Test
    fun getAllReturnsEveryCampaignWithDistinctIdsAndEmptyWhenNone() = runBlocking {
        assertEquals(emptyList<CampaignEntity>(), database.campaignDao().getAll())
        val a = database.campaignDao().insert(CampaignEntity(name = "A", subjectArchetype = "SOCIAL_THEORY", examDateTimeEpochMillis = 1_000_000L, createdAtEpochMillis = 500_000L))
        val b = database.campaignDao().insert(CampaignEntity(name = "B", subjectArchetype = "LANGUAGE", examDateTimeEpochMillis = 2_000_000L, createdAtEpochMillis = 600_000L))

        val all = database.campaignDao().getAll()
        assertEquals(setOf(a, b), all.map { it.id }.toSet())
        assertEquals(setOf("A", "B"), all.map { it.name }.toSet())
    }

    @Test
    fun updateChangesOnlyTheTargetRowAndReportsMissingRows() = runBlocking {
        val dao = database.campaignDao()
        val a = dao.insert(CampaignEntity(name = "A", subjectArchetype = "SOCIAL_THEORY", examDateTimeEpochMillis = 1_000_000L, createdAtEpochMillis = 500_000L))
        val b = dao.insert(CampaignEntity(name = "B", subjectArchetype = "LANGUAGE", examDateTimeEpochMillis = 2_000_000L, createdAtEpochMillis = 600_000L))
        val before = dao.getById(b)

        assertEquals(1, dao.update(dao.getById(a)!!.copy(name = "A2", examDateTimeEpochMillis = 9_000_000L)))
        assertEquals("A2", dao.getById(a)?.name)
        assertEquals(9_000_000L, dao.getById(a)?.examDateTimeEpochMillis)
        assertEquals("SOCIAL_THEORY", dao.getById(a)?.subjectArchetype)
        assertEquals(before, dao.getById(b))
        assertEquals(0, dao.update(CampaignEntity(id = 999, name = "X", subjectArchetype = "LANGUAGE", examDateTimeEpochMillis = 1L, createdAtEpochMillis = 1L)))
    }
}
