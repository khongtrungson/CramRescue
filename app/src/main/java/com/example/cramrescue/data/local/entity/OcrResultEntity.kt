package com.example.cramrescue.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey

// One row per (document, page). Milestone 5 stored one row per document; Milestone 6 made this
// per-page so a multi-page PDF keeps each page's own render dimensions and its own low-text
// quality flag (requirements.md FR-4 evaluates the <50 character warning per page).
//
// The composite primary key starts with documentId, so its index also covers the foreign key.
@Entity(
    tableName = "ocr_results",
    primaryKeys = ["documentId", "pageIndex"],
    foreignKeys = [
        ForeignKey(
            entity = SourceDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class OcrResultEntity(
    val documentId: Long,
    val pageIndex: Int,
    val sourceWidthPx: Int,
    val sourceHeightPx: Int,
    val characterCount: Int,
    val isLowText: Boolean,
    val processedAtEpochMillis: Long
)
