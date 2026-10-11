package com.example.cramrescue.presentation.cockpit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cramrescue.domain.mission.MissionFocus
import com.example.cramrescue.domain.speedrun.SpeedRunPolicy
import com.example.cramrescue.domain.speedrun.SpeedRunWindow
import com.example.cramrescue.domain.usecase.mission.MissionPlan
import com.example.cramrescue.domain.usecase.mission.ReadinessState
import com.example.cramrescue.domain.usecase.mission.ReadinessUnavailableReason
import com.example.cramrescue.domain.usecase.mission.StudyPlan
import com.example.cramrescue.ui.theme.CodeGreen
import com.example.cramrescue.ui.theme.CodeRed
import com.example.cramrescue.ui.theme.CodeRedContainer
import com.example.cramrescue.ui.theme.CodeYellow
import com.example.cramrescue.ui.theme.CodeYellowContainer
import com.example.cramrescue.ui.theme.OnCodeRed
import com.example.cramrescue.ui.theme.OnCodeRedContainer
import com.example.cramrescue.ui.theme.OnCodeYellow
import com.example.cramrescue.ui.theme.OnCodeYellowContainer

/** Pass Probability (FR-9) + next Micro-mission (FR-10). Renders state only; no calculation here. */
@Composable
fun StudyPlanSection(
    plan: StudyPlan,
    isExamReached: Boolean,
    speedRunWindow: SpeedRunWindow?,
    onStartSpeedRun: () -> Unit,
    isStartingMission: Boolean,
    missionMessage: String?,
    onStartMission: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Readiness(plan.readiness)

            Spacer(modifier = Modifier.height(18.dp))

            if (speedRunWindow == SpeedRunWindow.OPEN && plan.mission != MissionPlan.NoCards) {
                SpeedRun(plan, onStartSpeedRun)
                Spacer(modifier = Modifier.height(16.dp))
            }

            Mission(plan, isExamReached, isStartingMission, onStartMission)

            missionMessage?.let {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CodeRedContainer, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = OnCodeRedContainer,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = OnCodeRedContainer)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Readiness(readiness: ReadinessState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "XÁC SUẤT AN TOÀN",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    when (readiness) {
        is ReadinessState.Unavailable -> {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = when (readiness.reason) {
                            ReadinessUnavailableReason.NO_MATERIAL -> "Chưa có dữ liệu — hãy thêm tài liệu."
                            ReadinessUnavailableReason.NO_OCR -> "Chưa có dữ liệu — hãy trích xuất văn bản tài liệu."
                            ReadinessUnavailableReason.NO_ANALYSIS -> "Chưa có dữ liệu — hãy chạy Phân tích AI."
                            ReadinessUnavailableReason.NO_CARDS -> "Chưa có dữ liệu — hãy tạo thẻ ghi nhớ."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        is ReadinessState.Available -> {
            val snapshot = readiness.snapshot
            val progress by animateFloatAsState(targetValue = snapshot.percent / 100f, label = "passProbability")

            Row(
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(
                    text = "${snapshot.percent}%",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = when {
                        snapshot.percent >= 80 -> CodeGreen
                        snapshot.percent >= 50 -> CodeYellow
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (snapshot.percent >= 70) "Ngưỡng an toàn qua môn" else "Cần tiếp tục bám sát nhiệm vụ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
            )

            Spacer(modifier = Modifier.height(14.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (snapshot.redTotal > 0) {
                    Surface(
                        color = CodeRedContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(CodeRed)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Code Red đã thuộc: ${snapshot.redMastered} / ${snapshot.redTotal}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = OnCodeRedContainer
                            )
                        }
                    }
                }

                if (snapshot.yellowTotal > 0) {
                    Surface(
                        color = CodeYellowContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(CodeYellow)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Code Yellow đã thuộc: ${snapshot.yellowMastered} / ${snapshot.yellowTotal}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = OnCodeYellowContainer
                            )
                        }
                    }
                }
            }

            val rest = listOfNotNull(
                "Chưa nhớ: ${snapshot.notRememberedCount}".takeIf { snapshot.notRememberedCount > 0 },
                "Tạm ổn: ${snapshot.partialCount}".takeIf { snapshot.partialCount > 0 },
                "Chưa ôn: ${snapshot.unstudiedCount}".takeIf { snapshot.unstudiedCount > 0 }
            )
            if (rest.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Fact(rest.joinToString(" · "))
            }

            if (readiness.isStale) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = CodeYellowContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = OnCodeYellowContainer, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tính trên bộ thẻ từ phân tích cũ — tài liệu đã thay đổi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnCodeYellowContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Mission(plan: StudyPlan, isExamReached: Boolean, isStarting: Boolean, onStart: () -> Unit) {
    if (isExamReached) {
        Fact("Đã đến giờ thi — không còn nhiệm vụ ôn tập mới.")
        return
    }

    when (val mission = plan.mission) {
        MissionPlan.NoCards -> Unit
        MissionPlan.AllMastered ->
            Fact("Đã thuộc toàn bộ thẻ Code Red/Yellow hiện tại. Lịch ôn lặp lại sẽ có ở bản sau.")
        is MissionPlan.InProgress -> {
            MissionHeader("NHIỆM VỤ ĐANG DỞ")
            Spacer(modifier = Modifier.height(4.dp))
            Fact("Còn ${mission.remaining.size} / ${mission.total} thẻ")
            MissionButton("Tiếp tục nhiệm vụ", isStarting, onStart)
        }
        is MissionPlan.Next -> {
            MissionHeader("NHIỆM VỤ TIẾP THEO")
            Spacer(modifier = Modifier.height(4.dp))
            val focus = when (mission.focus) {
                MissionFocus.CODE_RED -> "ưu tiên Code Red chưa thuộc"
                MissionFocus.CODE_YELLOW -> "Code Red đã thuộc hết, tiếp tục Code Yellow"
            }
            Fact("${mission.cards.size} thẻ · $focus")
            if (plan.cardsStale) Fact("Thẻ thuộc bản phân tích cũ.")
            MissionButton("Bắt đầu nhiệm vụ", isStarting, onStart)
        }
    }
}

/** FR-13, shown only inside the FR-8 emergency window (never once the exam time is reached). */
@Composable
private fun SpeedRun(plan: StudyPlan, onStart: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "EMERGENCY SPEED-RUN",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            if (plan.speedRunCards.isEmpty()) {
                val redTotal = (plan.readiness as? ReadinessState.Available)?.snapshot?.redTotal ?: 0
                Fact(if (redTotal > 0) "Trạng thái sẵn sàng: Đã khóa kiến thức cốt lõi phòng thi." else "Không có thẻ Code Red nào để Speed-run.")
            } else {
                Fact("${plan.speedRunCards.size} thẻ Code Red chưa thuộc · mục tiêu ${SpeedRunPolicy.TARGET_MINUTES} phút")
                if (plan.cardsStale) Fact("Thẻ thuộc bản phân tích cũ.")
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onStart,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Bắt đầu Speed-run", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MissionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun MissionButton(text: String, isStarting: Boolean, onStart: () -> Unit) {
    Spacer(modifier = Modifier.height(10.dp))
    Button(
        onClick = onStart,
        enabled = !isStarting,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Fact(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
}

