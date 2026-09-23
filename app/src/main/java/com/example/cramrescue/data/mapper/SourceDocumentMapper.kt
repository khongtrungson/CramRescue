package com.example.cramrescue.data.mapper

import com.example.cramrescue.data.local.entity.SourceDocumentEntity
import com.example.cramrescue.domain.model.SourceDocument
import com.example.cramrescue.domain.model.SourceType
import java.time.Instant

fun SourceDocument.toEntity(): SourceDocumentEntity = SourceDocumentEntity(
    id = id,
    campaignId = campaignId,
    sourceType = sourceType.name,
    localPath = localPath,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    pageCount = pageCount,
    displayName = displayName
)

fun SourceDocumentEntity.toDomain(): SourceDocument = SourceDocument(
    id = id,
    campaignId = campaignId,
    sourceType = SourceType.valueOf(sourceType),
    localPath = localPath,
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    pageCount = pageCount,
    displayName = displayName
)
