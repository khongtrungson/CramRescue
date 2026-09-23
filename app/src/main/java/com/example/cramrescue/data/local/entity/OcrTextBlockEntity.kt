package com.example.cramrescue.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// left/top/right/bottom are pixel coordinates relative to a bitmap of size
// sourceWidthPx x sourceHeightPx on the sibling OcrResultEntity row (same documentId).
@Entity(
    tableName = "ocr_text_blocks",
    foreignKeys = [
        ForeignKey(
            entity = SourceDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId")]
)
data class OcrTextBlockEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val documentId: Long,
    val pageIndex: Int,
    val orderIndex: Int,
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)
