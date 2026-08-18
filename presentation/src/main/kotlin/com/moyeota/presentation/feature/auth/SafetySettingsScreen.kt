package com.moyeota.presentation.feature.auth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.MoyeotaTopBar
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBarMock
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaTheme
import com.moyeota.core.designsystem.theme.MoyeotaType

// 11 · 안심 설정 [S06]
// 진입: 10 프로필 완료 / 뒤로 → 10
// 「설정 저장하고 계속」 → 12 매너 서약, 「나중에 설정할게요」 → 12 (설정 모두 off로 저장)
// 토글은 즉시 저장 (별도 저장 버튼 없음) — CTA는 다음 단계 이동용
@Composable
fun SafetySettingsScreen(
    onBack: () -> Unit,
    onContinue: (nightLocationShare: Boolean, arrivalAlert: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 심야 구간 23:00~04:00 고정값, 보호자 1명 기등록 상태 (최대 2명)
    var nightLocationShare by remember { mutableStateOf(true) }
    var arrivalAlert by remember { mutableStateOf(false) }
    val guardians = remember { mutableStateOf(listOf("어머니 · 010-••••-1234")) }

    Column(modifier = modifier.fillMaxSize().background(MoyeotaColor.SurfaceSoft)) {
        StatusBarMock()
        MoyeotaTopBar(
            title = "",
            onBack = onBack,
            actions = {
                Text(
                    text = "4 / 5",
                    style = MoyeotaType.BodySm,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF8A93A0),
                )
            },
        )
        SafetyStepProgressBar(progress = 4f / 5f)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = "안심 설정을 켜둘까요?",
                style = MoyeotaType.DisplayMd,
                color = MoyeotaColor.InkPrimary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "마이페이지에서 언제든 바꿀 수 있어요",
                style = MoyeotaType.BodySm.copy(fontSize = 14.sp),
                color = MoyeotaColor.TextMute,
            )

            Spacer(Modifier.height(24.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(18.dp), spotColor = Color(0x0F1B2A4A))
                    .background(MoyeotaColor.SurfaceCanvas, RoundedCornerShape(18.dp)),
            ) {
                // 와이어프레임 카드 상단 여백 영역
                Spacer(Modifier.height(70.dp))
                HorizontalDivider(color = MoyeotaColor.Hairline, modifier = Modifier.padding(horizontal = 20.dp))
                SafetyToggleRow(
                    title = "심야 자동 위치 공유",
                    description = "밤 11시~새벽 4시 탑승은 보호자에게 자동 공유해요",
                    checked = nightLocationShare,
                    onCheckedChange = { on ->
                        // 심야 자동 공유 ON → 보호자 연락처 최소 1명 필수
                        // (없으면 등록 시트 강제 노출 — 시트 미연결이라 켜지지 않음)
                        if (!on || guardians.value.isNotEmpty()) {
                            nightLocationShare = on
                        }
                    },
                )
                HorizontalDivider(color = MoyeotaColor.Hairline, modifier = Modifier.padding(horizontal = 20.dp))
                SafetyToggleRow(
                    title = "도착 알림 보내기",
                    description = "내리면 지정한 사람에게 도착 알림이 가요",
                    checked = arrivalAlert,
                    onCheckedChange = { arrivalAlert = it },
                )
            }

            Spacer(Modifier.height(20.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF1F5FD), RoundedCornerShape(18.dp))
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                Text(
                    text = "보호자 연락처",
                    style = MoyeotaType.CaptionMd,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF4B5563),
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = guardians.value.first(),
                        style = MoyeotaType.HeadingMd,
                        fontWeight = FontWeight.Bold,
                        color = MoyeotaColor.InkPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    // 미연결 — 보호자 변경 시트 필요 (클릭해도 동작 없음)
                    Text(
                        text = "변경",
                        style = MoyeotaType.BodySm,
                        fontWeight = FontWeight.Bold,
                        color = MoyeotaColor.Primary500,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { /* 미연결 */ },
                    )
                }
                Spacer(Modifier.height(10.dp))
                // 미연결 — 보호자 등록 시트 필요 (클릭해도 동작 없음)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { /* 미연결 */ },
                ) {
                    SafetyPlusIcon()
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "한 명 더 등록하기",
                        style = MoyeotaType.BodySm,
                        fontWeight = FontWeight.Bold,
                        color = MoyeotaColor.Primary500,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = "조건에 맞는 탑승이 없으면 매칭이 늦어질 수 있어요",
                style = MoyeotaType.CaptionMd,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF8A93A0),
            )
            Spacer(Modifier.height(24.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            PrimaryCtaButton(
                text = "설정 저장하고 계속",
                onClick = { onContinue(nightLocationShare, arrivalAlert) },
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "나중에 설정할게요",
                style = MoyeotaType.BodySm,
                fontWeight = FontWeight.Medium,
                color = MoyeotaColor.TextMute,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        // 설정 모두 off로 저장 후 12 매너 서약으로 이동
                        nightLocationShare = false
                        arrivalAlert = false
                        onContinue(false, false)
                    },
            )
        }
        SafetyHomeIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun SafetyToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MoyeotaType.BodyMd,
                fontWeight = FontWeight.Bold,
                color = MoyeotaColor.InkPrimary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = MoyeotaType.CaptionMd,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF8A93A0),
            )
        }
        Spacer(Modifier.width(12.dp))
        // 토글은 즉시 저장 — 별도 저장 버튼 없음
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MoyeotaColor.Primary500,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFDFE4EA),
                uncheckedBorderColor = Color(0xFFDFE4EA),
            ),
        )
    }
}

@Composable
private fun SafetyPlusIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(14.dp)) {
        val stroke = 2.dp.toPx()
        drawLine(
            color = MoyeotaColor.Primary500,
            start = Offset(size.width / 2f, 0f),
            end = Offset(size.width / 2f, size.height),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = MoyeotaColor.Primary500,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun SafetyStepProgressBar(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(4.dp)
            .background(Color(0xFFE6EAF0), RoundedCornerShape(2.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress)
                .height(4.dp)
                .background(MoyeotaColor.Primary500, RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun SafetyHomeIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(top = 12.dp, bottom = 9.dp)
            .size(width = 135.dp, height = 5.dp)
            .background(MoyeotaColor.InkPrimary, RoundedCornerShape(2.5.dp)),
    )
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SafetySettingsScreenPreview() {
    MoyeotaTheme {
        SafetySettingsScreen(onBack = {}, onContinue = { _, _ -> })
    }
}
