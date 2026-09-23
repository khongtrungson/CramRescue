package com.example.cramrescue.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

// One-to-one with SourceDocumentEntity: documentId is both the primary key and the foreign key,
// so SQLite's implicit primary-key index already covers the foreign key lookup.
@Entity(
    tableName = "ocr_results",
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
    @PrimaryKey val documentId: Long,
    val sourceWidthPx: Int,
    val sourceHeightPx: Int,
    val characterCount: Int,
    val isLowText: Boolean,
    val processedAtEpochMillis: Long
)
