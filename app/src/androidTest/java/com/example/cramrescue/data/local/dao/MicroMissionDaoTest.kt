package com.example.cramrescue.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cramrescue.data.local.database.AppDatabase
import com.example.cramrescue.data.local.entity.CampaignEntity
import com.example.cramrescue.data.repository.MicroMissionRepositoryImpl
import com.example.cramrescue.domain.mission.MicroMission
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MicroMissionDaoTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var missions: MicroMissionRepositoryImpl
    private val databaseName = "test-missions-cramrescue.db"

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
        missions = MicroMissionRepositoryImpl(database.microMissionDao())
    }

    private fun campaign(name: String): Long = runBlocking {
        database.campaignDao().insert(
            CampaignEntity(name = name, subjectArchetype = "SOCIAL_THEORY", examDateTimeEpochMillis = 1L, createdAtEpochMillis = 1L)
        )
    }

    private fun mission(campaignId: Long, vararg ids: Long, baseline: Long = 0) =
        MicroMission(campaignId, ids.toList(), baseline, Instant.ofEpochMilli(1_000))

    @Test
    fun missionRoundTripsWithFrozenOrderAndIsScopedToItsCampaign() = runBlocking {
        val a = campaign("A")
        val b = campaign("B")
        missions.saveMission(mission(a, 7, 3, 9, baseline = 42))

        assertEquals(mission(a, 7, 3, 9, baseline = 42), missions.getMission(a))
        assertNull(missions.getMission(b))
    }

    @Test
    fun startingANewMissionReplacesThePreviousOne() = runBlocking {
        val a = campaign("A")
        missions.saveMission(mission(a, 1, 2))
        missions.saveMission(mission(a, 5, baseline = 8))

        assertEquals(mission(a, 5, baseline = 8), missions.getMission(a))
    }

    @Test
    fun deletingTheCampaignRemovesItsMission() = runBlocking {
        val a = campaign("A")
        missions.saveMission(mission(a, 1))

        database.openHelper.writableDatabase.execSQL("DELETE FROM campaigns WHERE id = $a")

        assertNull(missions.getMission(a))
    }

    @Test
    fun missionSurvivesReopeningTheDatabaseForResume() = runBlocking {
        val a = campaign("A")
        missions.saveMission(mission(a, 4, 2, baseline = 3))

        database.close()
        open()

        assertEquals(mission(a, 4, 2, baseline = 3), missions.getMission(a))
    }
}
