package com.example.cramrescue.presentation.grounding

import com.example.cramrescue.domain.model.BoundingBox
import kotlin.math.max
import kotlin.math.min

/** A rectangle in display (container / Canvas) pixels. Never persisted. */
data class DisplayRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

/**
 * Where the source (OCR) image lands inside a container under aspect-preserving Fit, centered:
 * displayed = offset + source * scale, for both axes.
 */
data class FitTransform(val scale: Float, val offsetX: Float, val offsetY: Float) {
    fun map(x: Float, y: Float): Pair<Float, Float> = (offsetX + x * scale) to (offsetY + y * scale)
}

/** A view (zoom/pan) transform applied equally to the page and its highlights: p' = p * zoom + t. */
data class ViewTransform(val zoom: Float, val translationX: Float, val translationY: Float)

/**
 * Pure geometry for Source Grounding, independent of Room, Compose, Android and the network.
 *
 * Coordinate spaces:
 *  1. OCR source space — integer pixels of the exact image ML Kit processed
 *     (sourceWidthPx x sourceHeightPx, persisted per page; the upright image for photos, the
 *     rendered page bitmap for PDFs). Persisted bounding boxes live here.
 *  2. Container space — the viewer's size in display pixels. The source is fitted into it.
 *  3. View space — container space after the user's/auto-focus zoom and pan.
 * The viewer bitmap may have any resolution: it is drawn into [contentRect], i.e. the same
 * rectangle the boxes are mapped into, so its own pixel size never enters the mapping.
 */
object SourceCoordinateMapper {

    /** Null when either size is not positive — nothing can be mapped. */
    fun fit(sourceWidth: Int, sourceHeight: Int, containerWidth: Float, containerHeight: Float): FitTransform? {
        if (sourceWidth <= 0 || sourceHeight <= 0 || containerWidth <= 0f || containerHeight <= 0f) return null
        val scale = min(containerWidth / sourceWidth, containerHeight / sourceHeight)
        return FitTransform(
            scale = scale,
            offsetX = (containerWidth - sourceWidth * scale) / 2f,
            offsetY = (containerHeight - sourceHeight * scale) / 2f
        )
    }

    /** The displayed page/image rectangle (letterbox excluded). */
    fun contentRect(sourceWidth: Int, sourceHeight: Int, transform: FitTransform): DisplayRect =
        DisplayRect(
            left = transform.offsetX,
            top = transform.offsetY,
            right = transform.offsetX + sourceWidth * transform.scale,
            bottom = transform.offsetY + sourceHeight * transform.scale
        )

    /**
     * Clamps a persisted box to the source bounds. Null if nothing valid remains (e.g. inverted,
     * empty, or entirely outside) — such a box is skipped, never drawn somewhere arbitrary.
     */
    fun sanitize(box: BoundingBox, sourceWidth: Int, sourceHeight: Int): BoundingBox? {
        if (sourceWidth <= 0 || sourceHeight <= 0) return null
        val clamped = BoundingBox(
            left = box.left.coerceIn(0, sourceWidth),
            top = box.top.coerceIn(0, sourceHeight),
            right = box.right.coerceIn(0, sourceWidth),
            bottom = box.bottom.coerceIn(0, sourceHeight)
        )
        return clamped.takeIf { it.left < it.right && it.top < it.bottom }
    }

    fun map(box: BoundingBox, transform: FitTransform): DisplayRect {
        val (left, top) = transform.map(box.left.toFloat(), box.top.toFloat())
        val (right, bottom) = transform.map(box.right.toFloat(), box.bottom.toFloat())
        return DisplayRect(left, top, right, bottom)
    }

    /** Each valid box mapped independently (draw geometry). Invalid boxes are dropped. */
    fun mapAll(boxes: List<BoundingBox>, sourceWidth: Int, sourceHeight: Int, transform: FitTransform): List<DisplayRect> =
        boxes.mapNotNull { sanitize(it, sourceWidth, sourceHeight) }.map { map(it, transform) }

    /** Focus geometry: the union of the highlight rectangles — separate from what is drawn. */
    fun union(rects: List<DisplayRect>): DisplayRect? {
        if (rects.isEmpty()) return null
        return DisplayRect(
            left = rects.minOf { it.left },
            top = rects.minOf { it.top },
            right = rects.maxOf { it.right },
            bottom = rects.maxOf { it.bottom }
        )
    }

    /** Visual breathing room around a correctly mapped rectangle, clipped to [bounds]. Not a correction. */
    fun pad(rect: DisplayRect, padding: Float, bounds: DisplayRect): DisplayRect = DisplayRect(
        left = max(bounds.left, rect.left - padding),
        top = max(bounds.top, rect.top - padding),
        right = min(bounds.right, rect.right + padding),
        bottom = min(bounds.bottom, rect.bottom + padding)
    )

    /**
     * Deterministic auto-focus: zoom so [focus] fills most of the container (between 1x and
     * [maxZoom]), centered on it, without panning past the page edges.
     */
    fun focusTransform(
        focus: DisplayRect,
        content: DisplayRect,
        containerWidth: Float,
        containerHeight: Float,
        maxZoom: Float = 4f,
        fill: Float = 0.9f
    ): ViewTransform {
        if (focus.width <= 0f || focus.height <= 0f || containerWidth <= 0f || containerHeight <= 0f) {
            return ViewTransform(1f, 0f, 0f)
        }
        val zoom = min(containerWidth * fill / focus.width, containerHeight * fill / focus.height).coerceIn(1f, maxZoom)
        return ViewTransform(
            zoom = zoom,
            translationX = clampTranslation(containerWidth / 2f - focus.centerX * zoom, content.left, content.right, zoom, containerWidth),
            translationY = clampTranslation(containerHeight / 2f - focus.centerY * zoom, content.top, content.bottom, zoom, containerHeight)
        )
    }

    /** Keeps the zoomed page covering the container along one axis, or centered when it is smaller. */
    fun clampTranslation(desired: Float, contentStart: Float, contentEnd: Float, zoom: Float, container: Float): Float {
        val scaledSize = (contentEnd - contentStart) * zoom
        if (scaledSize <= container) return (container - scaledSize) / 2f - contentStart * zoom
        val min = container - contentEnd * zoom
        val max = -contentStart * zoom
        return desired.coerceIn(min, max)
    }

    /** A point in container space after the view transform. */
    fun applyView(x: Float, y: Float, view: ViewTransform): Pair<Float, Float> =
        (x * view.zoom + view.translationX) to (y * view.zoom + view.translationY)
}
