package com.moyeota.data.remote.dto

import kotlinx.serialization.Serializable

// 백엔드 faresplit 모듈의 요청·응답 record 와 필드 1:1 대응
// (`faresplit/dto/FareSplitRequest.java`·`FareSplitDetailResponse.java`·`MyFareSplitListResponse.java`,
//  2026-11-12 기준 소스 대조. **실서버 미검증** — 로컬·배포 서버에 요청을 보내지 않았다).
//
// 서버 record 필드가 전부 박싱 타입(Long/Integer)이거나 enum 이라 null 이 올 수 있는 자리는 방어한다.
// 다만 **「모른다」와 「0」이 다른 뜻인 자리는 기본값 대신 nullable** 로 받는다 — 이 모듈에서는
// partyId·totalFare·myAmount·amount 넷이 그렇다(아래 각 필드 KDoc). kotlinx 는 **키 누락만** 기본값으로
// 떨어뜨리고 **명시적 null 에는 예외를 던지므로**, non-null 기본값은 「조용히 0으로 접기」와
// 「응답 전체 파싱 실패」 두 실패 모드를 동시에 들여온다.
//
// enum 자리(status·myRole·transfers[].status)도 nullable 로 받는다. 서버가 null 을 보낼 계약은
// 아니지만, 그 하나가 **정산 화면을 통째로 못 열게** 만들 이유가 없다 — 매퍼에 else 분기가 이미 있다
// (`FareSplitMappers`).

/**
 * 정산 시작(POST)과 수정(PATCH)이 **함께 쓰는** 요청 본문 — 서버도 `FareSplitRequest` 하나다.
 *
 * 수정은 일부 필드만 보내는 PATCH 가 아니다 — **네 필드 전부 필수**이고 규칙도 시작과 같다
 * (서버 `@NotNull`/`@NotBlank`/`@NotEmpty`). 하나라도 빠지면 400 `INVALID_REQUEST` 다.
 *
 * 전부 non-null 인 이유: **보내는 쪽**이라 앱이 값을 안다. 받는 쪽의 방어 논증은 이 자리에 해당하지
 * 않는다 — 여기서 nullable 을 쓰면 「빠뜨린 필드」가 컴파일을 통과한다.
 */
@Serializable
data class FareSplitRequestDto(
    /** 실제로 낸 택시비(원). 서버 `@Positive` — 0 이하면 400. */
    val totalFare: Int,
    /**
     * 받을 계좌의 은행 **코드**(`KAKAO`). 서버가 `Bank` enum 으로 역직렬화하므로
     * **표시 이름(「카카오뱅크」)을 보내면 400** `INVALID_REQUEST` 다.
     * 값의 출처는 [com.moyeota.domain.model.Bank.code] 이거나, 기존 정산을 고칠 때는
     * 서버가 준 `bank.code` 를 그대로 되돌린 것이다.
     */
    val bankCode: String,
    /**
     * 받을 계좌번호. **하이픈·공백을 뗀 숫자 10~16자리**여야 하고 어기면 400 `INVALID_ACCOUNT_NUMBER`.
     *
     * 서버도 `AccountNumber.of` 로 한 번 더 떼지만, 앱이 떼어 보내는 이유는 **보내기 전에 판정할 수
     * 있어야** 하기 때문이다 — 정규화는 매퍼가 하고 규칙은
     * [com.moyeota.domain.model.FareSplitPolicy.normalizeAccountNumber] 하나에만 있다.
     */
    val accountNumber: String,
    /**
     * 탑승자마다 부담 금액. **받는 사람(방장)을 포함한 탑승자 전원이 정확히 한 번씩** 있어야 하고
     * 합이 [totalFare] 와 같아야 한다 — 어긋나면 400 `PAYMENT_TARGET_MISMATCH` /
     * `PAYMENTS_NOT_EQUAL_FARE`. 1/N 로 미리 채우는 것은 **앱의 책임**이다(서버 Swagger 원문).
     */
    val payments: List<PaymentDto>,
) {
    @Serializable
    data class PaymentDto(
        /** 탑승자의 publicId(UUID 문자열). 서버가 이것으로 내부 id 를 찾는다 — 못 찾으면 `PAYMENT_TARGET_MISMATCH`. */
        val userPublicId: String,
        /** 이 사람의 부담 금액(원). 1원 이상(서버 `@Positive` + `FarePayment` 불변식). */
        val amount: Int,
    )
}

/**
 * 정산에 등장하는 사람 — 서버 `FareSplitDetailResponse.FareSplitPayer`.
 *
 * 두 응답이 **같은 record 를 공유**하므로(상세의 `payer`, 내 정산 목록의 `payer`) DTO 도 하나만 둔다.
 *
 * 두 필드가 **함께** null 이 될 수 있다 — 서버가 `MemberSummary` 를 못 찾으면
 * `payer == null ? null : payer.publicId()` 로 둘을 같이 비운다. 하나만 null 인 경우는 서버가 만들지 않는다.
 */
@Serializable
data class FareSplitPayerDto(
    val publicId: String? = null,
    val nickname: String? = null,
)

/**
 * 정산 상세 — **시작(201)·조회(200)·수정(200) 세 응답이 모두 이 모양**이다.
 *
 * 보는 사람에 따라 내용이 다르다(서버가 `viewerId` 로 만든다):
 *
 * | 필드 | 누구에게 실리나 |
 * |---|---|
 * | [myRole]·[myAmount] | 토큰의 사용자 기준 — 같은 방의 두 사람이 **다른 값**을 받는다 |
 * | [accountNumber] | `OPEN` 일 때만. 닫히면 서버가 **지운다** |
 * | [tossDeeplink] | `SENDER` + `OPEN` 일 때만 |
 * | [TransferItemDto.maskedRealName] | `PAYER` 가 볼 때만 |
 *
 * **이 넷을 non-null 로 받으면 파싱이 깨진다.** 서버는 그 자리에 `null` 을 **명시적으로** 싣고
 * (`@Schema(nullable = true)` — 키가 빠지는 것이 아니다), kotlinx 는 non-null 필드의 명시적 null 을
 * 기본값으로 떨어뜨리지 않는다. 즉 「보내는 사람이 상세를 열면 늘 실패한다」처럼, **역할에 따라
 * 통째로 안 열리는** 실패가 된다.
 */
@Serializable
data class FareSplitDetailResponse(
    /**
     * 방 id. **`Long = 0` 으로 두면 안 된다.**
     *
     * 이 값은 이후 모든 호출의 **경로 변수**다(받았어요·보냈어요·수정이 전부 `rooms/{partyId}` 아래다).
     * 0 으로 접히면 요청이 실패하는 게 아니라 **존재하지 않는 방으로 나간다** — 404 를 받고 나서야
     * 알게 되고, 그 404 는 「정산이 없다」로 읽혀 화면이 「정산 시작하기」를 띄운다(원인이 완전히 가려진다).
     *
     * 서버 record 는 `Long partyId` 이고 값은 PK 라 운영에서는 늘 실린다. 이 nullable 은 그 계약을
     * 의심하는 게 아니라, 말해 주지 않았을 때 **앱이 요청에 쓴 partyId 로 메울 수 있게** 하는 것이다
     * (`FareSplitMappers.toFareSplitDetail(partyId)` — 앱은 어느 방을 물었는지 알고 있다).
     */
    val partyId: Long? = null,
    /**
     * `OPEN`·`COMPLETED`·`EXPIRED`. 모르는 값(과 null)은 매퍼가 `OPEN` 으로 떨어뜨린다 —
     * 근거는 `FareSplitMappers.toFareSplitStatus`.
     */
    val status: String? = null,
    /**
     * 실제로 낸 택시비(원). **`Int = 0` 으로 두면 안 된다** — 0원짜리 정산은 서버가 만들 수 없는 값이고
     * (`@Positive`), 0 으로 접히면 화면이 「0원을 나눠 냈다」는 **거짓**을 말한다. null 이면
     * 매퍼가 0 으로 옮기되 그건 「서버가 말해 주지 않았다」는 뜻이다(화면은 금액 영역을 비워야 한다).
     */
    val totalFare: Int? = null,
    /** 택시비를 다 내고 받는 사람(= 방장). 서버가 요약을 못 찾으면 안쪽 두 필드가 함께 null 이다. */
    val payer: FareSplitPayerDto? = null,
    /**
     * `PAYER`·`SENDER` — **토큰 기준**이다. 모르는 값(과 null)은 매퍼가 `SENDER` 로 떨어뜨린다 —
     * 근거는 `FareSplitMappers.toFareSplitRole`.
     */
    val myRole: String? = null,
    /**
     * 내 부담 금액(원). **역할에 따라 뜻이 다르다** — `SENDER` 면 내가 **보낼** 금액,
     * `PAYER` 면 내가 **부담한** 몫이다.
     *
     * `Int = 0` 금지 이유는 [totalFare] 와 같되 더 급하다: 보내는 사람 화면에서 0 으로 접히면
     * 「0원 보내기」가 떠서 **사용자가 아무것도 보내지 않고 끝낸다.** 서버가 토스 딥링크에도 같은 값을
     * 넣으므로([tossDeeplink]) 둘이 어긋나면 화면과 딥링크 금액이 달라진다.
     */
    val myAmount: Int? = null,
    /** 받을 계좌의 은행 — 코드와 **보여 줄 이름**을 함께 준다([BankDto]). */
    /**
     * 내 송금 상태 — `NOT_SENT`·`SENT`·`CONFIRMED`. **SENDER 일 때만 있고 PAYER 는 null** 이다
     * (받는 사람은 보낼 것이 없다). Backend PR #328 이 더했다.
     *
     * 그 전에는 이 값이 없어서 보내는 사람이 `transfers` 에서 **자기 publicId 로 자기 행을 찾아야**
     * 했다. 세션 uuid 가 열쇠인 방식이라, 서버가 그 사람을 설명하지 못한 응답
     * (`transfers[].publicId == null`)에서는 **내가 보냈는지 알 수 없었다.** 이제 직접 온다.
     *
     * ⚠️ **null 을 `NOT_SENT` 로 접지 마라.** 받는 사람의 null 을 「아직 안 보냈다」로 읽으면
     * 방장 화면에 「보냈어요」 버튼이 뜨고, 누르면 403 `NOT_FARE_SPLIT_SENDER` 다.
     * 모르는 문자열도 null 로 둔다 — 매퍼가 그렇게 떨어뜨린다(`toTransferStatusOrNull`).
     */
    val myTransferStatus: String? = null,
    val bank: BankDto? = null,
    /**
     * 받을 계좌번호(숫자만). **없을 수 있고, 그건 오류가 아니다.** 비는 경우가 **둘**이다:
     *
     * | 왜 비나 | 상태 | 화면이 할 말 |
     * |---|---|---|
     * | 정산이 닫혔다(COMPLETED·EXPIRED) — `FareSplit.close` 가 `accountNumber = null` | `OPEN` 이 아니다 | 「끝난 정산」 |
     * | **받는 사람이 탈퇴했다** — `FareSplitUserDeletionListener` 가 `forgetPayerAccounts` 로 바로 지운다 | **`OPEN` 그대로** | 「보낼 계좌가 사라졌다」 |
     *
     * 즉 **「OPEN 이면 계좌번호가 있다」는 참이 아니다.** 그 둘을 「닫혔다」 하나로 읽으면, 탈퇴한
     * 방장에게 보낼 돈이 남은 사람이 **진행 중인 정산을 「끝났다」로 보고** 자기 몫을 영영 보내지
     * 않는다(서버는 그 송금을 계속 NOT_SENT 로 세고 4시간마다 재요청 알림을 보낸다).
     * 상태와 계좌번호를 **따로** 봐야 한다.
     */
    val accountNumber: String? = null,
    /**
     * 토스 송금 딥링크(`supertoss://send?amount=&bank=&accountNo=`). **`SENDER` + `OPEN` + 계좌번호가
     * 남아 있을 때만** 있다 — 서버가 [accountNumber] 에서 만들므로([accountNumber] 표의 둘째 줄처럼)
     * 받는 사람이 탈퇴하면 OPEN 인데도 함께 null 이 된다.
     *
     * **앱이 조립하지 마라.** 서버가 `TossDeeplink.of` 로 만들고, 은행 이름도 토스 전용 표기
     * (`Bank.tossName`: 「KB국민」)라 [BankDto.name](「KB국민은행」)과 **다르다.** 앱이 베끼면 두 표기가
     * 갈리고, 틀린 쪽은 토스가 조용히 열리지 않는 것으로만 드러난다.
     *
     * 서버 주석 원문: 「비공식 규격이라 열리지 않을 수 있어 계좌·금액 복사 버튼을 함께 둔다」 —
     * 그래서 도메인 모델이 같은 사실을 복사용 평문으로 한 번 더 들고 있다
     * ([com.moyeota.domain.model.FareSplitDetail.manualTransfer]).
     */
    val tossDeeplink: String? = null,
    /**
     * 보내는 사람별 송금, **방 합류 순**(서버가 `joinOrder` 로 정렬한다). 앱이 다시 정렬하지 않는다.
     * **받는 사람의 행은 빠져 있다**(`senderTransfers()`) — 목록 크기는 탑승자 수 - 1 이다.
     */
    val transfers: List<TransferItemDto> = emptyList(),
    /** 정산을 시작한 시각(ISO-8601). 서버 `Instant` — 채팅 `createdAt` 과 같은 관례로 **문자열 그대로** 받는다. */
    val createdAt: String? = null,
    /** `COMPLETED`·`EXPIRED` 가 된 시각(ISO-8601). `OPEN` 이면 null 이다. */
    val closedAt: String? = null,
) {
    /**
     * 은행 코드 + **보여 줄 이름** — 서버 `FareSplitDetailResponse.FareSplitBank`.
     *
     * 이름을 서버가 함께 주는 것이 핵심이다. 덕분에 서버가 23번째 은행을 추가해도 앱은 그 정산을
     * **정상으로 그릴 수 있다**(앱의 `Bank` enum 에 없어도 된다) —
     * 자세한 비대칭 논증은 [com.moyeota.domain.model.Bank] KDoc 의 표에 있다.
     */
    @Serializable
    data class BankDto(
        /** `Bank` enum 이름(`KAKAO`). 정산을 고칠 때 **이 값을 그대로 되돌려 보낸다.** */
        val code: String? = null,
        /** 사람이 읽을 이름(「카카오뱅크」). **토스 표기(`KB국민`)와는 다른 값**이다. */
        val name: String? = null,
    )

    /** 보내는 사람 한 명의 송금 — 서버 `FareSplitDetailResponse.FareSplitTransferItem`. */
    @Serializable
    data class TransferItemDto(
        /**
         * 보내는 사람의 publicId — 「받았어요」·「못 받았어요」의 **경로 변수**다.
         *
         * 서버가 유저 요약을 못 찾으면 [nickname] 과 **함께** null 이다. 그 행은 확인할 수 없고
         * (부를 id 가 없다), 그 한 행 때문에 정산이 COMPLETED 로 닫히지 못한다 — 화면이 버튼을
         * 잠가야 한다([com.moyeota.domain.model.FareSplitTransfer.isConfirmable]).
         */
        val publicId: String? = null,
        val nickname: String? = null,
        /**
         * 이 사람이 보낼 금액(원). **`Int = 0` 금지** — 0 으로 접히면 받는 사람 화면이 「0원을 받을
         * 사람」을 보여 주고, 합이 택시비와 맞지 않는 장부가 된다(사용자는 서버가 틀렸다고 읽는다).
         */
        val amount: Int? = null,
        /** `NOT_SENT`·`SENT`·`CONFIRMED`. 모르는 값은 매퍼가 `NOT_SENT` 로 떨어뜨린다. */
        val status: String? = null,
        /** 「보냈어요」를 누른 시각(ISO-8601). 아직 안 눌렀으면 null. */
        val sentAt: String? = null,
        /** 받는 사람이 확인한 시각(ISO-8601). 확인 전이면 null. */
        val confirmedAt: String? = null,
        /**
         * 가린 실명(「홍*동」) — **받는 사람(`PAYER`)이 볼 때만** 있다. 보내는 사람에게는 **늘 null**.
         * 쓰임은 은행 앱 입금자명 대조 하나다. null 을 「실명이 없는 사용자」로 읽지 말 것.
         */
        val maskedRealName: String? = null,
    )
}

/**
 * 내가 보낼 정산 목록 — 홈 배너(「아직 보내지 않은 택시비」)의 입력.
 * 서버 `MyFareSplitListResponse`. 없으면 `{"fareSplits":[]}` 다.
 *
 * **내가 보내는 사람인 OPEN 정산만** 온다 — 받는 사람으로 시작한 정산과 닫힌 정산은 나오지 않는다.
 */
@Serializable
data class MyFareSplitListResponse(
    val fareSplits: List<ItemDto> = emptyList(),
) {
    @Serializable
    data class ItemDto(
        /**
         * 방 id — **이 줄을 눌러 상세로 가는 유일한 열쇠**다. 그래서 `Long = 0` 이 아니라 nullable 이고,
         * 없는 줄은 **매퍼가 버린다**(열 수 없는 줄을 목록에 남기면 눌러도 아무 일이 없거나 404 다).
         * 같은 판단: `FavoritePlaceListResponse` 의 placeId 없는 행.
         */
        val partyId: Long? = null,
        /**
         * 출발지 이름. 서버가 방을 못 찾으면 [destination] 과 **함께** null 이다
         * (`places == null ? null : …` — 한쪽만 null 인 경우는 서버가 만들지 않는다).
         * 정산은 방보다 오래 남을 수 있어 실제로 일어난다.
         */
        val departure: String? = null,
        /** 도착지 이름. [departure] 와 함께 null 이 된다. */
        val destination: String? = null,
        /** 받는 사람(= 그 방 방장). */
        val payer: FareSplitPayerDto? = null,
        /** 내가 보낼 금액(원). `Int = 0` 금지 이유는 [FareSplitDetailResponse.myAmount] 와 같다. */
        val myAmount: Int? = null,
        /**
         * 내 송금 상태. **조회에 쓴 status 와 같은 값**이 돌아온다(서버가 그 값으로 걸러 준다) —
         * 그래도 응답 값을 쓰는 이유는 요청 쪽 상수를 화면이 두 번 적지 않게 하는 것이다.
         */
        val status: String? = null,
        /** 정산을 시작한 시각(ISO-8601). 목록은 **이 값 최신순**으로 서버가 정렬해 준다. */
        val createdAt: String? = null,
    )
}
