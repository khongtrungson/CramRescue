package com.example.cramrescue.data.mapper

import com.example.cramrescue.data.local.entity.OcrResultEntity
import com.example.cramrescue.data.local.entity.OcrTextBlockEntity
import com.example.cramrescue.domain.model.BoundingBox
import com.example.cramrescue.domain.model.OcrPageResult
import com.example.cramrescue.domain.model.OcrTextBlock
import java.time.Instant

fun OcrTextBlock.toEntity(): OcrTextBlockEntity = OcrTextBlockEntity(
    id = id,
    documentId = documentId,
    pageIndex = pageIndex,
    orderIndex = orderIndex,
    text = text,
    left = boundingBox.left,
    top = boundingBox.top,
    right = boundingBox.right,
    bottom = boundingBox.bottom
)

fun OcrTextBlockEntity.toDomain(): OcrTextBlock = OcrTextBlock(
    id = id,
    documentId = documentId,
    pageIndex = pageIndex,
    orderIndex = orderIndex,
    text = text,
    boundingBox = BoundingBox(left = left, top = top, right = right, bottom = bottom)
)

fun OcrPageResult.toEntity(): OcrResultEntity = OcrResultEntity(
    documentId = documentId,
    pageIndex = pageIndex,
    sourceWidthPx = sourceWidthPx,
    sourceHeightPx = sourceHeightPx,
    characterCount = characterCount,
    isLowText = isLowText,
    processedAtEpochMillis = processedAt.toEpochMilli()
)

fun OcrResultEntity.toDomain(blocks: List<OcrTextBlock>): OcrPageResult = OcrPageResult(
    documentId = documentId,
    pageIndex = pageIndex,
    sourceWidthPx = sourceWidthPx,
    sourceHeightPx = sourceHeightPx,
    characterCount = characterCount,
    isLowText = isLowText,
    processedAt = Instant.ofEpochMilli(processedAtEpochMillis),
    blocks = blocks
)
