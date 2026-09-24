package com.example.cramrescue.data.repository

import com.example.cramrescue.data.local.dao.MicroMissionDao
import com.example.cramrescue.data.local.entity.MicroMissionEntity
import com.example.cramrescue.domain.mission.MicroMission
import com.example.cramrescue.domain.repository.MicroMissionRepository
import java.time.Instant

private const val ID_SEPARATOR = ","

class MicroMissionRepositoryImpl(
    private val microMissionDao: MicroMissionDao
) : MicroMissionRepository {

    override suspend fun getMission(campaignId: Long): MicroMission? =
        microMissionDao.getForCampaign(campaignId)?.let { entity ->
            MicroMission(
                campaignId = entity.campaignId,
                // A malformed id is skipped rather than failing the whole Cockpit.
                cardIds = entity.cardIds.split(ID_SEPARATOR).mapNotNull { it.trim().toLongOrNull() },
                baselineAttemptId = entity.baselineAttemptId,
                startedAt = Instant.ofEpochMilli(entity.startedAtEpochMillis)
            )
        }

    override suspend fun saveMission(mission: MicroMission) {
        microMissionDao.upsert(
            MicroMissionEntity(
                campaignId = mission.campaignId,
                cardIds = mission.cardIds.joinToString(ID_SEPARATOR),
                baselineAttemptId = mission.baselineAttemptId,
                startedAtEpochMillis = mission.startedAt.toEpochMilli()
            )
        )
    }
}
