package com.moyeota.presentation.feature.mypage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
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
import com.moyeota.domain.model.ProfileImage
import com.moyeota.presentation.feature.auth.NicknameCheckState
import com.moyeota.presentation.feature.auth.NicknamePolicy

// 글자수 카운터 회색 (core token 미정의 — 10 프로필 만들기와 같은 값)
private val CounterGray = Color(0xFF9AA1AC)

/** 사진 격자의 열 수. 4 외에는 답이 없다 — 근거는 [ProfileImageGrid] KDoc. */
private const val PROFILE_IMAGE_COLUMNS = 4

/** 셀 사이 · 행 사이 간격. 이 앱이 쓰던 `spacedBy(12.dp)` 와 같은 값이다. */
private val ProfileImageGap = 12.dp

/**
 * 36 · 프로필 수정 (신규)
 *
 * 진입: 35 마이페이지 프로필 카드 / 뒤로 → 35 / 「저장」 성공 → 35 (이름·사진 다시 조회)
 *
 * 구성: 상단바 「프로필 수정」 → (로컬 로그인 계정만) 「아이디」 읽기 전용 행 → 닉네임 입력
 * (글자수 · 중복 확인) → 프로필 사진 격자 16장 + 「기본 이미지로 되돌리기」 → 「저장」
 *
 * 바꿀 수 있는 값이 **닉네임과 프로필 사진 둘**인 이유: `PATCH /api/v1/users/me` 가 받는 게 그 둘이고,
 * 사진은 서버가 준비한 16장 중에서 고르는 것뿐이다(업로드 경로가 서버에 없다). 실명 · 생년월일 ·
 * 성별 · 휴대폰 · 이메일은 서버에 수정 경로가 아예 없다. 그래서 「수정 불가」 행을 늘어놓는 대신
 * 바꿀 수 있는 것만 둔다(`docs/BACKEND-GAP-CLEANUP.md` 의 원칙).
 *
 * ### 「아이디」 행은 그 원칙의 **유일한 예외**다 (2026-11, 오너 승인)
 * 바꿀 수 없는 값인데도 두는 이유는 쓰임이 다르기 때문이다: 실명·생년월일은 사용자가 이미 아는 값이지만,
 * **로그인 아이디는 잊는 값**이고 「내 아이디가 뭐였지」를 확인하러 오는 자리가 바로 여기다. 서버가
 * `GET /local/users/info` 에 `loginId` 를 실어 주면서(Backend PR #330) 보여 줄 근거가 생겼다.
 *
 * - **읽기 전용 행**이지 비활성 입력칸이 아니다([LoginIdRow]) — 흐린 입력칸은 「언젠가 눌러서 바꾼다」로
 *   읽히는데 아이디는 바꾸는 서버 경로가 없다. 닉네임 필드와 같은 뼈대에 테두리 없는 채움 면이다.
 * - 로컬 로그인이 없는 계정(서버가 null)·조회 실패·공백이면 **행을 통째로 숨긴다**([loginId] 가 null).
 *   빈 값 행을 남기면 「아이디가 지워졌다」로 읽힌다.
 *
 * 규칙 · 문구 · 확인 방식은 10 프로필 만들기와 같은 것을 쓴다([NicknamePolicy], [NicknameCheckState],
 * 입력이 멈춘 뒤 자동 중복 확인) — 같은 값을 두 화면이 다른 기준으로 받으면 둘 중 하나는 거짓말이 된다.
 *
 * ### 지금 쓰는 사진을 격자에 미리 선택해 두지 않는다 — 열려 있는 결정이다
 * 서버는 내 사진의 **key** 를 알려 주는 창구가 없다 — `GET /api/v1/local/users/info` 는 URL 만 준다.
 * 그래서 격자의 선택 표시는 **「이번에 탭한 것」만** 뜻하고, 아무것도 안 탭했으면 아무 칸도
 * 선택되지 않는다. 지금 쓰는 사진은 35 마이페이지의 프로필 카드가 보여 준다.
 *
 * **「역추적이 엉뚱한 칸을 맞힌다」는 근거는 쓰지 말 것 — 사실이 아니다.** 백엔드는 두 URL 을 같은
 * 한 메서드로 만들고(`ProfileImages.toUrl(key) = baseUrl + key`), 목록의 `url` 도 내 `imageUrl` 도
 * 거기서 나온다. 즉 **문자열 완전 일치로 비교하면 빗나갈 수 없고**, base-url 이 바뀌면 양쪽이 함께
 * 바뀐다. 쿼리스트링이 붙어 일치가 깨지면 「선택 없음」이 되는데 그건 지금 동작과 같다.
 *
 * 그래서 이건 위험 회피가 아니라 **아직 안 만든 것**이다. 지금의 비용은 「내가 쓰는 사진이 어느 칸인지
 * 화면에서 알 수 없다」이고, 만든다면 `images.firstOrNull { it.url == 진입시_imageUrl }?.key` 를
 * **진입 시점의 key** 로 따로 들고 [ImageIntent.Pick] 과 구분해야 한다 — 그걸 Pick 으로 두면
 * 아무것도 안 바꿨는데 「저장」이 켜지고 불필요한 PATCH 가 나간다.
 *
 * @param nickname 입력 중인 값. 진입 시 서버의 현재 표시 이름으로 채워져 있다.
 * @param currentNickname 서버가 준 현재 표시 이름. 이 값과 같으면 **닉네임은** 저장할 게 없다
 *   — 사진이 바뀌었으면 「저장」 은 켜진다([canSaveProfile]).
 * @param check 중복 확인 결과. 입력이 멈춘 뒤 [ProfileEditRoute] 가 서버에 물어 갱신한다.
 * @param images 고를 수 있는 프리셋. **비어 있으면 사진 섹션을 접는다** — 조회 실패에 문구를 띄우지
 *   않는다(사용자가 고칠 게 없다). 닉네임 수정은 그대로 된다.
 * @param imageIntent 이 화면에서 사진에 내린 결정. 격자의 선택 표시와 「저장」 활성의 근거다.
 * @param usingDefaultImage 진입 시점의 사진이 기본 이미지인가. 「되돌리기」를 보일지만 정한다.
 * @param errorMessage 저장 실패 사유. 중복 · 형식 오류는 필드가 말하므로 여기엔 오지 않는다.
 *   사진 키 오류(400 `USER112`)는 **여기로** 온다 — 닉네임 필드로 돌리면 거짓이다.
 * @param loginId 보여 줄 로그인 아이디. **null 이면 「아이디」 행을 그리지 않는다**
 *   (`profileLoginIdOrNull` 이 공백·미조회·로컬 로그인 없음을 모두 null 로 접어 준다).
 */
@Composable
fun ProfileEditScreen(
    nickname: String,
    modifier: Modifier = Modifier,
    loginId: String? = null,
    currentNickname: String? = null,
    check: NicknameCheckState = NicknameCheckState.Idle,
    images: List<ProfileImage> = emptyList(),
    imageIntent: ImageIntent = ImageIntent.None,
    usingDefaultImage: Boolean = true,
    saving: Boolean = false,
    errorMessage: String? = null,
    onNicknameChange: (String) -> Unit = {},
    onImagePick: (String) -> Unit = {},
    /** 사진 목록 조회가 실패했다. **빈 목록과 다르게** 그린다 — 섞으면 섹션이 통째로 사라진다 */
    imagesLoadFailed: Boolean = false,
    onRetryImages: () -> Unit = {},
    onImageReset: () -> Unit = {},
    onSave: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val fieldError = nicknameEditError(nickname, currentNickname, check)
    val unchanged = isNicknameUnchanged(nickname, currentNickname)
    val canSave = canSaveProfile(nickname, currentNickname, check, imageIntent, saving)
    val canResetImage = canResetProfileImage(usingDefaultImage, imageIntent)

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
                text = "닉네임과 프로필 사진을 바꿀 수 있어요",
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

            // 「아이디」는 닉네임 **위**다 — 계정의 신원(바뀌지 않는 것)이 먼저, 꾸미는 값이 그다음이다.
            // 공백·null 이면 행과 그 아래 간격까지 통째로 없다(빈 자리를 남기지 않는다).
            if (!loginId.isNullOrBlank()) {
                Spacer(Modifier.height(24.dp))
                LoginIdRow(loginId = loginId)
            }

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

            // 사진 섹션은 닉네임 입력 **아래**다. 위에 두면 격자 높이가 콘텐츠 폭과 같아져
            // (360dp 기기에서 328dp) 닉네임 입력칸이 접힌선 아래로 밀려 360x640 화면에서 보이지
            // 않는다 — 이 화면에 들어오는 가장 흔한 이유가 닉네임 변경인데 그게 안 보이게 된다.
            //
            // 목록도 없고 되돌릴 것도 없으면 섹션 자체를 접는다. 빈 자리에 「준비된 그림 중에서
            // 고를 수 있어요」 만 남으면 그 문장이 거짓이 된다.
            //
            // **단 「조회 실패」는 접지 않는다.** 접으면 사진을 바꾸러 들어온 사용자에게 섹션이
            // 흔적 없이 사라져 「이 앱에는 사진 기능이 없다」로 읽힌다 — 고장인지 아닌지도 알 수 없고
            // 다시 받을 손잡이도 없다. 그래서 실패는 섹션을 **남기고** 그 자리에서 말한다.
            if (images.isNotEmpty() || canResetImage || imagesLoadFailed) {
                Spacer(Modifier.height(24.dp))
                Text(text = "프로필 사진", style = MoyeotaType.HeadingMd, color = MoyeotaColor.InkPrimary)
                if (images.isEmpty() && imagesLoadFailed) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "사진 목록을 불러오지 못했어요",
                        style = MoyeotaType.BodySm,
                        color = MoyeotaColor.TextMute,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "다시 시도",
                        style = MoyeotaType.BodySm.copy(fontWeight = FontWeight.Bold),
                        color = MoyeotaColor.Primary500,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !saving) { onRetryImages() }
                            .padding(horizontal = 12.dp)
                            .wrapContentHeight(),
                    )
                }
                if (images.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        // 왜 업로드 칸이 없는지 한 줄로 답한다. 10 프로필 만들기의 「사진은 올리지
                        // 않아요」 와 모순되지 않는다 — 올리는 건 여전히 없고, 준비된 그림을 고른다.
                        text = "준비된 그림 중에서 고를 수 있어요",
                        style = MoyeotaType.BodySm,
                        color = MoyeotaColor.TextMute,
                    )
                    Spacer(Modifier.height(ProfileImageGap))
                    ProfileImageGrid(
                        images = images,
                        // 선택 표시는 「이번에 탭한 것」만 뜻한다 — 함수 KDoc 의 역추적 금지 참조.
                        selectedKey = (imageIntent as? ImageIntent.Pick)?.key,
                        enabled = !saving,
                        onPick = onImagePick,
                    )
                }
                if (canResetImage) {
                    Spacer(Modifier.height(4.dp))
                    ProfileImageResetAction(enabled = !saving, onClick = onImageReset)
                }
            }

            // 저장 실패 사유는 화면을 갈아엎지 않고 입력·격자 아래에 남긴다 — 고칠 곳이 위에 있기
            // 때문 (04a 로그인과 같은 표기 방식)
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

/**
 * 「아이디」 읽기 전용 행 — 닉네임 필드와 **같은 뼈대**(라벨 위 · 8dp · 52dp 줄 · 안쪽 16dp)에
 * **테두리 없는 `SurfaceSoft` 채움**을 얹는다(디자인 QA 4-1).
 *
 * ### 왜 비활성 [MoyeotaTextField] 가 아닌가
 * 회색으로 죽은 입력칸(흐린 글자 + 테두리)은 「지금은 막혔지만 원래는 고치는 칸」으로 읽힌다 — 사용자는
 * 어딘가에 잠금을 푸는 길이 있다고 생각하고 찾는다. 아이디는 바꾸는 서버 경로 자체가 없다. 그래서
 * 이 행은 입력칸의 표지(테두리·포커스·커서)를 하나도 갖지 않고, 값은 **흐리게 하지 않는다**(`BodyMd`
 * · `InkPrimary` — 비활성처럼 보이는 낮은 대비를 쓰지 않는다). 채움 면은 「값을 담은 칸」이라는 것만
 * 말한다. 자물쇠·꺾쇠·도움말 글은 두지 않는다 — 누를 것이 없는 줄에 누를 것처럼 보이는 표지를 달지 않는다.
 *
 * 뼈대를 닉네임 필드와 맞추는 이유는 두 줄이 **같은 목록의 항목**으로 읽혀야 해서다(높이·여백이 다르면
 * 서로 다른 종류의 요소로 보인다).
 *
 * 스크린리더에는 「아이디, hong123」 한 문장으로 읽힌다 — 따로 읽히면 라벨과 값이 무엇의 짝인지
 * 끊긴다. 누를 수 있는 것이 없으므로 클릭 의미는 싣지 않는다.
 * iOS 포팅: 같은 모양에 복사를 허용하려면 `.textSelection(.enabled)`.
 */
@Composable
private fun LoginIdRow(loginId: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "아이디, $loginId" },
    ) {
        Text(text = "아이디", style = MoyeotaType.BodySm, color = MoyeotaColor.TextMute)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(MoyeotaColor.SurfaceSoft, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = loginId,
                style = MoyeotaType.BodyMd,
                color = MoyeotaColor.InkPrimary,
                // 아이디는 공백 없는 한 덩어리다 — 길면 줄을 바꾸기보다 끝을 줄인다(전체는 스크린리더가 읽는다)
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 프리셋 사진 격자.
 *
 * ### `LazyVerticalGrid` 를 쓰면 **런타임에 죽는다**
 * 이 본문은 이미 `verticalScroll` 안이다. 세로 스크롤 안에 세로 Lazy 격자를 넣으면
 * `Vertically scrollable component was measured with an infinity maximum height` 로 터진다
 * — 컴파일은 통과하고 **화면을 열 때만** 보이는 종류의 사고다. 그래서 `chunked` + `Row` 로 직접 깐다.
 * 목록은 수가 작고 고정(16장)이라 Lazy 로 아낄 것도 없다.
 *
 * ### 4열이 유일한 답이다
 * 셀 폭 = (화면폭 − 좌우 16dp 둘 − 간격 12dp 셋) / 4 → 320dp 에서 63dp, 360dp 에서 73dp,
 * 412dp 에서 86dp. 8열은 전 구간에서 48dp 터치 타깃에 못 미치고, 3열·5열은 16을 나누지 못해
 * 마지막 행이 깨진다.
 *
 * 마지막 행에 남는 칸은 [Spacer] 로 채워 **왼쪽 정렬을 유지한다.** Repository 계약이 「수를 가정하지
 * 말라」고 적어 둔 대로 서버가 16장이 아닌 수를 줄 수 있는데, 빈 칸을 안 채우면 그 행의 사진만
 * 커져 격자가 무너진다.
 */
@Composable
private fun ProfileImageGrid(
    images: List<ProfileImage>,
    selectedKey: String?,
    enabled: Boolean,
    onPick: (String) -> Unit,
) {
    Column(
        // 격자 전체가 **한 묶음의 라디오**라는 사실을 스크린리더에 싣는다. 없으면 16개의 독립된
        // 버튼으로 들려 「하나만 고른다」는 규칙이 사라진다(09 통신사 · 10 성별과 같은 처리).
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(ProfileImageGap),
    ) {
        images.chunked(PROFILE_IMAGE_COLUMNS).forEachIndexed { rowIndex, rowImages ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ProfileImageGap),
            ) {
                rowImages.forEachIndexed { columnIndex, image ->
                    ProfileImageCell(
                        image = image,
                        // 서버가 key 외에 이름을 주지 않아 번호로 읽어 준다.
                        number = rowIndex * PROFILE_IMAGE_COLUMNS + columnIndex + 1,
                        selected = image.key == selectedKey,
                        enabled = enabled,
                        onClick = { onPick(image.key) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(PROFILE_IMAGE_COLUMNS - rowImages.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * 격자의 한 칸.
 *
 * **색만으로 선택을 표시하지 않는다.** 이 앱에는 그런 자리가 한 곳도 없고, 항상 (테두리 또는 채움) +
 * (굵기 또는 그림자)가 함께 바뀐다 — 성별 칩 `SignupChoiceChip` 과 `MoyeotaChip` 이 쓰는 문법이다.
 * 여기서는 배경(`SurfaceCanvas` → `Primary50`)과 테두리(1dp `Hairline` → 2dp `Primary500`)가
 * 같이 바뀐다. 인원수 칩의 솔리드 채움은 **쓸 수 없다 — 그림을 덮는다.**
 *
 * 안쪽 4dp 여백 덕에 선택 링이 그림 위에 겹치지 않는다. 프리셋은 투명 배경 위 원형 아트워크라
 * (알파 0 이 20.6%) 꽉 채우면 원 테두리와 그림의 외곽선이 맞붙어 지저분해진다.
 *
 * `clickable` 이 아니라 [selectable] 을 쓰는 이유는 성별 칩과 같다 — 선택 상태와
 * [Role.RadioButton] 을 의미 트리에 실어야 「지금 어느 칸이 골라져 있는가」가 스크린리더에 전달된다.
 */
@Composable
private fun ProfileImageCell(
    image: ProfileImage,
    number: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            // 폭은 Row 의 weight 가 주고, 높이는 여기서 정사각으로 끌어온다.
            .aspectRatio(1f)
            .background(
                if (selected) MoyeotaColor.Primary50 else MoyeotaColor.SurfaceCanvas,
                CircleShape,
            )
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MoyeotaColor.Primary500 else MoyeotaColor.Hairline,
                shape = CircleShape,
            )
            // clip 이 selectable **앞**이어야 터치 물결이 원 밖으로 번지지 않는다. Material3 의
            // bounded ripple 은 clip 이 없으면 레이아웃 노드의 **사각** 경계로 클립되므로, 원 바깥
            // 네 모서리(페이지 배경이 보이는 자리)까지 사각 하이라이트가 칠해진다.
            // 아래 [ProfileImageResetAction] 이 같은 순서를 쓴다.
            .clip(CircleShape)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(4.dp),
    ) {
        AsyncImage(
            model = image.url,
            contentDescription = "프로필 이미지 $number",
            // AsyncImage 는 intrinsic size 가 없다 — 크기 modifier 를 빼면 0x0 으로 측정돼
            // **이미지는 멀쩡히 로드되는데 화면에는 안 보인다.** 부모가 정사각으로 고정돼 있으므로
            // fillMaxSize 가 확정 크기를 준다.
            modifier = Modifier.fillMaxSize().clip(CircleShape),
            // 기본값이 Fit 이라 그대로 두면 원 안에서 레터박스가 된다.
            contentScale = ContentScale.Crop,
        )
    }
}

/**
 * 「기본 이미지로 되돌리기」.
 *
 * 이 앱은 화면 본문에 `TextButton` 을 쓰지 않는다 — 쓰인 11건이 전부 `AlertDialog` 안이다.
 * 그래서 같은 뜻을 파란 굵은 글씨 한 줄로 낸다.
 *
 * 높이를 [heightIn] 으로 48dp 이상 벌려 둔다. 14sp 글자 한 줄은 터치 영역이 25dp 도 안 되어
 * 「저장」 바로 위에서 오타처럼 눌린다. `clip` → `clickable` → `padding` 순서여야
 * 물결 효과가 모서리를 넘지 않고, 그 여백까지 눌린다.
 *
 * 누르면 **로컬 선택만 바뀐다.** 이 화면의 커밋 지점은 「저장」 하나다 — 되돌리기만 즉시 서버로
 * 나가면 「저장을 안 눌렀는데 사진이 바뀜」이 생긴다.
 */
@Composable
private fun ProfileImageResetAction(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = "기본 이미지로 되돌리기",
            style = MoyeotaType.ButtonMd,
            fontWeight = FontWeight.Bold,
            color = MoyeotaColor.Primary500,
        )
    }
}

/**
 * Preview 용 프리셋 16장. key·url 형태는 **백엔드 소스 기준**이다(`profile/presets/01.png` 부터
 * `16.png`, 제로패딩) — 이번 작업에서 로컬 서버 DB 가 죽어 API 를 실제로 호출하지는 못했다.
 *
 * Preview 렌더러는 네트워크를 쓰지 않으므로 칸은 빈 원으로 보인다. 그래도 **격자의 치수와 선택
 * 표시**는 그대로 검증된다(그게 이 Preview 가 있는 이유다).
 */
private val PreviewProfileImages: List<ProfileImage> = List(16) { index ->
    val number = (index + 1).toString().padStart(2, '0')
    ProfileImage(
        key = "profile/presets/$number.png",
        url = "https://cdn.moyeota.co.kr/profile/presets/$number.png",
    )
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun ProfileEditScreenPreview() {
    MoyeotaTheme {
        ProfileEditScreen(nickname = "성윤", currentNickname = "성윤")
    }
}

/** 로컬 로그인 계정 — 닉네임 위에 「아이디」 읽기 전용 행이 붙는다. 360dp 에서 입력칸처럼 보이지 않는지 본다. */
@Preview(showBackground = true, widthDp = 360, heightDp = 780, name = "아이디 표시")
@Composable
private fun ProfileEditScreenLoginIdPreview() {
    MoyeotaTheme {
        ProfileEditScreen(nickname = "성윤", currentNickname = "성윤", loginId = "hong123")
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

/**
 * 격자가 그려진 상태. 닉네임은 그대로 두고 **사진만 고른** 모습이다 — 이 화면의 원래 버그가
 * 여기서 「저장」 이 꺼지는 것이었으므로, Preview 의 「저장」 이 파랗게 켜져 있는지가 확인점이다.
 * 320dp 폭을 쓰는 이유는 4열 셀이 가장 좁아지는(63dp) 경우를 보려는 것이다.
 */
@Preview(showBackground = true, widthDp = 320, heightDp = 852, name = "사진 선택")
@Composable
private fun ProfileEditScreenImagesPreview() {
    MoyeotaTheme {
        ProfileEditScreen(
            nickname = "성윤",
            currentNickname = "성윤",
            images = PreviewProfileImages,
            imageIntent = ImageIntent.Pick(PreviewProfileImages[2].key),
            usingDefaultImage = true,
        )
    }
}

/**
 * 목록 조회가 실패한 상태 — 사진 섹션이 접히고 **닉네임 수정은 그대로 된다.**
 * 실패 문구는 띄우지 않는다(사용자가 고칠 게 없다).
 */
@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "사진 목록 조회 실패")
@Composable
private fun ProfileEditScreenNoImagesPreview() {
    MoyeotaTheme {
        ProfileEditScreen(
            nickname = "성윤이",
            currentNickname = "성윤",
            check = NicknameCheckState.Available,
            images = emptyList(),
        )
    }
}
