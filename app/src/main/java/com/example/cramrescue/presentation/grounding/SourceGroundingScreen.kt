package com.example.cramrescue.presentation.grounding

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cramrescue.domain.grounding.GroundedSource
import com.example.cramrescue.domain.grounding.GroundingUnavailableReason
import com.example.cramrescue.domain.model.BoundingBox
import com.example.cramrescue.domain.model.SourceType
import com.example.cramrescue.ui.theme.GroundingHighlight
import kotlin.math.roundToInt

// Visual breathing room (display px) around the focus union when auto-focusing — not a
// coordinate correction; the drawn boxes themselves are never padded.
private const val FOCUS_PADDING_PX = 24f
private const val MAX_USER_ZOOM = 6f

@Composable
fun SourceGroundingScreen(
    viewModelFactory: SourceGroundingViewModelFactory,
    onBack: () -> Unit
) {
    val viewModel: SourceGroundingViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← Quay lại") }
        Text(
            text = "Đối chiếu nguồn",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))

        when (val state = uiState) {
            SourceGroundingUiState.Loading -> CircularProgressIndicator()
            is SourceGroundingUiState.Unavailable -> Message(unavailableMessage(state.reason))
            is SourceGroundingUiState.RenderError -> Message(renderErrorMessage(state.failure))
            is SourceGroundingUiState.Success -> GroundedSourceContent(state.source, state.bitmap)
        }
    }
}

@Composable
private fun ColumnScope.GroundedSourceContent(source: GroundedSource, bitmap: Bitmap) {
    val name = source.displayName ?: if (source.sourceType == SourceType.PDF) "Tài liệu PDF" else "Ảnh tài liệu"
    val page = if (source.sourceType == SourceType.PDF) " · Trang ${source.pageIndex + 1}/${source.pageCount}" else ""
    Text(
        text = "$name$page · ${source.blocks.size} khối được trích dẫn",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (source.cardStale) Warning("Thẻ thuộc bản phân tích cũ — tài liệu của chiến dịch đã thay đổi.")
    if (source.missingBlockCount > 0) {
        Warning("${source.missingBlockCount} khối được trích dẫn không còn trong kết quả OCR và không được đánh dấu.")
    }
    Spacer(modifier = Modifier.height(8.dp))

    GroundingViewer(
        bitmap = bitmap,
        sourceWidthPx = source.sourceWidthPx,
        sourceHeightPx = source.sourceHeightPx,
        boxes = source.blocks.map { it.boundingBox },
        modifier = Modifier.fillMaxWidth().weight(1f)
    )

    Spacer(modifier = Modifier.height(8.dp))
    Text("Văn bản nhận dạng", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(modifier = Modifier.heightIn(max = 140.dp).verticalScroll(rememberScrollState())) {
        source.blocks.forEach {
            Text("“${it.text}”", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

/**
 * Draws [bitmap] fitted into the available space and highlights [boxes] (OCR source pixels).
 * All geometry comes from [SourceCoordinateMapper] in pixels; it is recomputed whenever the
 * container size changes (rotation, window resize). The page and boxes share one view transform,
 * so pinch-to-zoom (FR-15) and auto-focus can never separate a box from its text.
 */
@Composable
fun GroundingViewer(
    bitmap: Bitmap,
    sourceWidthPx: Int,
    sourceHeightPx: Int,
    boxes: List<BoundingBox>,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = true
) {
    BoxWithConstraints(modifier = modifier.clipToBounds().testTag("grounding-viewer")) {
        val containerWidth = constraints.maxWidth.toFloat()
        val containerHeight = constraints.maxHeight.toFloat()
        val geometry = remember(containerWidth, containerHeight, sourceWidthPx, sourceHeightPx, boxes, autoFocus) {
            SourceCoordinateMapper.fit(sourceWidthPx, sourceHeightPx, containerWidth, containerHeight)?.let { fit ->
                val content = SourceCoordinateMapper.contentRect(sourceWidthPx, sourceHeightPx, fit)
                val rects = SourceCoordinateMapper.mapAll(boxes, sourceWidthPx, sourceHeightPx, fit)
                val focus = SourceCoordinateMapper.union(rects)?.let { SourceCoordinateMapper.pad(it, FOCUS_PADDING_PX, content) }
                val initialView = if (autoFocus && focus != null) {
                    SourceCoordinateMapper.focusTransform(focus, content, containerWidth, containerHeight)
                } else {
                    ViewTransform(1f, 0f, 0f)
                }
                ViewerGeometry(content, rects, initialView)
            }
        } ?: return@BoxWithConstraints

        var view by remember(geometry) { mutableStateOf(geometry.initialView) }
        val image = remember(bitmap) { bitmap.asImageBitmap() }
        val fill = GroundingHighlight.copy(alpha = 0.22f)
        val stroke = GroundingHighlight

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(geometry) {
                    detectTransformGestures { centroid, pan, zoomChange, _ ->
                        val newZoom = (view.zoom * zoomChange).coerceIn(1f, MAX_USER_ZOOM)
                        val ratio = newZoom / view.zoom
                        val content = geometry.content
                        view = ViewTransform(
                            zoom = newZoom,
                            translationX = SourceCoordinateMapper.clampTranslation(
                                centroid.x - (centroid.x - view.translationX) * ratio + pan.x,
                                content.left, content.right, newZoom, containerWidth
                            ),
                            translationY = SourceCoordinateMapper.clampTranslation(
                                centroid.y - (centroid.y - view.translationY) * ratio + pan.y,
                                content.top, content.bottom, newZoom, containerHeight
                            )
                        )
                    }
                }
        ) {
            withTransform({
                translate(view.translationX, view.translationY)
                scale(view.zoom, view.zoom, pivot = Offset.Zero)
            }) {
                val content = geometry.content
                drawImage(
                    image = image,
                    dstOffset = IntOffset(content.left.roundToInt(), content.top.roundToInt()),
                    dstSize = IntSize(content.width.roundToInt(), content.height.roundToInt())
                )
                val strokeWidth = 3f / view.zoom // constant on-screen thickness
                geometry.rects.forEach { rect ->
                    val topLeft = Offset(rect.left, rect.top)
                    val size = Size(rect.width, rect.height)
                    drawRect(color = fill, topLeft = topLeft, size = size)
                    drawRect(color = stroke, topLeft = topLeft, size = size, style = Stroke(width = strokeWidth))
                }
            }
        }
    }
}

private data class ViewerGeometry(val content: DisplayRect, val rects: List<DisplayRect>, val initialView: ViewTransform)

@Composable
private fun Message(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
private fun Warning(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

private fun unavailableMessage(reason: GroundingUnavailableReason): String = when (reason) {
    GroundingUnavailableReason.CARD_NOT_FOUND -> "Không tìm thấy thẻ này trong chiến dịch."
    GroundingUnavailableReason.NOT_CITED_BY_CARD,
    GroundingUnavailableReason.SOURCE_DELETED -> "Nguồn này không còn khả dụng."
    GroundingUnavailableReason.PAGE_MISSING -> "Trang nguồn không còn trong kết quả nhận dạng."
    GroundingUnavailableReason.INVALID_DIMENSIONS -> "Dữ liệu kích thước trang nguồn không hợp lệ."
    GroundingUnavailableReason.SOURCE_CHANGED ->
        "Tài liệu đã được nhận dạng lại sau khi tạo thẻ, nên không thể xác định chính xác vùng nguồn. Hãy phân tích và tạo lại thẻ."
    GroundingUnavailableReason.BLOCKS_MISSING -> "Các khối văn bản được trích dẫn không còn tồn tại."
}

private fun renderErrorMessage(failure: SourceLoadFailure): String = when (failure) {
    SourceLoadFailure.FILE_MISSING -> "Tệp nguồn không còn trên thiết bị."
    SourceLoadFailure.PAGE_OUT_OF_RANGE -> "Trang này không tồn tại trong tệp PDF."
    SourceLoadFailure.RENDER_FAILED -> "Không hiển thị được trang PDF."
    SourceLoadFailure.DECODE_FAILED -> "Không đọc được ảnh nguồn."
    SourceLoadFailure.GEOMETRY_MISMATCH ->
        "Ảnh nguồn không khớp với dữ liệu nhận dạng, nên không thể đánh dấu chính xác."
}
