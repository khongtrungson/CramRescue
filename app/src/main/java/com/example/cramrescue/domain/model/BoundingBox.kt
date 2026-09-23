package com.example.cramrescue.domain.model

/**
 * A rectangular text region, independent of any OCR library's geometry types.
 *
 * Coordinate convention: pixel coordinates relative to the source image as ML Kit received it
 * (see [OcrResult.sourceWidthPx]/[OcrResult.sourceHeightPx] for the space these are relative to).
 */
data class BoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)
