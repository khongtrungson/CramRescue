package com.example.cramrescue.presentation.grounding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cramrescue.domain.grounding.TextGroundedSource
import com.example.cramrescue.domain.model.TextBlockKind
import com.example.cramrescue.ui.theme.GroundingHighlight

/**
 * Word source grounding (FR-14 "Text Snippet"; OQ-1 resolved as a segmented text view): the
 * document's paragraphs with the cited ones highlighted and scrolled into view. No page image or
 * bounding box exists for Word, and none is simulated.
 */
@Composable
internal fun ColumnScope.TextGroundedSourceContent(source: TextGroundedSource) {
    val name = source.displayName ?: "Tài liệu Word"
    Text(
        text = "$name · văn bản Word · ${source.citedOrderIndices.size} đoạn được trích dẫn",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = "Tài liệu Word không có trang in; nguồn được hiển thị dưới dạng đoạn văn bản trích dẫn.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (source.cardStale) {
        Text("Thẻ thuộc bản phân tích cũ — tài liệu của chiến dịch đã thay đổi.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    if (source.missingBlockCount > 0) {
        Text(
            "${source.missingBlockCount} đoạn được trích dẫn không còn trong tài liệu và không được đánh dấu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    Spacer(modifier = Modifier.height(8.dp))

    val listState = rememberLazyListState()
    val firstCited = source.blocks.indexOfFirst { it.orderIndex in source.citedOrderIndices }
    LaunchedEffect(source.documentId, firstCited) {
        if (firstCited >= 0) listState.scrollToItem(firstCited)
    }
    val highlight = GroundingHighlight.copy(alpha = 0.22f)

    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f).testTag("word-source")) {
        itemsIndexed(source.blocks, key = { _, block -> block.orderIndex }) { _, block ->
            val cited = block.orderIndex in source.citedOrderIndices
            val shape = RoundedCornerShape(6.dp)
            val base = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            val itemModifier = if (cited) base.background(highlight, shape).padding(8.dp) else base.padding(horizontal = 8.dp)
            androidx.compose.foundation.layout.Column(modifier = itemModifier) {
                if (cited) {
                    Text(
                        text = "Đoạn ${block.orderIndex + 1} — được trích dẫn",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = block.text,
                    style = if (block.kind == TextBlockKind.HEADING) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (block.kind == TextBlockKind.HEADING) FontWeight.Bold else null,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}
