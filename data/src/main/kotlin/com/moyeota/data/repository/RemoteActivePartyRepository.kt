package com.moyeota.data.repository

import com.moyeota.data.local.ActivePartyStorage
import com.moyeota.data.local.RememberedParty
import com.moyeota.domain.model.Ride
import com.moyeota.domain.model.RideStatus
import com.moyeota.domain.repository.ActivePartyRepository
import com.moyeota.domain.repository.ChatRepository
import com.moyeota.domain.repository.RideRepository
import com.moyeota.domain.session.UserSession
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

/**
 * 서버에 "내 진행 중인 방" 엔드포인트가 없어서 앱이 대신 세우는 복구 경로.
 *
 * 두 단계로 찾는다.
 * 1. **로컬 기억**(DataStore) — 방을 만들거나 합류할 때 호출부가 심어 둔 partyId. 가장 싸고 정확하다.
 *    다만 기억은 힌트일 뿐이라 반드시 상세 조회로 검증한다(끝난 방·내가 나간 방일 수 있다).
 * 2. **채팅방 목록**(`GET /chat-rooms/me`) — 기억이 없을 때의 폴백. 방이 매칭되면 채팅방이 생기므로
 *    "내가 속한 채팅방의 partyId" 가 곧 "내가 속한 방"의 후보다. 앱을 지우고 다시 깔았거나
 *    다른 기기에서 합류한 경우를 여기서 건진다.
 *
 *    목록 응답(백엔드 `ChatRoomUserResult`)에는 partyId 필드가 **없다** — 2026-09-14 서버부터 방 정보
 *    (출발·도착·상태)가 목록에 함께 실리면서 [com.moyeota.data.remote.toChatRoomOrNull] 이 방 상세를
 *    읽지 않고 `partyId = 0` 으로 채운다. 그래서 후보마다 **채팅방 상세**(`GET /chat-rooms/{id}` —
 *    이쪽 `ChatRoomResult` 에는 partyId 가 있다)를 한 번 더 읽어 건져 온다([resolvePartyId]).
 *
 *    한동안은 그 0 을 **후보에서 빼** 버려서 이 폴백이 **항상 빈손**이었다. 그러면 앱을 지우고 다시
 *    깐 사람은 방이 서버에 멀쩡히 살아 있는데도 돌아갈 길이 없어 30분 만료를 기다려야 했다(방장이면
 *    방을 끝낼 사람이 사라진다). 추가 요청이 아까워 미뤄 둔 선택이었는데, 값은 기억이 없는 드문
 *    경우에만 최대 [CHAT_ROOM_SCAN_LIMIT] 번이고 평상시에는 한 번도 나가지 않는다.
 *
 *    **0 을 그대로 조회하지는 않는다** — `GET /matching/rooms/0` 은 404 뿐이다(QA 119 D-3).
 *    서버가 목록에 partyId 를 싣기 시작하면 추가 요청은 저절로 사라진다.
 *
 * **네트워크 실패와 "끝난 방"을 절대 같은 값으로 접지 않는다.** 둘을 뭉개면 지하철에서 앱을 켠 사용자의
 * 기억이 지워져, 신호가 돌아와도 매칭 화면으로 못 돌아간다. 그래서 검증 결과는 3값([Verdict])이며
 * 기억 삭제는 **서버가 분명히 아니라고 말했을 때만** 한다.
 */
class RemoteActivePartyRepository(
    private val rideRepository: RideRepository,
    private val chatRepository: ChatRepository,
    private val storage: ActivePartyStorage,
    private val session: UserSession,
) : ActivePartyRepository {

    override suspend fun remember(partyId: String) {
        // 미로그인 상태의 기억은 주인을 적을 수 없어 계정 가드가 무의미해진다 — 아예 남기지 않는다.
        val owner = session.currentUserUuid ?: return
        // 조회할 수 없는 id 는 심지 않는다. 심어 두면 다음 [resolve] 가 그 값으로 404 를 한 번 맞고
        // 지우는 것이 전부다 — 들어오는 자리에서 막는 쪽이 싸다.
        val id = partyId.toPartyIdOrNull() ?: return
        quietly { storage.save(RememberedParty(partyId = id.toString(), ownerUuid = owner)) }
    }

    override suspend fun clear() {
        quietly { storage.clear() }
    }

    override suspend fun resolve(): Ride? {
        // 로그인 전(AuthState.Unknown 포함)에는 "나"를 판정할 수 없다. 멤버 비교가 전부 false 가 되어
        // 진행 중인 방을 끝난 방으로 오판하므로, 기억을 건드리지 않고 그냥 물러난다.
        val owner = session.currentUserUuid ?: return null

        when (val remembered = quietly { storage.load() }) {
            null -> Unit
            else -> {
                if (remembered.ownerUuid != owner) {
                    // 계정이 바뀌었다. 앞 사람의 방으로 끌고 가면 안 되므로 기억을 버리고 다시 찾는다.
                    quietly { storage.clear() }
                } else {
                    when (val verdict = verify(remembered.partyId)) {
                        is Verdict.Active -> return verdict.ride
                        // 서버가 "그 방 아니다"라고 분명히 답했다 → 기억을 지우고 채팅방으로 다시 찾는다.
                        Verdict.Inactive -> quietly { storage.clear() }
                        // 서버에 닿지 못했다 → 기억은 그대로 두고 이번 회차만 포기한다.
                        Verdict.Unknown -> return null
                    }
                }
            }
        }

        return findFromChatRooms()
    }

    /**
     * 채팅방 목록으로 찾는 폴백. 방 하나당 상세 1회라 요청이 늘어나므로 [CHAT_ROOM_SCAN_LIMIT] 개
     * (최신순)까지만 본다 — 오래된 채팅방일수록 이미 끝난 탑승일 확률이 높다.
     */
    private suspend fun findFromChatRooms(): Ride? {
        val rooms = try {
            chatRepository.getMyChatRooms()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            return null
        }

        // 채팅방 id 는 단조 증가하는 서버 PK 다 — createdAt 문자열 파싱 없이 최신순을 얻는 가장 싼 방법.
        val candidates = rooms.sortedByDescending { it.room.id }.take(CHAT_ROOM_SCAN_LIMIT)
        for (room in candidates) {
            // 목록이 partyId 를 주면 그대로 쓴다. 지금 서버는 주지 않으므로(매퍼가 0 으로 채운다)
            // 거의 늘 아래 채팅방 상세를 한 번 더 읽게 된다 — 서버가 목록에 partyId 를 싣기 시작하면
            // 추가 요청은 저절로 사라진다.
            val partyId = room.room.partyId.takeIf { it > 0 }
                ?: resolvePartyId(room.room.id)
                ?: continue
            val verdict = verify(partyId.toString())
            if (verdict is Verdict.Active) {
                // 다음 호출부터는 상세 1회로 끝나도록 찾은 결과를 기억해 둔다.
                remember(partyId.toString())
                return verdict.ride
            }
        }
        return null
    }

    /**
     * 채팅방 상세(`GET /chat-rooms/{id}`)에서 partyId 를 건져 온다 — **목록에는 없고 상세에는 있다**
     * (백엔드 `ChatRoomUserResult` vs `ChatRoomResult`).
     *
     * 이 한 번이 「앱을 지우고 다시 깐 사람이 진행 중인 방으로 돌아갈 수 있는가」를 가른다. 예전에는
     * 목록의 자리값 0 을 후보에서 빼 버려 폴백이 **항상 빈손**이었고, 그 사람은 방이 서버에 멀쩡히
     * 살아 있는데도 30분 만료를 기다려야 했다(방장이면 방을 끝낼 사람이 사라진다).
     *
     * 비싸지 않다: 기억이 있는 평상시에는 이 경로를 아예 타지 않고, 탈 때도 [CHAT_ROOM_SCAN_LIMIT]
     * 개까지다. 실패는 조용히 넘긴다 — 한 방의 네트워크 실패가 **다음 후보까지** 막으면 안 된다.
     * 0 이나 음수는 존재할 수 없는 PK 라 없는 것과 같게 다룬다.
     */
    private suspend fun resolvePartyId(chatRoomId: Long): Long? = try {
        chatRepository.getChatRoom(chatRoomId).partyId.takeIf { it > 0 }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        null
    }

    /**
     * 조회해도 되는 방 id 인가. 서버 partyId 는 1 부터 올라가는 Long PK 라 **0 과 음수는 존재할 수
     * 없다** — 그 값으로 상세를 읽으면 404 뿐이다(QA 119 D-3: 「합승 완료」 직후
     * `GET /api/v1/matching/rooms/0`).
     */
    private fun String.toPartyIdOrNull(): Long? = toLongOrNull()?.takeIf { it > 0 }

    /** 방 상세를 읽어 "지금 내가 타고 있는 방인가"를 판정한다. */
    private suspend fun verify(partyId: String): Verdict {
        // 숫자가 아니거나 0 이하면 우리가 쓴 적 없는 쓰레기 값이다 — **읽지 않고** 아니라고 답한다.
        // 네트워크를 타지 않으므로 [Verdict.Unknown] 이 아니라 [Verdict.Inactive] 다(기억을 지워도 된다).
        val id = partyId.toPartyIdOrNull() ?: return Verdict.Inactive
        val ride = try {
            rideRepository.getPartyDetail(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return if (e.isDefiniteNo()) Verdict.Inactive else Verdict.Unknown
        }
        return if (ride.isActiveForMe()) Verdict.Active(ride) else Verdict.Inactive
    }

    /**
     * 404(방 없음)·403(멤버 아님)만 "확실한 아니오"다. 타임아웃·5xx·파싱 실패는 판단 보류다
     * — 서버가 잠깐 아픈 걸로 사용자의 복귀 경로를 지우면 안 된다.
     */
    private fun Throwable.isDefiniteNo(): Boolean =
        this is HttpException && (code() == 404 || code() == 403)

    private fun Ride.isActiveForMe(): Boolean =
        status in ACTIVE_STATUSES && members.any { it.isMe }

    private inline fun <T> quietly(block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Throwable) {
        // 로컬 저장소 실패(디스크 꽉 참·손상)로 화면을 죽이지 않는다. 최악은 배너가 안 뜨는 것뿐이다.
        null
    }

    private sealed interface Verdict {
        data class Active(val ride: Ride) : Verdict

        /** 서버 근거로 "진행 중이 아니다"가 확정된 상태. 기억을 지워도 되는 유일한 경우. */
        data object Inactive : Verdict

        /** 서버에 닿지 못해 판정 불가. 기억은 남긴다. */
        data object Unknown : Verdict
    }

    private companion object {
        const val CHAT_ROOM_SCAN_LIMIT = 5

        /**
         * "진행 중"으로 볼 상태들.
         *
         * [RideStatus.MATCHED] 가 들어 있는 게 중요하다 — 서버 `COMPLETED`(정원 충족, 기사 매칭 직전)가
         * 여기로 매핑된다([com.moyeota.data.remote.partyStatusToRideStatus]). 이걸 빼면 정원이 막 찬
         * 방, 즉 사용자가 화면을 가장 보고 싶어 하는 그 순간의 방이 "끝난 방"으로 지워진다.
         */
        val ACTIVE_STATUSES = setOf(
            RideStatus.RECRUITING,
            RideStatus.MATCHED,
            RideStatus.DISPATCHING,
            RideStatus.ONGOING,
        )
    }
}
