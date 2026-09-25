package com.example.cramrescue.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey

// Milestone 13. Paragraph-segmented text of a Word (.docx) document (FR-3). Deliberately separate
// from ocr_results/ocr_text_blocks: Word has no pages, pixel dimensions or bounding boxes, and
// storing it there would require inventing them. Identity is (documentId, orderIndex); the
// composite primary key's leading documentId also indexes the foreign key.
@Entity(
    tableName = "document_text_blocks",
    primaryKeys = ["documentId", "orderIndex"],
    foreignKeys = [
        ForeignKey(
            entity = SourceDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DocumentTextBlockEntity(
    val documentId: Long,
    val orderIndex: Int,
    val kind: String,
    val text: String,
    val extractedAtEpochMillis: Long
)
