package com.moyeota.data.remote

import com.moyeota.data.remote.dto.FareSplitDetailResponse
import com.moyeota.data.remote.dto.FareSplitRequestDto
import com.moyeota.data.remote.dto.MyFareSplitListResponse
import com.moyeota.domain.model.Bank
import com.moyeota.domain.model.FareSplitDraft
import com.moyeota.domain.model.FareSplitPolicy
import com.moyeota.domain.model.FareSplitRole
import com.moyeota.domain.model.FareSplitStatus
import com.moyeota.domain.model.FareShare
import com.moyeota.domain.model.TransferStatus
import com.moyeota.domain.model.WithdrawnMemberNames
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 정산 DTO → 도메인 변환을 **서버가 실제로 보낼 JSON 문자열에서** 시작해 못 박는다
 * (컨트롤러·record 소스 대조, 2026-11-12. **실서버 미검증** — `backend/http/` 에 정산 요청 예시가 없다).
 *
 * 여기서 잡으려는 실패는 둘이다:
 * 1. **명시적 null 이 파싱을 깨는 것.** 이 응답은 역할·상태에 따라 `accountNumber`·`tossDeeplink`·
 *    `maskedRealName` 을 **null 로 싣는다**(키가 빠지는 것이 아니다). non-null 로 선언하면
 *    kotlinx 가 기본값으로 떨어뜨리지 않고 예외를 던져 **역할에 따라 상세가 통째로 안 열린다.**
 * 2. **모르는 값을 틀린 쪽으로 떨어뜨리는 것.** 상태·역할·송금 상태·은행 네 자리 모두 「모르면
 *    무엇으로 읽나」가 **되돌릴 수 있는 쪽 / 없는 쪽**으로 갈린다(각 매퍼 KDoc 의 표).
 */
class FareSplitMappersTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val payerId = "11111111-1111-1111-1111-111111111111"
    private val senderAId = "22222222-2222-2222-2222-222222222222"
    private val senderBId = "33333333-3333-3333-3333-333333333333"

    /** 받는 사람(PAYER)이 보는 상세 — 가린 실명이 있고 토스 딥링크가 **없다**. */
    private val payerViewJson = """
        {
          "partyId": 42,
          "status": "OPEN",
          "totalFare": 18700,
          "payer": { "publicId": "$payerId", "nickname": "방장" },
          "myRole": "PAYER",
          "myAmount": 6234,
          "myTransferStatus": null,
          "bank": { "code": "KAKAO", "name": "카카오뱅크" },
          "accountNumber": "3333012345678",
          "tossDeeplink": null,
          "transfers": [
            { "publicId": "$senderAId", "nickname": "지민", "amount": 6233, "status": "SENT",
              "sentAt": "2026-11-12T01:00:00Z", "confirmedAt": null, "maskedRealName": "홍*동" },
            { "publicId": "$senderBId", "nickname": "수호", "amount": 6233, "status": "NOT_SENT",
              "sentAt": null, "confirmedAt": null, "maskedRealName": "김*수" }
          ],
          "createdAt": "2026-11-12T00:00:00Z",
          "closedAt": null
        }
    """.trimIndent()

    private fun detail(raw: String, partyId: Long = 42) =
        json.decodeFromString<FareSplitDetailResponse>(raw).toFareSplitDetail(partyId)

    @Test
    fun `받는 사람 상세를 도메인으로 옮긴다`() {
        val detail = detail(payerViewJson)

        assertEquals(42L, detail.partyId)
        assertEquals(FareSplitStatus.OPEN, detail.status)
        assertEquals(18_700, detail.totalFare)
        assertEquals(FareSplitRole.PAYER, detail.myRole)
        assertEquals(6_234, detail.myAmount)
        assertEquals("방장", detail.payer.nickname)
        assertEquals(payerId, detail.payer.publicId)
        assertEquals("3333012345678", detail.accountNumber)
        assertNull("받는 사람에게 토스 딥링크가 실렸다", detail.tossDeeplink)
        // 합계가 이 응답의 불변식이다 — 내 몫 + 보내는 사람들의 몫 = 택시비
        assertEquals(detail.totalFare, detail.myAmount + detail.transfers.sumOf { it.amount })

        val first = detail.transfers.first()
        assertEquals(senderAId, first.senderPublicId)
        assertEquals("지민", first.nickname)
        assertEquals(TransferStatus.SENT, first.status)
        assertEquals("2026-11-12T01:00:00Z", first.sentAt)
        assertNull(first.confirmedAt)
        assertEquals("홍*동", first.maskedRealName)
        assertTrue(first.isConfirmable)
    }

    /**
     * 보내는 사람에게는 **딥링크가 오고 가린 실명이 전부 null** 이다. 셋 다 `nullable` 이어야 하는데
     * 어느 하나라도 non-null 로 선언하면 **보내는 사람만 상세를 못 여는** 실패가 된다 —
     * 받는 사람(방장)으로 테스트하면 끝까지 드러나지 않는다.
     */
    @Test
    fun `보내는 사람에게는 토스 딥링크가 오고 가린 실명은 오지 않는다`() {
        val raw = """
            {
              "partyId": 42, "status": "OPEN", "totalFare": 18700,
              "payer": { "publicId": "$payerId", "nickname": "방장" },
              "myRole": "SENDER", "myAmount": 6233, "myTransferStatus": "NOT_SENT",
              "bank": { "code": "KAKAO", "name": "카카오뱅크" },
              "accountNumber": "3333012345678",
              "tossDeeplink": "supertoss://send?amount=6233&bank=%EC%B9%B4%EC%B9%B4%EC%98%A4%EB%B1%85%ED%81%AC&accountNo=3333012345678",
              "transfers": [
                { "publicId": "$senderAId", "nickname": "지민", "amount": 6233, "status": "NOT_SENT",
                  "sentAt": null, "confirmedAt": null, "maskedRealName": null }
              ],
              "createdAt": "2026-11-12T00:00:00Z", "closedAt": null
            }
        """.trimIndent()

        val detail = detail(raw)

        assertEquals(FareSplitRole.SENDER, detail.myRole)
        assertTrue(detail.tossDeeplink!!.startsWith("supertoss://send?"))
        assertNull("보내는 사람에게 가린 실명이 실렸다", detail.transfers.single().maskedRealName)

        // 딥링크가 열리지 않는 것을 전제한 복사 거리 — 서버를 다시 부르지 않고 손으로 옮길 수 있어야 한다
        val manual = detail.manualTransfer!!
        assertEquals("3333012345678", manual.accountNumber)
        assertEquals(6_233, manual.amount)
        assertEquals("카카오뱅크", manual.bankDisplayName)
    }

    /**
     * `transferOf` 는 **받는 사람 화면이 보내는 사람별 행을 고르는 자리**다(「받았어요」를 누를 대상).
     *
     * 한동안은 이게 보내는 사람이 **자기 송금 상태**를 찾는 유일한 길이기도 했다 — 응답에
     * `myTransferStatus` 가 없었기 때문이다. Backend PR #328 이 그 값을 직접 주면서 두 용도가 갈렸다.
     */
    @Test
    fun `받는 사람은 보내는 사람별 행을 publicId 로 고른다`() {
        val detail = detail(payerViewJson)

        assertEquals(TransferStatus.SENT, detail.transferOf(senderAId)?.status)
        assertNull("받는 사람의 행은 transfers 에 없다", detail.transferOf(payerId))
        assertNull("빈 publicId 가 아무 행이나 집어 오면 안 된다", detail.transferOf(""))
    }

    /**
     * 못 박는 것: **`myTransferStatus` 가 그대로 올라오고, 받는 사람의 null 은 null 로 남는다.**
     *
     * null 을 [TransferStatus.NOT_SENT] 로 접으면 **방장 화면에 「보냈어요」가 뜬다** — 누르면 403
     * `NOT_FARE_SPLIT_SENDER` 이고, 사용자는 자기 정산에서 왜 거절당하는지 알 수 없다.
     * `transfers[].status` 는 반대로 null 을 NOT_SENT 로 떨어뜨리므로(올 수 없는 값이라) 두 매퍼가
     * 같은 enum 을 **다른 방향으로** 읽는다 — 그 차이가 여기서 깨지지 않는지 함께 본다.
     */
    @Test
    fun `내 송금 상태는 받는 사람에게 null 이고 보내는 사람에게만 값이 있다`() {
        val payerView = detail(payerViewJson)
        assertNull("받는 사람의 myTransferStatus 를 NOT_SENT 로 접었다", payerView.myTransferStatus)
        // 같은 응답의 transfers 는 NOT_SENT 로 떨어진다 — 두 매퍼의 방향이 다르다는 증거
        assertEquals(TransferStatus.NOT_SENT, payerView.transferOf(senderBId)?.status)

        val senderView = detail(payerViewJson
            .replace(""""myRole": "PAYER"""", """"myRole": "SENDER"""")
            .replace(""""myTransferStatus": null""", """"myTransferStatus": "SENT""""))
        assertEquals(TransferStatus.SENT, senderView.myTransferStatus)
    }

    /**
     * 못 박는 것: **서버가 `myTransferStatus` 를 안 보내도 보내는 사람은 자기 상태를 안다.**
     *
     * 그 필드는 Backend PR #328 이 더한 값이고, **그 배포가 닿기 전의 서버는 키 자체를 보내지
     * 않는다.** 폴백이 없으면 `ignoreUnknownKeys` 가 조용한 null 을 만들고, 보내는 사람 화면이
     * 「내 송금 상태를 확인하지 못했어요」로 떨어져 **「보냈어요」가 영영 안 뜬다** — 즉 앱이
     * 지금 떠 있는 서버에서 쓸 수 없다(로컬 실측 2026-11-12, partyId 4146 에서 실제로 그랬다).
     *
     * 폴백은 옛 길을 되살리는 것이다: 그 필드가 생기기 전에는 `transfers` 에서 자기 publicId 로
     * 자기 행을 찾는 것이 유일한 길이었다. 세션 uuid 가 그 열쇠다.
     */
    @Test
    fun `서버가 내 송금 상태를 안 보내면 내 행에서 찾아 메운다`() {
        val legacy = payerViewJson
            .replace(""""myRole": "PAYER"""", """"myRole": "SENDER"""")
            .replace(""""myTransferStatus": null,""", "")   // 옛 서버: 키 자체가 없다

        val withSession = json.decodeFromString<FareSplitDetailResponse>(legacy)
            .toFareSplitDetail(42, currentUserUuid = senderAId)
        assertEquals("내 행(senderA)의 상태를 못 찾았다", TransferStatus.SENT, withSession.myTransferStatus)
        assertFalse("이미 보낸 송금인데 버튼이 열렸다", withSession.canMarkSent)

        // 세션을 모르면 지어내지 않는다 — 화면이 「모른다」로 가고 버튼을 닫는다.
        val noSession = json.decodeFromString<FareSplitDetailResponse>(legacy).toFareSplitDetail(42)
        assertNull(noSession.myTransferStatus)
        assertFalse(noSession.canMarkSent)
    }

    /**
     * 못 박는 것: **받는 사람에게는 폴백하지 않는다.**
     *
     * PAYER 의 null 은 「모른다」가 아니라 **「보낼 것이 없다」**이고, 받는 사람의 행은 애초에
     * `transfers` 에 없다. 그래도 세션 uuid 가 우연히 어떤 행과 맞는 일이 없도록 **역할로 먼저
     * 가른다** — 가리지 않으면 방장에게 「보냈어요」가 떠서 403 `NOT_FARE_SPLIT_SENDER` 가 된다.
     */
    @Test
    fun `받는 사람에게는 내 송금 상태를 메우지 않는다`() {
        val legacy = payerViewJson.replace(""""myTransferStatus": null,""", "")

        // 세션 uuid 를 transfers 의 한 행과 일부러 같게 줘도 PAYER 면 메우지 않는다.
        val detail = json.decodeFromString<FareSplitDetailResponse>(legacy)
            .toFareSplitDetail(42, currentUserUuid = senderAId)

        assertNull("받는 사람의 상태를 송금 행에서 끌어왔다", detail.myTransferStatus)
        assertFalse("받는 사람에게 보냈어요가 떴다", detail.canMarkSent)
    }

    /**
     * 못 박는 것: **모르는 상태 문자열도 null 이다** — 서버가 상태를 늘렸을 때 「아직 안 보냈다」로
     * 단정하지 않는다. 그러면 [FareSplitDetail.canMarkSent] 가 false 가 되어 화면이 버튼을 닫고
     * 상세를 다시 읽는다. 모르는 상태에 버튼을 열어 두는 것보다 낫다.
     */
    @Test
    fun `모르는 내 송금 상태는 null 로 떨어진다`() {
        val detail = detail(payerViewJson
            .replace(""""myRole": "PAYER"""", """"myRole": "SENDER"""")
            .replace(""""myTransferStatus": null""", """"myTransferStatus": "REFUNDED""""))

        assertNull(detail.myTransferStatus)
        assertFalse("모르는 상태인데 보냈어요 버튼이 열렸다", detail.canMarkSent)
    }

    /**
     * 못 박는 것: **「보냈어요」 버튼의 조건이 세 개 다 걸린다** — 진행 중 · 보내는 사람 · 아직 안 보냄.
     *
     * 화면이 조건을 따로 세우면 받는 사람의 null 을 「안 보냈다」로 읽는 실수가 끼어든다.
     * 이미 SENT 인데 열어 두면 사용자가 「안 눌렸나」 싶어 다시 누르고 같은 화면이 반복된다.
     */
    @Test
    fun `보냈어요는 진행 중인 보내는 사람이 아직 안 보냈을 때만 열린다`() {
        fun senderWith(status: String, splitStatus: String = "OPEN") = detail(
            payerViewJson
                .replace(""""myRole": "PAYER"""", """"myRole": "SENDER"""")
                .replace(""""myTransferStatus": null""", """"myTransferStatus": "$status"""")
                .replace(""""status": "OPEN"""", """"status": "$splitStatus""""),
        )

        assertTrue(senderWith("NOT_SENT").canMarkSent)
        assertFalse("보낸 뒤에도 버튼이 열려 있다", senderWith("SENT").canMarkSent)
        assertFalse(senderWith("CONFIRMED").canMarkSent)
        assertFalse("닫힌 정산에 버튼이 열려 있다", senderWith("NOT_SENT", splitStatus = "COMPLETED").canMarkSent)
        assertFalse("받는 사람에게 보냈어요가 떴다", detail(payerViewJson).canMarkSent)
    }

    /** 닫힌 정산은 서버가 계좌번호를 **지워서** 보낸다 — null 은 오류가 아니라 「끝났다」는 사실이다. */
    @Test
    fun `닫힌 정산은 계좌번호 없이 온다`() {
        val raw = """
            {
              "partyId": 42, "status": "COMPLETED", "totalFare": 18700,
              "payer": { "publicId": "$payerId", "nickname": "방장" },
              "myRole": "PAYER", "myAmount": 6234,
              "bank": { "code": "KAKAO", "name": "카카오뱅크" },
              "accountNumber": null, "tossDeeplink": null,
              "transfers": [
                { "publicId": "$senderAId", "nickname": "지민", "amount": 6233, "status": "CONFIRMED",
                  "sentAt": "2026-11-12T01:00:00Z", "confirmedAt": "2026-11-12T02:00:00Z", "maskedRealName": "홍*동" }
              ],
              "createdAt": "2026-11-12T00:00:00Z", "closedAt": "2026-11-12T02:00:00Z"
            }
        """.trimIndent()

        val detail = detail(raw)

        assertEquals(FareSplitStatus.COMPLETED, detail.status)
        assertFalse(detail.isOpen)
        assertNull(detail.accountNumber)
        assertNull("닫힌 정산에 복사 거리가 만들어졌다", detail.manualTransfer)
        assertFalse("닫힌 정산은 계좌를 잃은 것이 아니다", detail.hasLostAccount)
        assertEquals("2026-11-12T02:00:00Z", detail.closedAt)
        assertFalse(detail.canEdit)
    }

    /**
     * **「OPEN 이면 계좌번호가 있다」는 참이 아니다.** 받는 사람이 탈퇴하면 서버가 열린 정산의
     * 계좌번호만 즉시 지운다(`FareSplitUserDeletionListener` → `forgetPayerAccounts`) — 상태는
     * OPEN 그대로다. 「계좌가 없으면 끝난 정산」으로 읽으면 **아직 보낼 돈이 남은 사람에게
     * 「정산이 끝났어요」**가 뜨고, 서버는 그 송금을 계속 NOT_SENT 로 세며 4시간마다 재요청한다.
     */
    @Test
    fun `받는 사람이 탈퇴하면 진행 중인데 계좌번호가 없다`() {
        val raw = """
            {
              "partyId": 42, "status": "OPEN", "totalFare": 18700,
              "payer": { "publicId": null, "nickname": null },
              "myRole": "SENDER", "myAmount": 6233,
              "bank": { "code": "KAKAO", "name": "카카오뱅크" },
              "accountNumber": null, "tossDeeplink": null,
              "transfers": [
                { "publicId": "$senderAId", "nickname": "지민", "amount": 6233, "status": "NOT_SENT",
                  "sentAt": null, "confirmedAt": null, "maskedRealName": null }
              ],
              "createdAt": "2026-11-12T00:00:00Z", "closedAt": null
            }
        """.trimIndent()

        val detail = detail(raw)

        assertTrue(detail.isOpen)
        assertNull(detail.accountNumber)
        assertTrue("진행 중인데 계좌가 사라진 상태를 화면이 구분할 수 없다", detail.hasLostAccount)
        // 유저 요약이 없는 받는 사람 — 이름 자리는 PartyMappers 와 같은 상수를 쓴다
        assertEquals(WithdrawnMemberNames.NO_SUMMARY, detail.payer.nickname)
        assertFalse(detail.payer.isIdentified)
    }

    /**
     * `partyId` 가 없으면 **요청에 쓴 방 번호**로 메운다. 0 으로 접히면 이후 「받았어요」가
     * 존재하지 않는 방으로 나가고, 돌아온 404 는 「정산이 없다」로 읽혀 화면이 「정산 시작하기」를
     * 띄운다 — 원인이 완전히 가려지는 실패다.
     */
    @Test
    fun `partyId 가 없으면 요청에 쓴 방 번호로 메운다`() {
        val raw = """
            {
              "status": "OPEN", "totalFare": 18700,
              "payer": { "publicId": "$payerId", "nickname": "방장" },
              "myRole": "PAYER", "myAmount": 18700,
              "bank": { "code": "KAKAO", "name": "카카오뱅크" },
              "accountNumber": "3333012345678", "tossDeeplink": null,
              "transfers": [], "createdAt": null, "closedAt": null
            }
        """.trimIndent()

        assertEquals(77L, detail(raw, partyId = 77).partyId)
    }

    /**
     * **모르는 값은 「되돌릴 수 있는 쪽」으로 떨어진다** — 상태는 OPEN(눌러도 서버가 409 로 막는다),
     * 역할은 SENDER(「보냈어요」는 멱등이고 403 으로 막힌다), 송금은 NOT_SENT(「받았어요」가 한 번 더
     * 떠도 멱등 204 다). 반대로 기울이면 **받지 않은 돈을 받은 것으로 닫거나**, 보내야 할 사람에게
     * 「끝났어요」를 보여 주는 복구 불가 실패가 된다(각 매퍼 KDoc 의 표).
     */
    @Test
    fun `모르는 상태 역할 송금상태는 되돌릴 수 있는 쪽으로 떨어진다`() {
        assertEquals(FareSplitStatus.OPEN, toFareSplitStatus("SOMETHING_NEW"))
        assertEquals(FareSplitStatus.OPEN, toFareSplitStatus(null))
        assertEquals(FareSplitStatus.COMPLETED, toFareSplitStatus("COMPLETED"))
        assertEquals(FareSplitStatus.EXPIRED, toFareSplitStatus("EXPIRED"))

        assertEquals(FareSplitRole.SENDER, toFareSplitRole("HOST"))
        assertEquals(FareSplitRole.SENDER, toFareSplitRole(null))
        assertEquals(FareSplitRole.PAYER, toFareSplitRole("PAYER"))

        assertEquals(TransferStatus.NOT_SENT, toTransferStatus("REFUNDED"))
        assertEquals(TransferStatus.NOT_SENT, toTransferStatus(null))
        assertEquals(TransferStatus.SENT, toTransferStatus("SENT"))
        assertEquals(TransferStatus.CONFIRMED, toTransferStatus("CONFIRMED"))
    }

    /**
     * **모르는 은행 코드로 상세가 깨지지 않는다.** 서버가 23번째 은행을 추가해도 이름을 함께
     * 보내므로 화면은 정확히 그릴 수 있다 — enum 에 없는 코드를 거절하면 그 방의 정산이 앱 업데이트
     * 전까지 **영구히 안 열리고**, 아는 은행으로 떨어뜨리면 **틀린 은행 이름**으로 송금하게 된다.
     */
    @Test
    fun `모르는 은행 코드도 서버가 준 이름으로 그린다`() {
        val raw = payerViewJson.replace(
            """"bank": { "code": "KAKAO", "name": "카카오뱅크" }""",
            """"bank": { "code": "NEW_BANK_2027", "name": "새로생긴은행" }""",
        )

        val bank = detail(raw).bank

        assertEquals("NEW_BANK_2027", bank.code)
        assertEquals("새로생긴은행", bank.displayName)
        assertNull("모르는 코드가 아는 은행으로 떨어졌다 — 틀린 은행 이름으로 송금하게 된다", bank.known)
    }

    /**
     * 이름의 우선순위는 **서버 → 아는 은행 → 코드**다. 서버를 1순위에 두는 이유: 은행이 이름을
     * 바꾸면(「대구은행」→「iM뱅크」) 앱 상수는 낡고 서버 값은 배포로 갱신된다.
     */
    @Test
    fun `은행 이름이 비면 아는 은행의 이름으로 떨어지고 그마저 없으면 코드를 보여 준다`() {
        val known = detail(
            payerViewJson.replace(
                """"bank": { "code": "KAKAO", "name": "카카오뱅크" }""",
                """"bank": { "code": "KAKAO", "name": null }""",
            ),
        ).bank
        assertEquals(Bank.KAKAO, known.known)
        assertEquals("카카오뱅크", known.displayName)

        val unknown = detail(
            payerViewJson.replace(
                """"bank": { "code": "KAKAO", "name": "카카오뱅크" }""",
                """"bank": { "code": "MYSTERY", "name": null }""",
            ),
        ).bank
        assertEquals("빈칸보다 코드라도 보여 주는 쪽이 낫다", "MYSTERY", unknown.displayName)
    }

    /**
     * 유저 요약이 없는 송금은 **확인할 수 없는 행**이다 — 부를 publicId 가 없다. 화면이 버튼을
     * 잠그지 않으면 「받았어요」가 영구히 404 이고, **그 한 행 때문에 정산이 닫히지 않는다**
     * (서버는 전원 CONFIRMED 를 본다).
     */
    @Test
    fun `유저 요약이 없는 송금은 확인할 수 없는 행으로 남는다`() {
        val raw = payerViewJson.replace(
            """"publicId": "$senderBId", "nickname": "수호"""",
            """"publicId": null, "nickname": null""",
        )

        val broken = detail(raw).transfers.last()

        assertEquals("", broken.senderPublicId)
        assertFalse("부를 id 가 없는 행에 확인 버튼이 열렸다", broken.isConfirmable)
        assertEquals(WithdrawnMemberNames.NO_SUMMARY, broken.nickname)
        assertEquals("금액은 그대로 남아야 장부의 합이 맞는다", 6_233, broken.amount)
    }

    /**
     * `canEdit` 는 서버 `FareSplit.validateNotStarted` 와 **같은 조건**이다. 느슨하면 금액을 다 고친
     * 뒤 409 `FARE_SPLIT_ALREADY_STARTED` 로 입력이 버려지고, 빡빡하면 고칠 수 있는 정산에
     * 수정 버튼이 뜨지 않는다.
     */
    @Test
    fun `전원이 아직 안 보낸 열린 정산만 받는 사람이 고칠 수 있다`() {
        val allNotSent = payerViewJson.replace(""""status": "SENT"""", """"status": "NOT_SENT"""")
        assertTrue(detail(allNotSent).canEdit)

        // 한 명이라도 보냈다고 눌렀으면 못 고친다
        assertFalse(detail(payerViewJson).canEdit)

        // 보내는 사람은 전원 NOT_SENT 여도 못 고친다(403 NOT_FARE_SPLIT_PAYER)
        assertFalse(detail(allNotSent.replace(""""myRole": "PAYER"""", """"myRole": "SENDER"""")).canEdit)
    }

    // ─── 내가 보낼 정산 목록(홈 배너) ───

    @Test
    fun `내 정산 목록을 도메인으로 옮긴다`() {
        val raw = """
            {
              "fareSplits": [
                { "partyId": 42, "departure": "서울시청", "destination": "강남역",
                  "payer": { "publicId": "$payerId", "nickname": "방장" },
                  "myAmount": 6233, "status": "NOT_SENT", "createdAt": "2026-11-12T00:00:00Z" }
              ]
            }
        """.trimIndent()

        val item = json.decodeFromString<MyFareSplitListResponse>(raw).toMyFareSplits().single()

        assertEquals(42L, item.partyId)
        assertEquals("서울시청", item.departure)
        assertEquals("강남역", item.destination)
        assertEquals(6_233, item.myAmount)
        assertEquals(TransferStatus.NOT_SENT, item.status)
        assertEquals("방장", item.payer.nickname)
    }

    /**
     * `partyId` 가 그 줄을 **눌러 상세로 가는 유일한 열쇠**라, 없는 줄은 버린다. 남겨 두면 눌러도
     * 아무 일이 없거나(0번 방) 404 다. 한 줄 때문에 배너 전체를 날리지 않는 것은 즐겨찾기와 같은 관례.
     *
     * 방이 지워진 줄(`departure`·`destination` 이 함께 null)은 **버리지 않는다** — 보낼 돈은 그대로
     * 남아 있고 상세로 들어갈 수도 있다. 장소 이름이 없는 것은 화면이 메울 일이다.
     */
    @Test
    fun `내 정산 목록에서 partyId 없는 줄만 버린다`() {
        val raw = """
            {
              "fareSplits": [
                { "partyId": null, "departure": "서울시청", "destination": "강남역",
                  "payer": { "publicId": "$payerId", "nickname": "방장" },
                  "myAmount": 6233, "status": "NOT_SENT", "createdAt": "2026-11-12T00:00:00Z" },
                { "partyId": 43, "departure": null, "destination": null,
                  "payer": null, "myAmount": 7000, "status": "NOT_SENT", "createdAt": null }
              ]
            }
        """.trimIndent()

        val items = json.decodeFromString<MyFareSplitListResponse>(raw).toMyFareSplits()

        assertEquals(listOf(43L), items.map { it.partyId })
        assertNull(items.single().departure)
        assertNull(items.single().createdAt)
        assertEquals(WithdrawnMemberNames.NO_SUMMARY, items.single().payer.nickname)
    }

    /** 빈 목록은 정상이다 — 「내가 보낼 돈이 없다」는 뜻이고 에러가 아니다. */
    @Test
    fun `빈 목록과 키 누락은 모두 빈 결과다`() {
        assertEquals(emptyList<Long>(), json.decodeFromString<MyFareSplitListResponse>("""{"fareSplits":[]}""").toMyFareSplits())
        assertEquals(emptyList<Long>(), json.decodeFromString<MyFareSplitListResponse>("{}").toMyFareSplits())
    }

    // ─── 요청 본문 ───

    /**
     * 요청의 계약은 **키 이름 넷**(`totalFare`·`bankCode`·`accountNumber`·`payments[].userPublicId`)과
     * **계좌번호 정규화**다. 서버는 모르는 키를 무시하므로 이름이 틀리면 「필드 누락」 400 으로만
     * 드러나고 원인이 앱 쪽 오타임을 가린다.
     *
     * 정규화를 **매퍼가** 하는 이유: 화면이 잊어도 하이픈이 섞인 값이 나가지 않게 하려는 배치다
     * (규칙 자체는 도메인에 하나만 있다 — 매퍼가 베껴 쓰지 않는다).
     */
    @Test
    fun `요청 본문은 계좌번호에서 하이픈을 떼고 키 이름을 고정한다`() {
        val draft = FareSplitDraft(
            totalFare = 18_700,
            bankCode = Bank.KAKAO.code,
            accountNumber = "3333-01-2345678",
            shares = FareSplitPolicy.splitEvenly(18_700, payerId, listOf(payerId, senderAId, senderBId)),
        )

        val encoded = json.encodeToString(FareSplitRequestDto.serializer(), draft.toRequestDto())

        assertEquals(
            """{"totalFare":18700,"bankCode":"KAKAO","accountNumber":"3333012345678",""" +
                """"payments":[{"userPublicId":"$payerId","amount":6234},""" +
                """{"userPublicId":"$senderAId","amount":6233},""" +
                """{"userPublicId":"$senderBId","amount":6233}]}""",
            encoded,
        )
    }

    /** 표시 이름을 보내면 400 `INVALID_REQUEST` 다 — 서버가 `Bank` enum 으로 역직렬화한다. */
    @Test
    fun `은행은 표시 이름이 아니라 코드로 나간다`() {
        val dto = FareSplitDraft(18_700, Bank.DAEGU.code, "1101234567890", listOf(FareShare(payerId, 18_700))).toRequestDto()

        assertEquals("DAEGU", dto.bankCode)
        assertFalse("표시 이름이 bankCode 로 나갔다", dto.bankCode == Bank.DAEGU.displayName)
    }

    /**
     * **모르는 은행의 정산도 고칠 수 있어야 한다.** 수정은 은행도 필수 필드라, 초안이 은행을
     * [Bank] enum 으로만 받았다면 서버가 추가한 은행의 정산은 **금액만 고치는 것조차 불가능**해진다.
     * 그래서 코드를 문자열로 들고 다니고, 상세의 `bank.code` 를 그대로 되돌린다.
     */
    @Test
    fun `모르는 은행 코드도 그대로 되돌려 보낼 수 있다`() {
        val loaded = detail(
            payerViewJson.replace(
                """"bank": { "code": "KAKAO", "name": "카카오뱅크" }""",
                """"bank": { "code": "NEW_BANK_2027", "name": "새로생긴은행" }""",
            ),
        )

        val dto = FareSplitDraft(
            totalFare = loaded.totalFare,
            bankCode = loaded.bank.code,
            accountNumber = loaded.accountNumber!!,
            shares = listOf(FareShare(payerId, loaded.totalFare)),
        ).toRequestDto()

        assertEquals("NEW_BANK_2027", dto.bankCode)
    }
}
