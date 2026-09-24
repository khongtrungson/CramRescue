package com.example.cramrescue.presentation.microcard.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cramrescue.presentation.analysis.TriageBadge

@Composable
fun MicroCardsScreen(
    viewModelFactory: MicroCardsViewModelFactory,
    onBack: () -> Unit,
    onOpenAnalysis: () -> Unit,
    onOpenCard: (Long) -> Unit
) {
    val viewModel: MicroCardsViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()

    // Local reads only, on every (re)entry — never a Gemini request.
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Quay lại") }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.isLoading) {
            CircularProgressIndicator()
            return@Column
        }

        Text(
            text = "Thẻ ghi nhớ (Micro-Card)",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = uiState.campaignName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        when (uiState.blocker) {
            MicroCardsBlocker.NO_MATERIAL -> Notice("Chưa có tài liệu. Hãy thêm tài liệu từ Buồng lái sinh tồn.")
            MicroCardsBlocker.NO_OCR ->
                Notice("Tài liệu chưa có văn bản OCR. Mở từng tài liệu và bấm \"Trích xuất văn bản\".")
            MicroCardsBlocker.NO_ANALYSIS -> {
                Notice("Chưa có phân tích AI. Thẻ ghi nhớ được tạo từ các mục Code Red / Code Yellow của phân tích.")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenAnalysis, modifier = Modifier.fillMaxWidth()) {
                    Text("Mở Phân tích AI")
                }
            }
            null -> Unit
        }

        if (uiState.blocker == null) {
            if (uiState.analysisStale) {
                Notice(
                    "Tài liệu của chiến dịch đã thay đổi sau lần phân tích gần nhất. " +
                        "Hãy phân tích lại, sau đó tạo lại thẻ."
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenAnalysis, modifier = Modifier.fillMaxWidth()) {
                    Text("Mở Phân tích AI")
                }
                Spacer(modifier = Modifier.height(8.dp))
            } else if (uiState.cardsStale) {
                Notice("Các thẻ dưới đây được tạo từ một bản phân tích cũ hơn.")
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (uiState.canCreateCards && (!uiState.hasCards || uiState.cardsStale)) {
                Button(
                    onClick = viewModel::createCards,
                    enabled = !uiState.isCreating,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (uiState.hasCards) "Tạo lại thẻ từ phân tích hiện tại" else "Tạo thẻ ghi nhớ từ phân tích")
                }
            }
        }

        val message = uiState.message
        if (message != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = if (uiState.messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground
            )
        }

        if (uiState.hasCards) {
            CardSection("CODE RED — buộc phải thuộc", uiState.redCards, onOpenCard)
            CardSection("CODE YELLOW — kéo điểm an toàn", uiState.yellowCards, onOpenCard)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Code Green: đã lược bỏ ${uiState.discardedGreenCount} khái niệm râu ria/nâng cao (không tạo thẻ).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Notice(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun CardSection(title: String, cards: List<MicroCardItemUi>, onOpenCard: (Long) -> Unit) {
    if (cards.isEmpty()) return
    Spacer(modifier = Modifier.height(24.dp))
    Text(
        text = "$title (${cards.size})",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )
    cards.forEach { card ->
        Spacer(modifier = Modifier.height(12.dp))
        MicroCardItem(card, onClick = { onOpenCard(card.id) })
    }
}

@Composable
private fun MicroCardItem(card: MicroCardItemUi, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            TriageBadge(card.triage)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            card.bullets.forEach { bullet ->
                Text(
                    text = "• $bullet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Nguồn: ${card.sourceLabel}" + if (card.sourcesAvailable) "" else " (nguồn đã thay đổi)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
