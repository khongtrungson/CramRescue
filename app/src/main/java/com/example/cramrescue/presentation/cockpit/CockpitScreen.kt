package com.example.cramrescue.presentation.cockpit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cramrescue.domain.model.CampaignCountdown
import com.example.cramrescue.domain.speedrun.SpeedRunPolicy
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CockpitScreen(
    viewModelFactory: CockpitViewModelFactory,
    onBackToHome: () -> Unit,
    onAddMaterials: (Long) -> Unit,
    onOpenAnalysis: (Long) -> Unit,
    onOpenGeminiSettings: () -> Unit,
    onOpenMicroCards: (Long) -> Unit,
    onStartMission: (Long) -> Unit,
    onStartSpeedRun: (Long) -> Unit
) {
    val viewModel: CockpitViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()

    // Study state is re-read on every (re)entry, e.g. returning from a mission — so Pass
    // Probability updates without a restart, and never once per countdown tick.
    LaunchedEffect(Unit) { viewModel.refreshStudyPlan() }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            uiState.campaignNotFound -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Không tìm thấy chiến dịch.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onBackToHome) {
                        Text("Về màn hình chính")
                    }
                }
            }

            else -> {
                val campaign = uiState.campaign
                if (campaign != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Buồng lái sinh tồn",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = campaign.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = campaign.subjectArchetype.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        val countdown = uiState.countdown
                        val isEmergency = countdown?.isEmergency == true
                        val countdownColor = if (isEmergency) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onBackground
                        }

                        Text(
                            text = if (countdown?.isExamReached == true) "ĐÃ ĐẾN GIỜ THI" else "CÒN LẠI",
                            style = MaterialTheme.typography.labelLarge,
                            color = countdownColor
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = (countdown?.remaining ?: Duration.ZERO).toCountdownText(),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = countdownColor
                        )

                        if (isEmergency) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "KHẨN CẤP",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                            text = "Thi: " + DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                                .format(campaign.examDateTime.atZone(ZoneId.systemDefault())),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        uiState.studyPlan?.let { plan ->
                            Spacer(modifier = Modifier.height(24.dp))
                            StudyPlanSection(
                                plan = plan,
                                isExamReached = uiState.countdown?.isExamReached == true,
                                // Recomputed from the ticking countdown; no database work per tick.
                                speedRunWindow = uiState.countdown?.let { SpeedRunPolicy.window(it) },
                                onStartSpeedRun = { onStartSpeedRun(campaign.id) },
                                isStartingMission = uiState.isStartingMission,
                                missionMessage = uiState.missionMessage,
                                onStartMission = { viewModel.startMission { onStartMission(campaign.id) } }
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(onClick = { onAddMaterials(campaign.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Thêm tài liệu")
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(onClick = { onOpenAnalysis(campaign.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Phân tích AI (Pareto 80/20)")
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButton(onClick = { onOpenMicroCards(campaign.id) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Thẻ ghi nhớ (Micro-Card)")
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        TextButton(onClick = onOpenGeminiSettings, modifier = Modifier.fillMaxWidth()) {
                            Text("Cài đặt Gemini API key")
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        TextButton(onClick = onBackToHome, modifier = Modifier.fillMaxWidth()) {
                            Text("Về màn hình chính")
                        }
                    }
                }
            }
        }
    }
}

private fun Duration.toCountdownText(): String {
    val totalSeconds = seconds.coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return "%02d:%02d:%02d".format(hours, minutes, secs)
}
