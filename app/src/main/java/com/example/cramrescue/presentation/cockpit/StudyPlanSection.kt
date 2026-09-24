package com.example.cramrescue.presentation.cockpit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.cramrescue.domain.mission.MissionFocus
import com.example.cramrescue.domain.usecase.mission.MissionPlan
import com.example.cramrescue.domain.usecase.mission.ReadinessState
import com.example.cramrescue.domain.usecase.mission.ReadinessUnavailableReason
import com.example.cramrescue.domain.usecase.mission.StudyPlan

/** Pass Probability (FR-9) + next Micro-mission (FR-10). Renders state only; no calculation here. */
@Composable
fun StudyPlanSection(
    plan: StudyPlan,
    isExamReached: Boolean,
    isStartingMission: Boolean,
    missionMessage: String?,
    onStartMission: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Readiness(plan.readiness)
            Spacer(modifier = Modifier.height(16.dp))
            Mission(plan, isExamReached, isStartingMission, onStartMission)
            missionMessage?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun Readiness(readiness: ReadinessState) {
    Text(
        text = "XÁC SUẤT AN TOÀN",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    when (readiness) {
        is ReadinessState.Unavailable -> Text(
            text = when (readiness.reason) {
                ReadinessUnavailableReason.NO_MATERIAL -> "Chưa có dữ liệu — hãy thêm tài liệu."
                ReadinessUnavailableReason.NO_OCR -> "Chưa có dữ liệu — hãy trích xuất văn bản tài liệu."
                ReadinessUnavailableReason.NO_ANALYSIS -> "Chưa có dữ liệu — hãy chạy Phân tích AI."
                ReadinessUnavailableReason.NO_CARDS -> "Chưa có dữ liệu — hãy tạo thẻ ghi nhớ."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        is ReadinessState.Available -> {
            val snapshot = readiness.snapshot
            val progress by animateFloatAsState(targetValue = snapshot.percent / 100f, label = "passProbability")
            Text(
                text = "${snapshot.percent}%",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            if (snapshot.redTotal > 0) Fact("Code Red đã thuộc: ${snapshot.redMastered} / ${snapshot.redTotal}")
            if (snapshot.yellowTotal > 0) Fact("Code Yellow đã thuộc: ${snapshot.yellowMastered} / ${snapshot.yellowTotal}")
            val rest = listOfNotNull(
                "Chưa nhớ: ${snapshot.notRememberedCount}".takeIf { snapshot.notRememberedCount > 0 },
                "Tạm ổn: ${snapshot.partialCount}".takeIf { snapshot.partialCount > 0 },
                "Chưa ôn: ${snapshot.unstudiedCount}".takeIf { snapshot.unstudiedCount > 0 }
            )
            if (rest.isNotEmpty()) Fact(rest.joinToString(" · "))
            if (readiness.isStale) Fact("Tính trên bộ thẻ từ phân tích cũ — tài liệu đã thay đổi.")
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
            Fact("Còn ${mission.remaining.size} / ${mission.total} thẻ")
            MissionButton("Tiếp tục nhiệm vụ", isStarting, onStart)
        }
        is MissionPlan.Next -> {
            MissionHeader("NHIỆM VỤ TIẾP THEO")
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

@Composable
private fun MissionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MissionButton(text: String, isStarting: Boolean, onStart: () -> Unit) {
    Spacer(modifier = Modifier.height(8.dp))
    Button(onClick = onStart, enabled = !isStarting, modifier = Modifier.fillMaxWidth()) { Text(text) }
}

@Composable
private fun Fact(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
}
