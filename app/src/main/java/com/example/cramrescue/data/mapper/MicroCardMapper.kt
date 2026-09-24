package com.example.cramrescue.data.mapper

import com.example.cramrescue.data.local.entity.MicroCardEntity
import com.example.cramrescue.data.local.entity.MicroCardSourceRefEntity
import com.example.cramrescue.domain.model.MicroCard
import com.example.cramrescue.domain.model.SourceRef
import com.example.cramrescue.domain.model.TriageLevel
import java.time.Instant

private const val BULLET_SEPARATOR = "\n"

fun MicroCard.toEntity(): Pair<MicroCardEntity, List<MicroCardSourceRefEntity>> =
    MicroCardEntity(
        id = id,
        campaignId = campaignId,
        analysisGeneratedAtEpochMillis = analysisGeneratedAt.toEpochMilli(),
        orderIndex = orderIndex,
        triage = triage.name,
        title = title,
        bullets = bullets.joinToString(BULLET_SEPARATOR),
        notes = notes,
        createdAtEpochMillis = createdAt.toEpochMilli()
    ) to sourceRefs.map { ref ->
        MicroCardSourceRefEntity(
            microCardId = id,
            documentId = ref.documentId,
            pageIndex = ref.pageIndex,
            blockOrderIndex = ref.blockOrderIndex
        )
    }

fun MicroCardEntity.toDomain(refs: List<MicroCardSourceRefEntity>): MicroCard = MicroCard(
    id = id,
    campaignId = campaignId,
    analysisGeneratedAt = Instant.ofEpochMilli(analysisGeneratedAtEpochMillis),
    orderIndex = orderIndex,
    triage = TriageLevel.valueOf(triage),
    title = title,
    bullets = bullets.split(BULLET_SEPARATOR).filter { it.isNotBlank() },
    notes = notes,
    sourceRefs = refs.map { SourceRef(it.documentId, it.pageIndex, it.blockOrderIndex) },
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis)
)
