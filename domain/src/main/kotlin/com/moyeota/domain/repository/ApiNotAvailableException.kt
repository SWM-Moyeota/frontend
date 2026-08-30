package com.moyeota.domain.repository

// 화면 흐름상 필요하지만 백엔드에 아직 엔드포인트가 없는 동작에 던진다.
// ViewModel 은 이 예외를 잡아 사용자에게 "아직 준비 중" 메시지를 보여주면 된다.
// 존재하지 않는 경로를 추측해 호출하고 404 를 받는 것보다 원인이 분명하다.
class ApiNotAvailableException(message: String) : RuntimeException(message)
