package com.example.cramrescue.domain.model

import java.time.Instant

enum class SourceType { CAMERA, GALLERY }

/**
 * A single imported study material page, owned by exactly one Campaign. Milestone 4 only
 * ingests and stores the image; Milestone 5 will run OCR against [localPath].
 */
data class SourceDocument(
    val id: Long = 0L,
    val campaignId: Long,
    val sourceType: SourceType,
    val localPath: String,
    val createdAt: Instant
)
