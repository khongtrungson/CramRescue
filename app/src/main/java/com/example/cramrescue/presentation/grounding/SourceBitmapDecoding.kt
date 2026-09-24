package com.example.cramrescue.presentation.grounding

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface

/**
 * Decodes a camera/gallery source image UPRIGHT (EXIF orientation applied), downsampled so its
 * longest side is at most about [maxDimension].
 *
 * Why EXIF matters: M5 OCR reads images with ML Kit's InputImage.fromFilePath, which applies the
 * file's EXIF orientation, so persisted bounding boxes and sourceWidth/HeightPx are in the upright
 * image's pixel space. BitmapFactory alone ignores EXIF, so a rotated photo would be drawn sideways
 * and every highlight would land in the wrong place. (Verified by SourceOrientationAuditTest.)
 */
internal fun decodeUprightSampledBitmap(path: String, maxDimension: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (bounds.outWidth / (sampleSize * 2) >= maxDimension || bounds.outHeight / (sampleSize * 2) >= maxDimension) {
        sampleSize *= 2
    }
    val raw = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sampleSize }) ?: return null

    val orientation = runCatching {
        ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val matrix = orientationMatrix(orientation) ?: return raw

    val upright = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
    if (upright !== raw) raw.recycle()
    return upright
}

/** The transform that makes an image with EXIF [orientation] upright; null when already upright. */
private fun orientationMatrix(orientation: Int): Matrix? = when (orientation) {
    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Matrix().apply { postScale(-1f, 1f) }
    ExifInterface.ORIENTATION_ROTATE_180 -> Matrix().apply { postRotate(180f) }
    ExifInterface.ORIENTATION_FLIP_VERTICAL -> Matrix().apply { postScale(1f, -1f) }
    ExifInterface.ORIENTATION_TRANSPOSE -> Matrix().apply { postRotate(90f); postScale(-1f, 1f) }
    ExifInterface.ORIENTATION_ROTATE_90 -> Matrix().apply { postRotate(90f) }
    ExifInterface.ORIENTATION_TRANSVERSE -> Matrix().apply { postRotate(-90f); postScale(-1f, 1f) }
    ExifInterface.ORIENTATION_ROTATE_270 -> Matrix().apply { postRotate(270f) }
    else -> null
}
