package com.example.cramrescue.domain.ocr

import com.example.cramrescue.domain.model.BoundingBox

/**
 * On-device text recognition boundary. This is the only contract application/domain code should
 * depend on for OCR — implementations (e.g. ML Kit) must not leak their own types past it.
 */
interface TextRecognitionEngine {
    suspend fun recognize(imagePath: String): RecognizedText
}

/** One raw recognized block, before it has a persistence identity. */
data class RecognizedTextBlock(
    val text: String,
    val boundingBox: BoundingBox
)

/** Coordinate convention: see [com.example.cramrescue.domain.model.OcrResult]. */
data class RecognizedText(
    val sourceWidthPx: Int,
    val sourceHeightPx: Int,
    val blocks: List<RecognizedTextBlock>
)
