package com.example.cramrescue.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cramrescue.R
import com.example.cramrescue.ui.theme.CodeRed
import com.example.cramrescue.ui.theme.CodeRedContainer
import com.example.cramrescue.ui.theme.OnCodeRed

@Composable
fun HomeScreen(
    viewModelFactory: HomeViewModelFactory,
    onCreateCampaignClick: () -> Unit,
    onOpenCockpit: (Long) -> Unit
) {
    val viewModel: HomeViewModel = viewModel(factory = viewModelFactory)
    val uiState by viewModel.uiState.collectAsState()

    // Re-read on every entry: a Campaign may have been created, edited or deleted meanwhile.
    LaunchedEffect(Unit) { viewModel.refresh() }

    HomeContent(uiState, onCreateCampaignClick, onOpenCockpit)
}

@Composable
internal fun HomeContent(
    uiState: HomeUiState,
    onCreateCampaignClick: () -> Unit,
    onOpenCockpit: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .paint(
                painter = painterResource(id = R.drawable.bg), // Ảnh nền của bạn
                contentScale = ContentScale.Crop // Phủ kín toàn màn hình bao gồm cả thanh trạng thái
            )
            .statusBarsPadding()
            .padding(horizontal = 5.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Hero Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Định nghĩa dải màu gradient từ đỏ cam sang vàng cam
                    val orangeGradient = Brush.linearGradient(
                        colors = listOf(Color(0xFFFF3D00), Color(0xFFFFAB00))
                    )

                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp) // Tăng kích thước icon cho nổi bật
                            .graphicsLayer(alpha = 0.99f) // Bắt buộc để áp dụng blend mode cho gradient
                            .drawWithCache {
                                onDrawWithContent {
                                    drawContent()
                                    drawRect(orangeGradient, blendMode = BlendMode.SrcAtop)
                                }
                            }
                    )

                    Text(
                        text = "CramRescue 48h",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Trợ Lý Sinh Tồn Phòng Thi",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(),
                    //.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.isEmpty -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 16.dp,
                                shape = RoundedCornerShape(28.dp),
                                //spotColor = Color.Black.copy(alpha = 25f) // (Tùy chọn) chỉnh màu bóng
                            ),
                        shape = RoundedCornerShape(28.dp), // Bo góc tròn mềm mại giống thiết kế mẫu
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f), // Màu nền thẻ
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.logo), // Thay bằng tên file ảnh của bạn trong res/drawable
                                contentDescription = "Logo CramRescue",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(150.dp) //
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // 2. Tiêu đề chính
                            Text(
                                text = "CHƯA CÓ CHIẾN DỊCH NÀO",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // 3. Nội dung mô tả
                            Text(
                                text = "Tạo chiến dịch cho kỳ thi sắp tới để bắt đầu",
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // 5. Mục Mẹo Sinh Tồn 48h (Căn trái)
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = "Mẹo Sinh Tồn 48h",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "• Tải lên đề cương ngay",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "• Phân tích AI nhanh chóng",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "• Cố vấn AI sẵn sàng",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // 6. Nút bấm Tạo chiến dịch sinh tồn
                            Button(
                                onClick = onCreateCampaignClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    // Hiệu ứng bóng phát sáng viền ngoài cho nút
                                    .shadow(
                                        elevation = 12.dp,
                                        shape = RoundedCornerShape(16.dp),
                                        ambientColor = Color(0xFF81C784),
                                        spotColor = Color(0xFF81C784)
                                    ),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF212121)
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = Color(0xFFA5D6A7), // Icon + màu xanh nhạt phát sáng
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "+ TẠO CHIẾN DỊCH SINH TỒN",
                                        color = Color(0xFFA5D6A7), // Chữ màu xanh sáng glowing
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            else -> {
                Button(
                    onClick = onCreateCampaignClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tạo chiến dịch mới", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier
                        //.fillMaxSize()
                        //.weight(0.2f)
                        .padding(horizontal = 16.dp)
                        .shadow(
                            elevation = 120.dp,
                            shape = RoundedCornerShape(16.dp),
                        )
                        .testTag("campaign-list"),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    if (uiState.upcoming.isNotEmpty()) {
                        item { SectionTitle("Sắp thi") }
                        items(uiState.upcoming, key = { it.id }) { row ->
                            CampaignRow(row, onOpenCockpit)
                        }
                    }
                    if (uiState.past.isNotEmpty()) {
                        item { SectionTitle("Đã qua giờ thi") }
                        items(uiState.past, key = { it.id }) { row ->
                            CampaignRow(row, onOpenCockpit)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CampaignRow(row: CampaignRowUi, onOpenCockpit: (Long) -> Unit) {
    val reached = row.status == CampaignRowStatus.EXAM_REACHED
    val isEmergency = row.status == CampaignRowStatus.EMERGENCY

    val archetypeIcon: ImageVector = when {
        row.archetype.contains("Tự nhiên", ignoreCase = true) -> Icons.Default.Functions
        row.archetype.contains("Xã hội", ignoreCase = true) -> Icons.Default.MenuBook
        row.archetype.contains("Lập trình", ignoreCase = true) -> Icons.Default.Code
        else -> Icons.Default.Translate
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (isEmergency) CodeRed.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("campaign-row-${row.id}")
            .clickable { onOpenCockpit(row.id) }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Archetype Icon Box
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = archetypeIcon,
                    contentDescription = null,
                    tint = if (isEmergency) CodeRed else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = row.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (reached) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (isEmergency) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(CodeRed, RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = OnCodeRed,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Khẩn cấp",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = OnCodeRed
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = row.archetype,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${row.examLabel} \n${row.remainingLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isEmergency) CodeRed else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isEmergency) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

