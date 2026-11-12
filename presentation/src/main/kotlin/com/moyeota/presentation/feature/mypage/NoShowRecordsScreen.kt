package com.moyeota.presentation.feature.mypage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.moyeota.core.designsystem.component.MoyeotaTopBar
import com.moyeota.core.designsystem.component.NoticeBanner
import com.moyeota.core.designsystem.component.NoticeKind
import com.moyeota.core.designsystem.component.PrimaryCtaButton
import com.moyeota.core.designsystem.component.SecondaryButton
import com.moyeota.core.designsystem.component.StatusBadge
import com.moyeota.core.designsystem.component.StatusBarSpacer
import com.moyeota.core.designsystem.theme.MoyeotaColor
import com.moyeota.core.designsystem.theme.MoyeotaShape
import com.moyeota.core.designsystem.theme.MoyeotaType
import com.moyeota.domain.model.NoShowRecord
import com.moyeota.domain.model.ObjectionStatus

/**
 * 38 · 노쇼 기록 · 이의 신청 [신규]
 *
 * 진입: 35 마이페이지 「노쇼 기록」 행.
 *
 * 서버: `GET /api/v1/matching/no-shows` — **노쇼 시각 최신순 최대 20개**(페이징이 없다),
 * `POST /api/v1/matching/rooms/{partyId}/no-show-objection` (201, 본문 없음).
 *
 * ```
 * ← 노쇼 기록
 *   노쇼 1건마다 그 방 약속 시간부터 24시간 동안 …     ← 제재 계산식
 *   ┌───────────────────────────────┐
 *   │ 강남역 → 판교역                        │
 *   │ 약속 시간   11월 5일 오후 4:15          │
 *   │ 노쇼 시각   11월 5일 오후 4:20          │
 *   │ ─────────────────────────── │
 *   │ [      이의 신청      ]                │  ← 이 상태의 줄에만 생긴다
 *   └───────────────────────────────┘
 *   ┌───────────────────────────────┐
 *   │ 부산대 정문 → 서면역      [이의 검토 중]  │
 *   │ …                                     │
 *   │ 운영자가 확인한 결과는 이 화면에서 …      │
 *   └───────────────────────────────┘
 * ```
 *
 * ### 이 화면이 왜 있는가
 * PR #82 로 앱이 노쇼를 **만들** 수 있게 됐다(「출발」을 누르면 도착하지 않은 사람 전원이 노쇼로
 * 찍힌다). 받는 쪽은 비어 있었다 — 제재당한 사용자는 방 만들기·합류가 403 `NO_SHOW_RESTRICTED` 로
 * 막히는 것만 보고 **이유도 기한도 알 수 없었고, 잘못 찍혀도 다툴 길이 없었다.** 이 화면이 그
 * 다투는 길이다.
 *
 * ### 이의 신청 버튼이 생기는 줄은 하나다 ★
 * `objectionStatus == null && !voided` — 서버 Swagger 가 그렇게 지시하고, 서버가 201 을 주는
 * 조건도 같다. 판정은 [noShowRowState]·[NoShowItem.allowsObjection] 이 하고 화면은 그 결과만
 * 본다(화면이 다시 두 필드를 보면 두 개의 진실이 생긴다).
 *
 * ### 그리지 않는 것과 그 이유
 * - **처리 시한·결과 알림** — 운영 약속이 없고 결과 푸시도 없다. 사용자가 결과를 아는 길은
 *   이 화면을 다시 여는 것뿐이라, 문구도 그렇게만 적는다([noShowNoteText]).
 * - **「제한이 풀렸어요」** — 무효가 된 노쇼 한 건이 곧 제한 해제는 아니다(다른 노쇼가 남아
 *   있을 수 있다). 지금 제한 여부는 35 의 배너가 서버 `restrictedUntil` 로만 말한다.
 * - **동승자·방장 이름** — 이 응답에 멤버가 없다. 「누가 출발을 눌렀는지」도 서버가 주지 않는다.
 * - **페이지 더 보기** — 서버가 20건까지만 주고 커서도 개수 파라미터도 없다. 「더 보기」를 달면
 *   누를 수 없는 버튼이 된다. 대신 목록이 **꽉 찼을 때만** 「최근 20건만 볼 수 있어요」를 적는다
 *   ([noShowHistoryCapNotice]) — 정확히 20건인지 잘린 건지는 응답으로 구분할 수 없지만 그 문구는
 *   두 경우 모두 참이고, 아무 말도 없으면 사용자는 그 목록이 전부라고 읽는다.
 * - **요금·탑승 인원** — 34 지난 탑승 기록의 값이고 이 응답에는 없다.
 *
 * ### 색은 토큰만 쓴다
 * 34 가 세운 규칙을 따른다 — 파일-로컬 그레이를 만들지 않는다. 상태 색은 디자인시스템의
 * [StatusBadge]·[NoticeBanner] 가 `NoticeKind` 로 들고 있는 네 쌍뿐이고, 그 매핑만 이 파일이 한다
 * ([noShowBadgeKind]) — 판정과 글자는 `NoShowRecordsDisplay.kt` 가 맡는다.
 */
@Composable
internal fun NoShowRecordsScreen(
    records: List<NoShowRecord> = emptyList(),
    /** 첫 조회 중. 목록이 비어 있을 때만 전체 스피너를 그린다. */
    loading: Boolean = false,
    /** 조회가 실패했는가. 문구는 하나다 — 사용자가 할 일은 「다시 시도」뿐이다. */
    failed: Boolean = false,
    /** 이의 신청 시트가 열려 있는 방. null 이면 닫혀 있다. */
    objectionTarget: Long? = null,
    reason: String = "",
    submitting: Boolean = false,
    /** 직전 이의 신청이 거절된 사유. 시트 안 배너가 된다. */
    objectionFailure: NoShowObjectionFailure? = null,
    /** 201 을 받았다 — 목록 위에 안내를 띄운다. */
    objectionSent: Boolean = false,
    onObjectionOpen: (Long) -> Unit = {},
    onObjectionDismiss: () -> Unit = {},
    onReasonChange: (String) -> Unit = {},
    onObjectionSubmit: () -> Unit = {},
    onRetry: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val items = remember(records) { records.map { it.toNoShowItem() } }

    Column(modifier = Modifier.fillMaxSize().background(MoyeotaColor.SurfaceSoft)) {
        StatusBarSpacer()
        MoyeotaTopBar(title = "노쇼 기록", onBack = onBack)

        when {
            // 첫 조회가 끝나기 전에는 빈 상태 문구를 그리지 않는다 — 노쇼가 있는 사용자에게
            // 「기록된 적이 없어요」가 한 번 스쳐 보이면 그건 거짓말이 한 프레임 보인 것이다.
            loading && items.isEmpty() -> CenteredPane { CircularProgressIndicator() }

            failed && items.isEmpty() -> CenteredPane {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = NO_SHOW_ERROR_TEXT,
                        style = MoyeotaType.BodyMd,
                        color = MoyeotaColor.TextMute,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    SecondaryButton(text = "다시 시도", onClick = onRetry)
                }
            }

            // 노쇼가 0건인 사용자가 보는 화면. **제재 계산식을 여기서는 적지 않는다** —
            // 겪지도 않은 사람에게 24시간 제한을 설명하는 것은 안내가 아니라 경고다.
            items.isEmpty() -> CenteredPane {
                Text(
                    text = NO_SHOW_EMPTY_TEXT,
                    style = MoyeotaType.BodyMd,
                    color = MoyeotaColor.TextMute,
                    textAlign = TextAlign.Center,
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (objectionSent) {
                    item { NoticeBanner(kind = NoticeKind.SUCCESS, text = OBJECTION_SENT_TEXT) }
                }
                // 기록이 **있는** 사람에게만 계산식을 보여 준다. 자기 제한이 언제 풀리는지
                // 셀 수 있는 유일한 근거다(약속 시간 + 24시간 × 건수).
                item {
                    Text(
                        text = NO_SHOW_RULE_TEXT,
                        style = MoyeotaType.BodySm,
                        color = MoyeotaColor.TextMute,
                    )
                }
                // 목록을 들고 있는데 조회가 실패한 상태(새로 받기 실패) — 조용히 옛 목록을
                // 보여 주면 사용자는 방금 낸 이의가 반영됐는지 알 수 없다.
                if (failed) {
                    item { NoticeBanner(kind = NoticeKind.ERROR, text = NO_SHOW_ERROR_TEXT) }
                }
                items(items = items, key = { it.partyId }) { item ->
                    NoShowCard(item = item, onObject = { onObjectionOpen(item.partyId) })
                }
                // 목록이 꽉 찼을 때만 「전체가 아니다」를 말한다 — 「더 보기」는 달 수 없다
                // (커서도 개수 파라미터도 없다). 판정은 `noShowHistoryCapNotice`.
                noShowHistoryCapNotice(items.size)?.let { notice ->
                    item {
                        Text(
                            text = notice,
                            style = MoyeotaType.CaptionMd,
                            // 읽어야 하는 사실(「최근 N건만 보여요」)이라 장식 회색이 아니라 보조 글자색이다
                            color = MoyeotaColor.TextMute,
                        )
                    }
                }
            }
        }
    }

    // 이의 신청 시트. 목록 위에 겹쳐 띄운다 — 사유를 쓰는 동안에도 어느 방에 대한 것인지
    // 카드가 뒤에 남아 있어야 한다(시트 안에도 경로를 한 번 더 인용한다).
    if (objectionTarget != null) {
        val target = items.firstOrNull { it.partyId == objectionTarget }
        ObjectionSheet(
            routeLabel = target?.routeLabel,
            reason = reason,
            submitting = submitting,
            failure = objectionFailure,
            onReasonChange = onReasonChange,
            onSubmit = onObjectionSubmit,
            onDismiss = onObjectionDismiss,
        )
    }
}

@Composable
private fun CenteredPane(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** 상태 → 배지·부연의 색. **글자는 `NoShowRecordsDisplay.kt` 가 만든다**(그 파일은 색을 모른다). */
private fun noShowBadgeKind(state: NoShowRowState): NoticeKind = when (state) {
    // 배지를 그리지 않는 상태지만 when 을 빠짐없이 갈라 둔다 — 상태가 늘면 여기서 컴파일이 막힌다.
    NoShowRowState.OPEN -> NoticeKind.INFO
    NoShowRowState.PENDING -> NoticeKind.WAITING
    // 「무효」와 「이의 인정됨」은 사용자에게 좋은 소식이다 — 성공 색이 맞다.
    NoShowRowState.ACCEPTED, NoShowRowState.VOIDED -> NoticeKind.SUCCESS
    // 거절은 빨강이다. 다툼이 끝났다는 사실의 무게가 그만큼이고, 이 화면에서 빨간 배지는
    // 이것 하나뿐이라 눈에 걸린다(35 의 탈퇴 행이 카드 안 유일한 빨강인 것과 같은 규칙).
    NoShowRowState.REJECTED -> NoticeKind.ERROR
}

/**
 * 노쇼 한 장. **눌러도 갈 곳이 없어 카드 자체에는 탭 동작과 꺾쇠를 두지 않았다**
 * (34 지난 탑승 기록과 같은 판단 — 끝난 방의 상세 화면은 진행 중 방을 전제로 쓰여 있다).
 * 이 카드에서 유일하게 눌리는 것은 이의 신청 버튼이고, 그래서 그 버튼이 곧 상태 표시가 된다.
 */
@Composable
private fun NoShowCard(item: NoShowItem, onObject: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MoyeotaShape.Card)
            .background(MoyeotaColor.SurfaceCard)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (item.routeLabel != null) {
                Text(
                    text = item.routeLabel,
                    style = MoyeotaType.HeadingMd,
                    color = MoyeotaColor.InkPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                // 경로를 둘 다 모르는 응답. 빈 화살표를 그리지 않되 배지 자리는 지킨다.
                Spacer(Modifier.weight(1f))
            }
            if (item.badgeText != null) {
                Spacer(Modifier.width(8.dp))
                StatusBadge(kind = noShowBadgeKind(item.state), text = item.badgeText)
            }
        }

        if (item.meetingLabel != null || item.markedLabel != null) {
            Spacer(Modifier.height(12.dp))
            // 두 시각을 **이름을 붙여** 나란히 둔다. 날짜만 두 줄 있으면 어느 쪽이 내 약속이었고
            // 어느 쪽이 찍힌 때인지 알 수 없는데, 제한은 **약속 시간부터** 세므로 그 구분이
            // 사용자가 자기 제한 기한을 계산하는 근거다.
            item.meetingLabel?.let { TimeRow(label = NO_SHOW_FIELD_MEETING, value = it) }
            item.markedLabel?.let {
                if (item.meetingLabel != null) Spacer(Modifier.height(4.dp))
                TimeRow(label = NO_SHOW_FIELD_MARKED, value = it)
            }
        }

        if (item.note != null) {
            Spacer(Modifier.height(8.dp))
            Text(text = item.note, style = MoyeotaType.BodySm, color = MoyeotaColor.TextMute)
        }

        // ★ 이 줄이 이 화면의 1급 규칙이다 — 서버가 201 을 주는 조건과 같은 조건에서만 그린다.
        if (item.allowsObjection) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MoyeotaColor.Hairline)
            Spacer(Modifier.height(12.dp))
            SecondaryButton(text = "이의 신청", onClick = onObject)
        }
    }
}

@Composable
private fun TimeRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MoyeotaType.BodySm,
            color = MoyeotaColor.TextMute,
            modifier = Modifier.width(64.dp),
        )
        Text(text = value, style = MoyeotaType.BodySm, color = MoyeotaColor.TextBody)
    }
}

/**
 * 이의 사유를 쓰는 시트.
 *
 * ### 보낼 수 없는 글을 보내게 두지 않는다
 * 서버는 `reason` 을 **trim 한 뒤 1~500자**로만 받고 벗어나면 400 `INVALID_REQUEST` 다. 그 응답
 * 문구로는 무엇을 고쳐야 하는지 알 수 없으므로, 카운터를 **처음부터** 보여 주고 버튼을 잠근다
 * ([objectionReasonUi] — 잠긴 이유가 카운터에 이미 적혀 있다).
 *
 * ### 되돌릴 수 없는 한 번이다
 * 방마다 한 번뿐이라 보내고 나면 고칠 수 없다. 그래서 확인 다이얼로그를 또 겹치지 않는 대신
 * **시트 안에 그 사실을 적고**([OBJECTION_SHEET_NOTICE]) 보내기 버튼을 유일한 Primary 로 둔다.
 *
 * ### 실패가 끝인지 아닌지를 버튼으로 말한다
 * 403·409·404 는 다시 눌러도 같은 답이다([NoShowObjectionFailure.isTerminal]) — 그때는 보내기를
 * 잠그고 「닫기」만 남긴다. 문구에 「다시 시도」류를 넣지 않는 것과 같은 규칙을 버튼에도 적용한
 * 것이다(`FinishFailureMessageTest` 의 바). 연결 실패(UNKNOWN)만 다시 누를 수 있다.
 *
 * 디자인시스템의 `MoyeotaTextField` 를 쓰지 않은 이유는 24 채팅의 신고 상세 칸과 같다 — 그
 * 컴포넌트는 입력 박스를 52dp 고정 높이로 그려서 500자를 받는 칸에서는 글이 잘려 보인다.
 */
@Composable
private fun ObjectionSheet(
    routeLabel: String?,
    reason: String,
    submitting: Boolean,
    failure: NoShowObjectionFailure?,
    onReasonChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val input = objectionReasonUi(reason = reason, submitting = submitting)
    // 끝난 실패는 다시 보낼 수 없다 — 잠그는 판단은 enum 이 들고 있고 화면은 묻기만 한다.
    val terminal = failure?.isTerminal == true

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 키보드가 올라오면 시트가 그만큼 올라와야 한다 — 없으면 500자를 다 쓴 사용자가
            // 키보드 뒤에 숨은 보내기 버튼을 누를 수 없다(24 채팅 시트가 같은 처리를 한다).
            .imePadding()
            .background(MoyeotaColor.Scrim)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .clip(MoyeotaShape.Dialog)
                .background(MoyeotaColor.SurfaceCanvas)
                // 시트 안을 눌렀을 때 바깥 클릭(닫기)으로 새지 않게 막는다 — 쓴 글이 사라진다.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { }
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text(
                text = OBJECTION_SHEET_TITLE,
                style = MoyeotaType.HeadingMd,
                color = MoyeotaColor.InkPrimary,
            )
            if (routeLabel != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    // 홑낫표로 인용한다 — 공통 규칙(인용값은 「」). 어느 방에 대한 이의인지
                    // 보여 주지 않으면 20개 목록에서 누른 카드가 맞는지 확인할 길이 없다.
                    text = "「$routeLabel」",
                    style = MoyeotaType.BodySm,
                    color = MoyeotaColor.TextMute,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .background(MoyeotaColor.SurfaceSoft, MoyeotaShape.Card)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = reason,
                    // 한도의 2배에서 자른다 — 붙여넣기 폭주만 막고, 「넘었다」는 카운터가 말한다.
                    onValueChange = { onReasonChange(it.take(OBJECTION_REASON_HARD_LIMIT)) },
                    textStyle = MoyeotaType.BodyMd.copy(color = MoyeotaColor.InkPrimary),
                    // 여러 줄로 받는다 — 상황을 적는 칸이라 한 줄로 가로 스크롤되면 쓴 글을
                    // 다시 읽을 수가 없다.
                    singleLine = false,
                    enabled = !submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (reason.isEmpty()) {
                    Text(
                        text = OBJECTION_INPUT_PLACEHOLDER,
                        style = MoyeotaType.BodyMd,
                        color = MoyeotaColor.TextPlaceholder,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = input.counter,
                style = MoyeotaType.CaptionMd,
                color = if (input.overLimit) MoyeotaColor.Danger600 else MoyeotaColor.TextMute,
            )

            Spacer(Modifier.height(12.dp))
            Text(
                text = OBJECTION_SHEET_NOTICE,
                style = MoyeotaType.CaptionMd,
                color = MoyeotaColor.TextMute,
            )

            if (failure != null) {
                Spacer(Modifier.height(12.dp))
                NoticeBanner(
                    kind = NoticeKind.ERROR,
                    text = noShowObjectionFailureMessage(failure),
                )
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton(
                    text = if (terminal) "닫기" else "취소",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    enabled = !submitting,
                )
                PrimaryCtaButton(
                    text = if (submitting) OBJECTION_SUBMITTING_LABEL else OBJECTION_SUBMIT_LABEL,
                    onClick = onSubmit,
                    modifier = Modifier.weight(1f),
                    enabled = input.submitEnabled && !terminal,
                    loading = submitting,
                )
            }
        }
    }
}

// ─────────────────────────── Preview ───────────────────────────

// 다섯 상태가 **한 화면에서** 서로 구분되는지 본다 — 배지 색·부연·버튼이 전부 다른 자리다.
private val PreviewRecords = listOf(
    // 이의를 낼 수 있는 유일한 줄 — 버튼이 이 카드에만 있어야 한다
    NoShowRecord(
        partyId = 101,
        departure = "강남역 2번 출구",
        destination = "판교역",
        meetingAt = "2026-11-05T07:15:00Z",
        noShowAt = "2026-11-05T07:20:11Z",
        isVoided = false,
        objectionStatus = null,
    ),
    NoShowRecord(
        partyId = 102,
        departure = "부산대 정문",
        destination = "서면역 1번 출구",
        meetingAt = "2026-11-03T09:00:00Z",
        noShowAt = "2026-11-03T09:02:40Z",
        isVoided = false,
        objectionStatus = ObjectionStatus.PENDING,
    ),
    NoShowRecord(
        partyId = 103,
        departure = "서울대입구역",
        destination = "사당역",
        meetingAt = "2026-10-28T12:30:00Z",
        noShowAt = "2026-10-28T12:31:00Z",
        isVoided = false,
        objectionStatus = ObjectionStatus.REJECTED,
    ),
    // 이의가 인정돼 무효가 된 줄(서버가 보통 함께 주는 조합)
    NoShowRecord(
        partyId = 104,
        departure = "해운대역",
        destination = "센텀시티",
        meetingAt = "2026-10-20T22:10:00Z",
        noShowAt = "2026-10-20T22:12:00Z",
        isVoided = true,
        objectionStatus = ObjectionStatus.ACCEPTED,
    ),
    // 이의 없이 운영자가 무효로 돌린 줄 — **버튼이 생기면 409 가 되는** 조합이다
    NoShowRecord(
        partyId = 105,
        departure = "동래역",
        destination = "온천장역",
        meetingAt = "2026-10-11T01:00:00Z",
        noShowAt = "2026-10-11T01:05:00Z",
        isVoided = true,
        objectionStatus = null,
    ),
)

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "다섯 상태")
@Composable
private fun NoShowRecordsScreenPreview() {
    NoShowRecordsScreen(records = PreviewRecords)
}

// 노쇼가 0건인 사용자 — 제재 계산식도 경고도 없고 「없어요」 한 줄이다
@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "기록 없음")
@Composable
private fun NoShowRecordsScreenEmptyPreview() {
    NoShowRecordsScreen()
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "조회 실패")
@Composable
private fun NoShowRecordsScreenFailedPreview() {
    NoShowRecordsScreen(failed = true)
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "이의 보낸 직후")
@Composable
private fun NoShowRecordsScreenSentPreview() {
    NoShowRecordsScreen(
        records = PreviewRecords.map {
            if (it.partyId == 101L) it.copy(objectionStatus = ObjectionStatus.PENDING) else it
        },
        objectionSent = true,
    )
}

// 빈 사유 — 카운터가 「0/500자」이고 보내기가 잠겨 있어야 한다
@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "시트 · 빈 사유")
@Composable
private fun ObjectionSheetEmptyPreview() {
    NoShowRecordsScreen(records = PreviewRecords, objectionTarget = 101L)
}

// 500자를 넘긴 사유 — 카운터가 몇 자를 줄여야 하는지 말하고 보내기가 잠긴다
@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "시트 · 한도 초과")
@Composable
private fun ObjectionSheetOverLimitPreview() {
    NoShowRecordsScreen(
        records = PreviewRecords,
        objectionTarget = 101L,
        reason = "가".repeat(OBJECTION_REASON_MAX + 17),
    )
}

// 409 — 보내기가 잠기고 왼쪽 버튼이 「닫기」로 바뀐다(다시 눌러도 같은 답이다)
@Preview(showBackground = true, widthDp = 393, heightDp = 852, name = "시트 · 이미 냈다")
@Composable
private fun ObjectionSheetFailedPreview() {
    NoShowRecordsScreen(
        records = PreviewRecords,
        objectionTarget = 101L,
        reason = "그날 약속 장소에 10분 전부터 있었어요",
        objectionFailure = NoShowObjectionFailure.ALREADY_OBJECTED,
    )
}
