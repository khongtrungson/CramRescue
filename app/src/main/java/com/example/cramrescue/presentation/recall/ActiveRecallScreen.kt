package com.example.cramrescue.presentation.recall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cramrescue.domain.model.RecallAssessment
import com.example.cramrescue.domain.recall.ClozeSegment
import com.example.cramrescue.presentation.analysis.TriageBadge

// Fixed-width placeholder, so the box doesn't hint at the hidden keyword's length.
private const val HIDDEN_PLACEHOLDER = "  ? ? ?  "

@Composable
fun ActiveRecallScreen(
    viewModelFactory: ActiveRecallViewModelFactory,
    onBack: () -> Unit,
    onOpenSource: (cardId: Long, documentId: Long, pageIndex: Int) -> Unit
) {
    val viewModel: ActiveRecallViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Quay lại") }
        Spacer(modifier = Modifier.height(16.dp))

        when (val state = uiState) {
            ActiveRecallUiState.Loading -> CircularProgressIndicator()
            is ActiveRecallUiState.NoCards -> Text(state.message, color = MaterialTheme.colorScheme.onBackground)
            is ActiveRecallUiState.Studying -> Studying(
                state = state,
                onReveal = viewModel::reveal,
                onAssess = viewModel::assess,
                onOpenSource = { documentId, pageIndex -> onOpenSource(state.cardId, documentId, pageIndex) }
            )
            is ActiveRecallUiState.Completed -> Completed(state, onRestart = viewModel::restart, onBack = onBack)
        }
    }
}

@Composable
private fun Studying(
    state: ActiveRecallUiState.Studying,
    onReveal: () -> Unit,
    onAssess: (RecallAssessment) -> Unit,
    onOpenSource: (documentId: Long, pageIndex: Int) -> Unit
) {
    Text(
        text = (if (state.isMission) "Nhiệm vụ · " else "") + "Thẻ ${state.position} / ${state.total}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (state.cardsStale) {
        Text(
            text = "Các thẻ này thuộc một bản phân tích cũ — tài liệu đã thay đổi.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(modifier = Modifier.height(12.dp))

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            TriageBadge(state.triage)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(16.dp))
            Question(state.question, revealed = state.revealed != null, onReveal = onReveal)
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    val revealed = state.revealed
    if (revealed == null) {
        Button(onClick = onReveal, modifier = Modifier.fillMaxWidth()) { Text("Hiện đáp án") }
        return
    }

    Answer(revealed, onOpenSource)

    Spacer(modifier = Modifier.height(20.dp))
    Text(
        text = "Bạn nhớ thẻ này đến đâu?",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onBackground
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { onAssess(RecallAssessment.NOT_REMEMBERED) },
            enabled = !state.isSaving,
            modifier = Modifier.weight(1f)
        ) { Text(RecallAssessment.NOT_REMEMBERED.label()) }
        OutlinedButton(
            onClick = { onAssess(RecallAssessment.PARTIAL) },
            enabled = !state.isSaving,
            modifier = Modifier.weight(1f)
        ) { Text(RecallAssessment.PARTIAL.label()) }
        // FR-9: marking a card mastered raises Pass Probability — light haptic feedback.
        val haptics = LocalHapticFeedback.current
        Button(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onAssess(RecallAssessment.MASTERED)
            },
            enabled = !state.isSaving,
            modifier = Modifier.weight(1f)
        ) { Text(RecallAssessment.MASTERED.label()) }
    }

    state.errorMessage?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun Question(question: QuestionUi, revealed: Boolean, onReveal: () -> Unit) {
    when (question) {
        is QuestionUi.General -> Text(
            text = "Hãy nhớ lại ${question.pointCount} ý chính của khái niệm này, rồi bấm \"Hiện đáp án\".",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        is QuestionUi.Cloze -> {
            val boxColor = MaterialTheme.colorScheme.primary
            val onBox = MaterialTheme.colorScheme.onPrimary
            val highlight = MaterialTheme.colorScheme.secondaryContainer
            val text = buildAnnotatedString {
                question.segments.forEach { segment ->
                    when (segment) {
                        is ClozeSegment.Visible -> append(segment.text)
                        is ClozeSegment.Hidden -> if (revealed) {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, background = highlight)) { append(segment.text) }
                        } else {
                            // FR-11: the keyword is hidden in a contrasting box; tapping it reveals.
                            // The hidden text itself is not in the composition before reveal.
                            withLink(LinkAnnotation.Clickable("reveal") { onReveal() }) {
                                withStyle(SpanStyle(background = boxColor, color = onBox)) { append(HIDDEN_PLACEHOLDER) }
                            }
                        }
                    }
                }
            }
            Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun Answer(revealed: RevealedUi, onOpenSource: (documentId: Long, pageIndex: Int) -> Unit) {
    if (revealed.answers.isNotEmpty()) {
        Text(
            text = "Đáp án: " + revealed.answers.joinToString(", "),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
    revealed.bullets.forEach { bullet ->
        Text(text = "• $bullet", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(4.dp))
    }
    revealed.notes?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Text("Ghi chú mở rộng: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (revealed.sourceGroups.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        // Reachable only after Reveal: the source text/highlight would give the answer away.
        revealed.sourceGroups.forEach { group ->
            TextButton(onClick = { onOpenSource(group.documentId, group.pageIndex) }) {
                Text(
                    text = "Đối chiếu nguồn: ${group.location} (${group.blockCount} khối)",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun Completed(state: ActiveRecallUiState.Completed, onRestart: () -> Unit, onBack: () -> Unit) {
    Text(
        text = "Hoàn thành ${state.total} thẻ",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )
    Spacer(modifier = Modifier.height(16.dp))
    RecallAssessment.entries.forEach { level ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(level.label(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text("${state.counts[level] ?: 0}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
    Spacer(modifier = Modifier.height(24.dp))
    if (state.isMission) {
        // The next mission is chosen from the updated results back on the Cockpit — never auto-started.
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Về Buồng lái") }
    } else {
        Button(onClick = onRestart, modifier = Modifier.fillMaxWidth()) { Text("Ôn lại") }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Về danh sách thẻ") }
    }
}
