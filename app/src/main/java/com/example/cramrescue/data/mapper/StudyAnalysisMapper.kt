package com.example.cramrescue.data.mapper

import com.example.cramrescue.data.local.entity.AnalysisPriorityEntity
import com.example.cramrescue.data.local.entity.AnalysisSourceRefEntity
import com.example.cramrescue.data.local.entity.CampaignAnalysisEntity
import com.example.cramrescue.domain.model.SourceRef
import com.example.cramrescue.domain.model.StudyAnalysis
import com.example.cramrescue.domain.model.StudyPriority
import com.example.cramrescue.domain.model.TriageLevel
import java.time.Instant

private const val BULLET_SEPARATOR = "\n"

fun StudyAnalysis.toEntity(): CampaignAnalysisEntity = CampaignAnalysisEntity(
    campaignId = campaignId,
    summary = summary,
    discardedGreenCount = discardedGreenCount,
    analyzedPageCount = analyzedPageCount,
    lowTextPageCount = lowTextPageCount,
    sourceFingerprint = sourceFingerprint,
    modelId = modelId,
    generatedAtEpochMillis = generatedAt.toEpochMilli()
)

fun StudyPriority.toEntity(campaignId: Long): Pair<AnalysisPriorityEntity, List<AnalysisSourceRefEntity>> =
    AnalysisPriorityEntity(
        campaignId = campaignId,
        orderIndex = orderIndex,
        triage = triage.name,
        title = title,
        bullets = bullets.joinToString(BULLET_SEPARATOR),
        notes = notes
    ) to sourceRefs.map { ref ->
        AnalysisSourceRefEntity(
            priorityId = 0L,
            documentId = ref.documentId,
            pageIndex = ref.pageIndex,
            blockOrderIndex = ref.blockOrderIndex
        )
    }

fun AnalysisPriorityEntity.toDomain(refs: List<AnalysisSourceRefEntity>): StudyPriority = StudyPriority(
    orderIndex = orderIndex,
    title = title,
    triage = TriageLevel.valueOf(triage),
    bullets = bullets.split(BULLET_SEPARATOR).filter { it.isNotBlank() },
    notes = notes,
    sourceRefs = refs.map { SourceRef(it.documentId, it.pageIndex, it.blockOrderIndex) }
)

fun CampaignAnalysisEntity.toDomain(priorities: List<StudyPriority>): StudyAnalysis = StudyAnalysis(
    campaignId = campaignId,
    summary = summary,
    priorities = priorities,
    discardedGreenCount = discardedGreenCount,
    analyzedPageCount = analyzedPageCount,
    lowTextPageCount = lowTextPageCount,
    sourceFingerprint = sourceFingerprint,
    modelId = modelId,
    generatedAt = Instant.ofEpochMilli(generatedAtEpochMillis)
)
