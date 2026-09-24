package com.example.cramrescue.data.repository

import com.example.cramrescue.data.local.dao.StudyAttemptDao
import com.example.cramrescue.data.mapper.toDomainOrNull
import com.example.cramrescue.data.mapper.toEntity
import com.example.cramrescue.domain.model.StudyAttempt
import com.example.cramrescue.domain.repository.StudyAttemptRepository

class StudyAttemptRepositoryImpl(
    private val studyAttemptDao: StudyAttemptDao
) : StudyAttemptRepository {

    override suspend fun recordAttempt(attempt: StudyAttempt): Long =
        studyAttemptDao.insert(attempt.toEntity().copy(id = 0L))

    override suspend fun getAttemptsForCampaign(campaignId: Long): List<StudyAttempt> =
        studyAttemptDao.getForCampaign(campaignId).mapNotNull { it.toDomainOrNull() }

    override suspend fun getAttemptsForCard(campaignId: Long, microCardId: Long): List<StudyAttempt> =
        studyAttemptDao.getForCard(campaignId, microCardId).mapNotNull { it.toDomainOrNull() }
}
