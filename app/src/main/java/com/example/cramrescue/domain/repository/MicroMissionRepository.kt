package com.example.cramrescue.domain.repository

import com.example.cramrescue.domain.mission.MicroMission

interface MicroMissionRepository {
    /** The campaign's most recently started mission (in progress or finished), if any. */
    suspend fun getMission(campaignId: Long): MicroMission?

    /** Replaces the campaign's mission (one per campaign). */
    suspend fun saveMission(mission: MicroMission)
}
