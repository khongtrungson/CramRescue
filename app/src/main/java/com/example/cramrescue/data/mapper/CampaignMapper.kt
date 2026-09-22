package com.example.cramrescue.data.mapper

import com.example.cramrescue.data.local.entity.CampaignEntity
import com.example.cramrescue.domain.model.Campaign
import com.example.cramrescue.domain.model.SubjectArchetype
import java.time.Instant

fun Campaign.toEntity(): CampaignEntity = CampaignEntity(
    id = id,
    name = name,
    subjectArchetype = subjectArchetype.name,
    examDateTimeEpochMillis = examDateTime.toEpochMilli(),
    createdAtEpochMillis = createdAt.toEpochMilli()
)

fun CampaignEntity.toDomain(): Campaign = Campaign(
    id = id,
    name = name,
    subjectArchetype = SubjectArchetype.valueOf(subjectArchetype),
    examDateTime = Instant.ofEpochMilli(examDateTimeEpochMillis),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis)
)
