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
