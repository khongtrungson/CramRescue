package com.example.cramrescue.domain.repository

import com.example.cramrescue.domain.model.StudyAttempt

interface StudyAttemptRepository {
    /** Appends one attempt (history is never overwritten). Returns its id. */
    suspend fun recordAttempt(attempt: StudyAttempt): Long

    /** Only that campaign's attempts, oldest first. */
    suspend fun getAttemptsForCampaign(campaignId: Long): List<StudyAttempt>

    /** One card's attempts within its campaign, oldest first. */
    suspend fun getAttemptsForCard(campaignId: Long, microCardId: Long): List<StudyAttempt>
}
