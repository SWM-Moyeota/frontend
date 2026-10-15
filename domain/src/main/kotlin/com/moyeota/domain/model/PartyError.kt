package com.moyeota.domain.model

/**
 * 방 합류 실패를 화면이 **복구할 수 있는 형태**로 좁힌 예외
 * ([RideRepository.joinParty][com.moyeota.domain.repository.RideRepository.joinParty] 전용).
 *
 * 합류에만 따로 예외를 둔 이유는 실패의 **대가가 유독 크기** 때문이다. 서버는 멤버 추가를 먼저 커밋하고
 * 응답을 만드는데, 그 응답 처리에서 앱이 넘어지면 **그 계정은 어떤 방에도 못 들어간다** — 같은 방에
 * 다시 합류해도, 다른 방에 합류해도 409 로 막히고(아래 [isAlreadyJoined] 참고) 30분 스윕 전까지 그대로다.
 * 그래서 "이미 내가 그 방 멤버다"를 **성공으로 되돌릴 수 있는 신호**가 필요하다.
 *
 * 분기 근거를 프로퍼티로 노출하는 건 [ChatException] 과 같은 이유다 — 서버 코드 문자열 비교가 화면으로
 * 새어 나가면 서버가 enum 이름을 바꿀 때 조용히 어긋난다.
 */
class PartyJoinException(
    /** 서버 `MatchingErrorCode` 이름(`ALREADY_JOINED_OTHER_PARTY` 등). 본문이 없거나 파싱 실패면 null. */
    val code: String?,
    /** 서버가 준 한국어 원문 message. 없으면 null. */
    val serverMessage: String?,
    /** HTTP 상태 코드. 응답 자체가 없으면(네트워크 실패) null. */
    val httpStatus: Int? = null,
    /** 응답을 받지 못한 실패(연결 거부·타임아웃·DNS). 사용자에게는 재시도를 안내한다. */
    val isNetwork: Boolean = false,
    /**
     * **이미 내가 그 방 멤버였을 때** 저장소가 확인해 담아 주는 그 방의 현재 상태.
     *
     * 데이터 계층이 방 상세를 읽어 내 publicId 가 members 에 있는지 **확인한 뒤에만** 채운다 —
     * 추측이 아니라 증거다. 채워져 있으면 화면은 이 실패를 성공처럼 다뤄도 된다
     * (합류 성공과 똑같이 대기 화면으로 넘기면 된다). 그 밖에는 null.
     */
    val joinedStatus: PartyStatus? = null,
    cause: Throwable? = null,
) : RuntimeException(serverMessage ?: code ?: "방에 합류하지 못했습니다.", cause) {

    /**
     * **이미 내가 이 방의 멤버다** — 합류는 "실패"가 아니라 "이미 됨"이다.
     * [joinedStatus] 가 그 방의 현재 상태이므로, 화면은 합류 성공과 같은 길로 보내면 된다.
     */
    val isAlreadyInThisParty: Boolean get() = joinedStatus != null

    /**
     * **다른 방에 진행 중이다** — 그 방을 먼저 정리(나가기·종료)해야 이 방에 들어갈 수 있다.
     *
     * 서버는 "이 방"과 "다른 방"을 **코드로 구분해 주지 않는다**(둘 다 `ALREADY_JOINED_OTHER_PARTY`).
     * 가른 건 데이터 계층이다 — 방 상세에 내가 없을 때만 여기로 떨어진다.
     */
    val isAlreadyInAnotherParty: Boolean get() = code == ALREADY_JOINED_OTHER_PARTY && joinedStatus == null

    /** 어느 쪽이든 "이미 참여 중"이라 서버가 막은 것. 같은 요청을 다시 보내도 결과가 같다 */
    val isAlreadyJoined: Boolean get() = isAlreadyJoinedCode(code)

    /** 409 `PARTY_FULL` — 정원이 가득 찼다(내가 보던 목록이 낡았다). */
    val isPartyFull: Boolean get() = code == "PARTY_FULL"

    /** 409 `PARTY_CLOSED` — 모집이 끝난 방이다(이미 매칭·종료로 넘어갔다). */
    val isPartyClosed: Boolean get() = code == "PARTY_CLOSED"

    /** 404 `PARTY_NOT_FOUND` — 방이 사라졌다. 목록에서 빼야 한다. */
    val isPartyNotFound: Boolean get() = code == "PARTY_NOT_FOUND" || (code == null && httpStatus == 404)

    companion object {
        /**
         * 서버가 **재합류에도 이 코드를 준다.** `PartyService.join` 이 `party.join()` 보다 먼저
         * `existsOngoingByMemberId` 를 보고, 그 질의가 합류 대상 방을 제외하지 않기 때문이다 —
         * 그래서 "이미 참여한 방"을 뜻하는 [ALREADY_JOINED_PARTY] 는 사실상 오지 않는다.
         */
        const val ALREADY_JOINED_OTHER_PARTY = "ALREADY_JOINED_OTHER_PARTY"

        /** 서버에는 있지만 합류 경로에서는 위 코드에 가려 사실상 오지 않는다 — 와도 같게 다룬다. */
        const val ALREADY_JOINED_PARTY = "ALREADY_JOINED_PARTY"

        /**
         * 「이미 참여 중」을 뜻하는 409 인가 — 이때만 "혹시 그게 이 방인가"를 확인할 가치가 있다.
         *
         * 두 코드를 함께 보는 건 [ALREADY_JOINED_OTHER_PARTY] 의 사정 때문이다: 코드만으로는
         * 이 방인지 다른 방인지 가릴 수 없어, 데이터 계층이 방 상세로 확인한다.
         * 서버 코드 문자열을 아는 곳을 여기 하나로 묶어 둔다 — 데이터 계층이 같은 문자열을
         * 또 들고 있으면 한쪽만 서버 변경을 따라간다.
         */
        fun isAlreadyJoinedCode(code: String?): Boolean =
            code == ALREADY_JOINED_OTHER_PARTY || code == ALREADY_JOINED_PARTY
    }
}

/**
 * 「합승 완료」 실패를 화면이 **사유별로 안내할 수 있는 형태**로 좁힌 예외
 * ([RideRepository.finishParty][com.moyeota.domain.repository.RideRepository.finishParty] 전용).
 *
 * 따로 둔 이유는 이 액션의 실패가 **서로 다른 네 가지 뜻**을 갖는데 **HTTP 상태로는 갈리지 않기**
 * 때문이다 — 403 이 둘(`NOT_PARTY_MEMBER`·`NOT_PARTY_HOST`), 409 가 둘(`PARTY_NOT_COMPLETED`·
 * `DRIVER_ALREADY_ASSIGNED`)이다(서버 `Party.finishWithoutDriver` 의 검사 순서가 그대로
 * 이 순서다). 예전에는 넷 다 「합승을 완료하지 못했어요」 하나로 떨어져서, **방장이 아니라서 막힌
 * 사람이 다음에 뭘 해야 하는지 알 수 없었다** — 같은 버튼을 다시 눌러도 같은 403 이다.
 *
 * 그래서 **상태 코드로는 폴백하지 않는다.** 403 을 「방장만 할 수 있어요」로 뭉개면 방 멤버가 아닌
 * 사람에게 방장을 찾으라고 말하게 되고, 409 를 「정원이 안 찼어요」로 뭉개면 기사가 배정된 방에
 * 거짓을 말하게 된다. 코드를 모르면 [code] 는 null 이고 화면은 **일반 문구**로 떨어진다 —
 * 서버가 코드를 늘려도 앱이 틀린 말을 하지 않는다.
 *
 * 분기 근거를 프로퍼티로 노출하는 건 [PartyJoinException] 과 같은 이유다 — 서버 코드 문자열 비교가
 * 화면으로 새어 나가면 서버가 enum 이름을 바꿀 때 조용히 어긋난다.
 */
class PartyFinishException(
    /** 서버 `MatchingErrorCode` 이름(`NOT_PARTY_HOST` 등). 본문이 없거나 파싱 실패면 null. */
    val code: String?,
    /** 서버가 준 한국어 원문 message. 없으면 null. */
    val serverMessage: String?,
    /** HTTP 상태 코드. 응답 자체가 없으면(네트워크 실패) null. 문구 분기에는 쓰지 않는다 — 위 KDoc 참고. */
    val httpStatus: Int? = null,
    cause: Throwable? = null,
) : RuntimeException(serverMessage ?: code ?: "합승을 완료하지 못했습니다.", cause) {

    /**
     * 403 `NOT_PARTY_HOST` — **방장만** 완료할 수 있다(Backend #237).
     *
     * 방장은 서버가 저장하는 값이 아니라 **지금 멤버 중 가장 먼저 합류한 사람**이다
     * (`Party.hostId()` = `min(joinedAt, memberId)`). 그래서 방장이 나가면 다음으로 이른 멤버에게
     * 자동 승계되고, 그 승계는 SSE 변경분에 실리지 않는다 — 앱 스냅샷이 낡아 버튼을 보여 준 뒤에도
     * 이 403 은 날 수 있다. 화면은 **누가 눌러야 하는지**를 말해 줘야 한다.
     */
    val isNotHost: Boolean get() = code == NOT_PARTY_HOST

    /** 403 `NOT_PARTY_MEMBER` — 이 방의 멤버가 아니다. [isNotHost] 와 **같은 403 이지만 뜻이 다르다**. */
    val isNotMember: Boolean get() = code == NOT_PARTY_MEMBER

    /** 409 `PARTY_NOT_COMPLETED` — 정원이 아직 다 차지 않았다(서버는 COMPLETED 방만 닫아 준다). */
    val isNotCompleted: Boolean get() = code == PARTY_NOT_COMPLETED

    /** 409 `DRIVER_ALREADY_ASSIGNED` — 기사가 배정됐다. 이 방은 기사 없이 끝낼 방이 아니다. */
    val isDriverAssigned: Boolean get() = code == DRIVER_ALREADY_ASSIGNED

    /** 404 `PARTY_NOT_FOUND` — 방이 사라졌다. */
    val isPartyNotFound: Boolean get() = code == PARTY_NOT_FOUND

    /**
     * 서버 `MatchingErrorCode` 중 **이 엔드포인트가 실제로 던지는** 코드만 둔다
     * (`PartyService.finish` → `Party.finishWithoutDriver`). 문자열을 아는 곳을 여기 하나로 묶어
     * 데이터 계층과 화면이 각자 들고 있지 않게 한다.
     */
    companion object {
        const val NOT_PARTY_MEMBER = "NOT_PARTY_MEMBER"
        const val NOT_PARTY_HOST = "NOT_PARTY_HOST"
        const val PARTY_NOT_COMPLETED = "PARTY_NOT_COMPLETED"
        const val DRIVER_ALREADY_ASSIGNED = "DRIVER_ALREADY_ASSIGNED"
        const val PARTY_NOT_FOUND = "PARTY_NOT_FOUND"
    }
}
