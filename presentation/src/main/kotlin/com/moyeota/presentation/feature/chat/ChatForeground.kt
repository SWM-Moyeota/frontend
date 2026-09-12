package com.moyeota.presentation.feature.chat

/**
 * 지금 **화면에 떠 있는** 채팅방 id. 없으면 null.
 *
 * 푸시 수신 서비스(app 모듈)가 「이 방을 보고 있는 중이면 알림을 띄우지 않는다」를 판단하는 데 쓴다 —
 * 대화를 보고 있는데 같은 메시지가 상단 알림으로도 뜨면 두 번 알리는 셈이다. 폴링(3초)이 새 메시지를
 * 곧 화면에 그리므로 알림 없이도 놓치지 않는다.
 *
 * [ChatRoomRoute] 가 화면 START 에 적고 STOP 에 지운다. 프로세스 전역 단일 값이라 방을 옮기면 덮어쓴다.
 * FCM 서비스 스레드에서 읽으므로 @Volatile.
 */
object ChatForeground {
    @Volatile
    var visibleRoomId: Long? = null
}
