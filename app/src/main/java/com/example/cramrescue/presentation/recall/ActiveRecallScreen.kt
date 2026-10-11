package com.example.cramrescue.presentation.recall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import com.example.cramrescue.domain.model.TriageLevel
import com.example.cramrescue.domain.recall.ClozeSegment
import com.example.cramrescue.presentation.analysis.TriageBadge
import com.example.cramrescue.ui.theme.CodeGreen
import com.example.cramrescue.ui.theme.CodeRed
import com.example.cramrescue.ui.theme.CodeYellow
import com.example.cramrescue.ui.theme.CodeYellowContainer
import com.example.cramrescue.ui.theme.OnCodeGreen
import com.example.cramrescue.ui.theme.OnCodeYellowContainer

// Fixed-width placeholder, so the box doesn't hint at the hidden keyword's length.
private const val HIDDEN_PLACEHOLDER = "  ? ? ?  "

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveRecallScreen(
    viewModelFactory: ActiveRecallViewModelFactory,
    onBack: () -> Unit,
    onOpenSource: (cardId: Long, documentId: Long, pageIndex: Int) -> Unit
) {
    val viewModel: ActiveRecallViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            val title = when (val s = uiState) {
                is ActiveRecallUiState.Studying -> when (s.mode) {
                    StudyMode.ALL -> "Ôn tập thẻ"
                    StudyMode.MISSION -> "Nhiệm vụ sinh tồn"
                    StudyMode.SPEED_RUN -> "Speed-run khẩn cấp"
                }
                is ActiveRecallUiState.Completed -> "Kết quả phiên học"
                else -> "Active Recall"
            }

            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Quay lại"
                            )
                        }
                    },
                    actions = {
                        (uiState as? ActiveRecallUiState.Studying)?.let { s ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(end = 12.dp)
                            ) {
                                Text(
                                    text = "${s.position} / ${s.total}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )

                (uiState as? ActiveRecallUiState.Studying)?.let { s ->
                    val progress = s.position.toFloat() / s.total.toFloat()
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val s = uiState) {
                ActiveRecallUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                is ActiveRecallUiState.NoCards -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = s.message,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onBack) {
                            Text("Quay lại")
                        }
                    }
                }

                is ActiveRecallUiState.Studying -> Studying(
                    state = s,
                    onReveal = viewModel::reveal,
                    onAssess = viewModel::assess,
                    onOpenSource = { docId, pageIndex ->
                        onOpenSource(s.cardId, docId, pageIndex)
                    }
                )

                is ActiveRecallUiState.Completed -> Completed(
                    state = s,
                    onRestart = viewModel::restart,
                    onBack = onBack
                )
            }
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
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Scrollable content area
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            if (state.cardsStale) {
                Surface(
                    color = CodeYellowContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = OnCodeYellowContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Các thẻ này thuộc một bản phân tích cũ — tài liệu đã thay đổi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnCodeYellowContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Flashcard Hero Canvas
            val borderColor = if (state.triage == TriageLevel.CODE_RED) {
                CodeRed.copy(alpha = 0.5f)
            } else {
                CodeYellow.copy(alpha = 0.5f)
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, borderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    TriageBadge(state.triage)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Question(state.question, revealed = state.revealed != null, onReveal = onReveal)

                    val revealed = state.revealed
                    if (revealed != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Answer(revealed, onOpenSource)
                    }
                }
            }

            state.errorMessage?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Sticky Bottom Thumb-Zone Action Area
        Surface(
            color = MaterialTheme.colorScheme.background,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                val revealed = state.revealed
                if (revealed == null) {
                    Button(
                        onClick = onReveal,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text(
                            text = "Hiện đáp án",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = "Bạn nhớ thẻ này đến đâu?",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = { onAssess(RecallAssessment.NOT_REMEMBERED) },
                            enabled = !state.isSaving,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, CodeRed.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text(
                                text = RecallAssessment.NOT_REMEMBERED.label(),
                                color = CodeRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        OutlinedButton(
                            onClick = { onAssess(RecallAssessment.PARTIAL) },
                            enabled = !state.isSaving,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, CodeYellow.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text(
                                text = RecallAssessment.PARTIAL.label(),
                                color = CodeYellow,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        // FR-9: marking a card mastered raises Pass Probability — light haptic feedback.
                        val haptics = LocalHapticFeedback.current
                        Button(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onAssess(RecallAssessment.MASTERED)
                            },
                            enabled = !state.isSaving,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CodeGreen,
                                contentColor = OnCodeGreen
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text(
                                text = RecallAssessment.MASTERED.label(),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Question(question: QuestionUi, revealed: Boolean, onReveal: () -> Unit) {
    when (question) {
        is QuestionUi.General -> {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Hãy nhớ lại ${question.pointCount} ý chính của khái niệm này, rồi bấm \"Hiện đáp án\".",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
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
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Đáp án: " + revealed.answers.joinToString(", "),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(12.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
    }
    revealed.bullets.forEach { bullet ->
        Text(
            text = "• $bullet",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 2.dp)
        )
    }
    revealed.notes?.let {
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Ghi chú mở rộng: $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
    if (revealed.sourceGroups.isNotEmpty()) {
        Spacer(modifier = Modifier.height(10.dp))
        // Reachable only after Reveal: the source text/highlight would give the answer away.
        revealed.sourceGroups.forEach { group ->
            OutlinedButton(
                onClick = { onOpenSource(group.documentId, group.pageIndex) },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FindInPage,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = CodeGreen,
                    modifier = Modifier.size(56.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Hoàn thành ${state.total} thẻ",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(20.dp))

                RecallAssessment.entries.forEach { level ->
                    val (color, count) = when (level) {
                        RecallAssessment.MASTERED -> Pair(CodeGreen, state.counts[level] ?: 0)
                        RecallAssessment.PARTIAL -> Pair(CodeYellow, state.counts[level] ?: 0)
                        RecallAssessment.NOT_REMEMBERED -> Pair(CodeRed, state.counts[level] ?: 0)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(level.label(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (state.mode != StudyMode.ALL) {
            Button(
                onClick = onBack,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Về Buồng lái", fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onRestart,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Ôn lại", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("Về danh sách thẻ", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
