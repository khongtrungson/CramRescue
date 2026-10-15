package com.example.cramrescue.presentation.quiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cramrescue.ui.theme.CodeGreen
import com.example.cramrescue.ui.theme.CodeRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizResultScreen(
    campaignId: Long,
    quizId: Long,
    attemptId: Long,
    viewModelFactory: QuizResultViewModelFactory,
    onBackToList: () -> Unit,
    onRetakeQuiz: (Long) -> Unit
) {
    val viewModel: QuizResultViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kết quả bài thi", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackToList) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Về danh sách")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                uiState.attempt == null -> {
                    Text(
                        text = uiState.errorMessage ?: "Không tìm thấy kết quả bài thi.",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    val stats = uiState.stats
                    val isPass = stats.score >= 5.0f

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header Score Card
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(
                                    2.dp,
                                    if (isPass) CodeGreen.copy(alpha = 0.5f) else CodeRed.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (isPass) "ĐẠT NGƯỠNG AN TOÀN" else "CẦN ÔN TẬP THÊM",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.sp,
                                        color = if (isPass) CodeGreen else CodeRed
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "${stats.score}",
                                        style = MaterialTheme.typography.displayLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isPass) CodeGreen else CodeRed
                                    )
                                    Text(
                                        text = "Thang điểm 10.0",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Quick Stats Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        StatBox(
                                            label = "Đúng",
                                            value = "${stats.correctCount}/${stats.totalQuestions}",
                                            color = CodeGreen,
                                            modifier = Modifier.weight(1f).fillMaxHeight()
                                        )
                                        StatBox(
                                            label = "Sai/Bỏ qua",
                                            value = "${stats.incorrectCount}/${stats.totalQuestions}",
                                            color = CodeRed,
                                            modifier = Modifier.weight(1f).fillMaxHeight()
                                        )
                                        StatBox(
                                            label = "Thời gian",
                                            value = formatSecondsToMMSS(stats.durationSeconds),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f).fillMaxHeight()
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = { onRetakeQuiz(quizId) },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Làm lại đề này")
                                        }
                                        OutlinedButton(
                                            onClick = onBackToList,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Danh sách đề")
                                        }
                                    }
                                }
                            }
                        }

                        // Detailed Question Review
                        item {
                            Text(
                                text = "CHI TIẾT BÀI LÀM",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                            )
                        }

                        itemsIndexed(uiState.reviewItems) { index, item ->
                            QuestionReviewCard(
                                index = index,
                                item = item,
                                onShowGrounding = { viewModel.showGrounding(item) }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(40.dp))
                        }
                    }
                }
            }
        }
    }

    uiState.activeGrounding?.let { grounding ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissGrounding() },
            icon = { Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Đối chiếu nguồn tài liệu gốc") },
            text = {
                Column {
                    Text(
                        text = "Câu hỏi: \"${grounding.questionStem}\"",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Mã khối văn bản được trích dẫn:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = grounding.sourceBlockIds.joinToString(", "),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Giải thích dẫn chứng:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = grounding.explanation,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.dismissGrounding() }) {
                    Text("Đã hiểu")
                }
            }
        )
    }
}

@Composable
private fun StatBox(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuestionReviewCard(
    index: Int,
    item: QuestionReviewItem,
    onShowGrounding: () -> Unit
) {
    val q = item.question
    val optionLabels = listOf("A", "B", "C", "D")

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (item.isCorrect) CodeGreen.copy(alpha = 0.4f) else CodeRed.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (item.isCorrect) CodeGreen else CodeRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (item.isCorrect) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Câu ${index + 1}: ${if (item.isCorrect) "Đúng" else "Sai"}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isCorrect) CodeGreen else CodeRed
                    )
                }

                TextButton(onClick = onShowGrounding) {
                    Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Đối chiếu nguồn", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = q.stem,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4 Options
            q.options.forEachIndexed { optIdx, optText ->
                val isUserSelection = item.selectedOptionIndex == optIdx
                val isCorrectAnswer = q.correctOptionIndex == optIdx

                val (optBg, optBorder, optTextColor) = when {
                    isUserSelection && isCorrectAnswer -> Triple(CodeGreen.copy(alpha = 0.15f), CodeGreen, CodeGreen)
                    isUserSelection && !isCorrectAnswer -> Triple(CodeRed.copy(alpha = 0.15f), CodeRed, CodeRed)
                    isCorrectAnswer -> Triple(CodeGreen.copy(alpha = 0.08f), CodeGreen.copy(alpha = 0.6f), CodeGreen)
                    else -> Triple(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f), MaterialTheme.colorScheme.onSurface)
                }

                Surface(
                    color = optBg,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, optBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${optionLabels.getOrElse(optIdx) { "" }}.",
                            fontWeight = FontWeight.Bold,
                            color = optTextColor,
                            modifier = Modifier.width(24.dp)
                        )
                        Text(
                            text = optText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (isUserSelection && isCorrectAnswer) {
                            Text("✓ Đã chọn (Đúng)", color = CodeGreen, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        } else if (isUserSelection && !isCorrectAnswer) {
                            Text("✗ Đã chọn (Sai)", color = CodeRed, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        } else if (isCorrectAnswer) {
                            Text("✓ Đáp án đúng", color = CodeGreen, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Explanation box
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Giải thích chi tiết:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = q.explanation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatSecondsToMMSS(seconds: Long): String {
    val m = (seconds.coerceAtLeast(0)) / 60
    val s = (seconds.coerceAtLeast(0)) % 60
    return "%02d:%02d".format(m, s)
}
