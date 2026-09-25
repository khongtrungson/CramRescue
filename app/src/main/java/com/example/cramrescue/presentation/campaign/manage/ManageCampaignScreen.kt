package com.example.cramrescue.presentation.campaign.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cramrescue.presentation.campaign.toMessage
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun ManageCampaignScreen(
    viewModelFactory: ManageCampaignViewModelFactory,
    onBack: () -> Unit,
    onCampaignDeleted: () -> Unit
) {
    val viewModel: ManageCampaignViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()

    // Leave immediately after a clean delete; if files were left behind, say so first.
    LaunchedEffect(uiState.deleted, uiState.undeletedFileCount) {
        if (uiState.deleted && uiState.undeletedFileCount == 0) onCampaignDeleted()
    }

    ManageCampaignContent(
        uiState = uiState,
        onNameChange = viewModel::onNameChange,
        onDateSelected = viewModel::onDateSelected,
        onTimeSelected = viewModel::onTimeSelected,
        onSave = viewModel::save,
        onRequestDelete = viewModel::requestDelete,
        onDismissDelete = viewModel::dismissDelete,
        onConfirmDelete = viewModel::confirmDelete,
        onBack = onBack,
        onDeletedAcknowledged = onCampaignDeleted
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManageCampaignContent(
    uiState: ManageCampaignUiState,
    onNameChange: (String) -> Unit,
    onDateSelected: (java.time.LocalDate) -> Unit,
    onTimeSelected: (LocalTime) -> Unit,
    onSave: () -> Unit,
    onRequestDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onBack: () -> Unit,
    onDeletedAcknowledged: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Buồng lái sinh tồn") }
        Text("Quản lý chiến dịch", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))

        when {
            uiState.isLoading -> Text("Đang tải...", style = MaterialTheme.typography.bodyMedium)
            uiState.notFound && !uiState.deleted -> {
                Text("Chiến dịch không còn tồn tại.", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onDeletedAcknowledged) { Text("Về danh sách chiến dịch") }
            }
            else -> {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = onNameChange,
                    label = { Text("Tên môn học") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text("Nhóm môn học", style = MaterialTheme.typography.titleMedium)
                Text(uiState.archetype, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Không thể đổi sau khi tạo: phân tích AI được tạo theo nhóm môn học này.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text("Thời điểm thi", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDatePicker = true }) {
                        Text(uiState.examDate?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) ?: "Chọn ngày thi")
                    }
                    OutlinedButton(onClick = { showTimePicker = true }) {
                        Text(uiState.examTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "Chọn giờ thi")
                    }
                }
                Text(
                    "Đổi giờ thi chỉ cập nhật đồng hồ đếm ngược; tài liệu, phân tích, thẻ và lịch sử ôn tập được giữ nguyên.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                uiState.errors.forEach { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(error.toMessage(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                uiState.message?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onSave, enabled = !uiState.isSaving && !uiState.isDeleting, modifier = Modifier.fillMaxWidth()) {
                    Text(if (uiState.isSaving) "Đang lưu..." else "Lưu thay đổi")
                }

                Spacer(modifier = Modifier.height(32.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onRequestDelete, enabled = !uiState.isDeleting && !uiState.isSaving, modifier = Modifier.fillMaxWidth()) {
                    Text(if (uiState.isDeleting) "Đang xóa..." else "Xóa chiến dịch", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (uiState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text("Xóa chiến dịch \"${uiState.savedName}\"?") },
            text = {
                Text(
                    "Toàn bộ tài liệu đã nhập, văn bản trích xuất, phân tích, thẻ ghi nhớ, lịch sử ôn tập và nhiệm vụ " +
                        "của chiến dịch này sẽ bị xóa vĩnh viễn khỏi máy. Không thể hoàn tác. Tệp gốc của bạn không bị ảnh hưởng."
                )
            },
            confirmButton = {
                TextButton(onClick = onConfirmDelete) {
                    Text("Xóa vĩnh viễn", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = onDismissDelete) { Text("Hủy") } }
        )
    }

    if (uiState.deleted && uiState.undeletedFileCount > 0) {
        AlertDialog(
            onDismissRequest = onDeletedAcknowledged,
            title = { Text("Đã xóa chiến dịch") },
            text = {
                Text("${uiState.undeletedFileCount} tệp tài liệu trong bộ nhớ ứng dụng không xóa được. Dữ liệu chiến dịch đã được xóa hết.")
            },
            confirmButton = { TextButton(onClick = onDeletedAcknowledged) { Text("OK") } }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.examDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onDateSelected(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text("Chọn") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Hủy") } }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = uiState.examTime?.hour ?: 0,
            initialMinute = uiState.examTime?.minute ?: 0
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    onTimeSelected(LocalTime.of(timePickerState.hour, timePickerState.minute))
                    showTimePicker = false
                }) { Text("Chọn") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Hủy") } },
            text = { TimePicker(state = timePickerState) }
        )
    }
}
