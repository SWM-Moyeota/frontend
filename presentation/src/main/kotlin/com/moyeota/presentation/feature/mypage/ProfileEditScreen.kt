package com.moyeota.presentation.feature.mypage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moyeota.core.designsystem.component.MoyeotaTextField
import com.moyeota.core.designsystem.component.MoyeotaTopBar
import com.moyeota.core.designsystem.component.NavigationBarSpacer
import com.moyeota.core.designsystem.component.NoticeBanner
import com.moyeota.core.designsystem.component.NoticeKind
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaTheme
import com.moyeota.core.designsystem.theme.MoyeotaType
import com.moyeota.presentation.feature.auth.NicknameCheckState
import com.moyeota.presentation.feature.auth.NicknamePolicy

// 글자수 카운터 회색 (core token 미정의 — 10 프로필 만들기와 같은 값)
private val CounterGray = Color(0xFF9AA1AC)

/**
 * 36 · 프로필 수정 (신규)
 *
 * 진입: 35 마이페이지 프로필 카드 / 뒤로 → 35 / 「저장」 성공 → 35 (이름 다시 조회)
 *
 * 구성: 상단바 「프로필 수정」 → 닉네임 입력(글자수 · 중복 확인) → 「저장」
 *
 * 바꿀 수 있는 값이 닉네임 **하나뿐**인 이유: `PATCH /users/me` 가 받는 건 nickname 과 imageUrl 둘인데,
 * 프로필 사진은 업로드할 곳(스토리지 · 업로드 API)이 없어 보낼 값을 만들 수 없다. 실명 · 생년월일 ·
 * 성별 · 휴대폰 · 이메일은 서버에 수정 경로가 아예 없다. 그래서 「수정 불가」 행을 늘어놓는 대신
 * 바꿀 수 있는 것만 둔다(`docs/BACKEND-GAP-CLEANUP.md` 의 원칙).
 *
 * 규칙 · 문구 · 확인 방식은 10 프로필 만들기와 같은 것을 쓴다([NicknamePolicy], [NicknameCheckState],
 * 입력이 멈춘 뒤 자동 중복 확인) — 같은 값을 두 화면이 다른 기준으로 받으면 둘 중 하나는 거짓말이 된다.
 *
 * @param nickname 입력 중인 값. 진입 시 서버의 현재 표시 이름으로 채워져 있다.
 * @param currentNickname 서버가 준 현재 표시 이름. 이 값과 같으면 저장할 게 없다(「저장」 비활성).
 * @param check 중복 확인 결과. 입력이 멈춘 뒤 [ProfileEditRoute] 가 서버에 물어 갱신한다.
 * @param errorMessage 저장 실패 사유. 중복 · 형식 오류는 필드가 말하므로 여기엔 오지 않는다.
 */
@Composable
fun ProfileEditScreen(
    nickname: String,
    modifier: Modifier = Modifier,
    currentNickname: String? = null,
    check: NicknameCheckState = NicknameCheckState.Idle,
    saving: Boolean = false,
    errorMessage: String? = null,
    onNicknameChange: (String) -> Unit = {},
    onSave: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val fieldError = nicknameEditError(nickname, currentNickname, check)
    val unchanged = isNicknameUnchanged(nickname, currentNickname)
    val canSave = canSaveNickname(nickname, currentNickname, check, saving)

    Column(modifier = modifier.fillMaxSize().imePadding().background(MoyeotaColor.SurfaceSoft)) {
        StatusBarSpacer()
        MoyeotaTopBar(title = "프로필 수정", onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                // 10 의 「닉네임은 나중에 바꿀 수 있어요」 가 가리키던 화면이 여기다
                text = "닉네임을 바꿀 수 있어요",
                style = MoyeotaType.HeadingLg,
                color = MoyeotaColor.InkPrimary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                // 실명이 공개되지 않는다는 사실을 한 번 더 말해 둔다 — 10 에서 이미 안내했지만,
                // 이름을 바꾸러 들어온 순간이 그 사실을 가장 확인하고 싶을 때다
                text = "탑승 상대에게 보이는 유일한 이름이에요. 실명은 공개되지 않아요",
                style = MoyeotaType.BodySm.copy(fontSize = 14.sp),
                color = MoyeotaColor.TextMute,
            )

            Spacer(Modifier.height(24.dp))
            MoyeotaTextField(
                value = nickname,
                onValueChange = onNicknameChange,
                label = "닉네임",
                placeholder = "2~10자 한글·영문·숫자",
                errorText = fieldError,
                helperText = when {
                    fieldError != null -> null
                    check == NicknameCheckState.Checking -> "사용할 수 있는지 확인하고 있어요"
                    unchanged -> "지금 쓰고 있는 닉네임이에요"
                    nickname.isNotEmpty() -> "탑승 상대에게는 「$nickname」 으로 보여요"
                    else -> null
                },
                enabled = !saving,
                trailing = {
                    Text(
                        text = "${nickname.length} / ${NicknamePolicy.MAX_LENGTH}",
                        style = MoyeotaType.CaptionMd,
                        fontWeight = FontWeight.Medium,
                        color = CounterGray,
                    )
                },
            )
            // 사용 가능 확인만 초록으로 따로 세운다 — 나머지 상태는 필드가 자기 색으로 그린다.
            // 확인에 실패한 경우(네트워크)는 아무 말도 하지 않는다: 사용자가 고칠 수 있는 게 없다.
            if (fieldError == null && !unchanged && check == NicknameCheckState.Available) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "사용 가능한 닉네임이에요",
                    style = MoyeotaType.CaptionMd,
                    fontWeight = FontWeight.Medium,
                    color = MoyeotaColor.Success500,
                )
            }

            // 저장 실패 사유는 화면을 갈아엎지 않고 입력 바로 아래에 남긴다 — 고칠 곳이 위에 있기 때문
            // (04a 로그인과 같은 표기 방식)
            if (errorMessage != null) {
                Spacer(Modifier.height(16.dp))
                NoticeBanner(kind = NoticeKind.ERROR, text = errorMessage)
            }
            Spacer(Modifier.height(24.dp))
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            PrimaryCtaButton(
                text = "저장",
                onClick = onSave,
                enabled = canSave,
                loading = saving,
            )
        }
        Spacer(Modifier.height(16.dp))
        NavigationBarSpacer()
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ProfileEditScreenPreview() {
    MoyeotaTheme {
        ProfileEditScreen(nickname = "성윤", currentNickname = "성윤")
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "사용 가능")
@Composable
private fun ProfileEditScreenAvailablePreview() {
    MoyeotaTheme {
        ProfileEditScreen(
            nickname = "성윤이",
            currentNickname = "성윤",
            check = NicknameCheckState.Available,
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "중복")
@Composable
private fun ProfileEditScreenTakenPreview() {
    MoyeotaTheme {
        ProfileEditScreen(
            nickname = "길동이",
            currentNickname = "성윤",
            check = NicknameCheckState.Taken,
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "저장 실패")
@Composable
private fun ProfileEditScreenErrorPreview() {
    MoyeotaTheme {
        ProfileEditScreen(
            nickname = "길동이",
            currentNickname = "성윤",
            check = NicknameCheckState.Available,
            errorMessage = "네트워크 연결을 확인해 주세요",
        )
    }
}
