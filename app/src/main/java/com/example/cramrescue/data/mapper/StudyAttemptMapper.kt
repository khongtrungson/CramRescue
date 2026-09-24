package com.example.cramrescue.data.mapper

import com.example.cramrescue.data.local.entity.StudyAttemptEntity
import com.example.cramrescue.domain.model.RecallAssessment
import com.example.cramrescue.domain.model.StudyAttempt
import com.example.cramrescue.domain.model.TriageLevel
import java.time.Instant

fun StudyAttempt.toEntity(): StudyAttemptEntity = StudyAttemptEntity(
    id = id,
    campaignId = campaignId,
    microCardId = microCardId,
    triage = triage.name,
    cardTitle = cardTitle,
    assessment = assessment.name,
    attemptedAtEpochMillis = attemptedAt.toEpochMilli()
)

/** Null if a stored enum name is unknown — one bad row must not break reading the history. */
fun StudyAttemptEntity.toDomainOrNull(): StudyAttempt? {
    val triage = TriageLevel.entries.firstOrNull { it.name == triage } ?: return null
    val assessment = RecallAssessment.entries.firstOrNull { it.name == assessment } ?: return null
    return StudyAttempt(
        id = id,
        campaignId = campaignId,
        microCardId = microCardId,
        triage = triage,
        cardTitle = cardTitle,
        assessment = assessment,
        attemptedAt = Instant.ofEpochMilli(attemptedAtEpochMillis)
    )
}
