package com.moyeota.data.remote.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.decodeBase64

/**
 * 액세스 토큰 페이로드에서 우리가 읽는 유일한 클레임.
 * 나머지(tokenType/iat/exp/jti)는 서버만 쓰므로 [Json.ignoreUnknownKeys] 로 흘려보낸다.
 */
@Serializable
private data class JwtPayload(val sub: String = "")

private val payloadJson = Json { ignoreUnknownKeys = true }

/**
 * JWT 의 `sub` 클레임을 꺼낸다. 백엔드에서 `sub` = 사용자 publicId UUID 다
 * (`JwtProvider.build`: `.subject(publicId.toString())`, 실측으로 가입 응답 `uuid` 와 일치 확인).
 *
 * **왜 토큰을 파싱하는가.** 인증 개편으로 로그인 응답이 `{accessToken, refreshToken}` 만 주게 되면서
 * 서버가 사용자 UUID 를 알려주는 다른 경로가 없어졌다. 가입 응답의 `uuid` 는 "방금 가입한" 흐름에서만
 * 얻을 수 있고, 앱을 재설치 없이 다시 켰을 때·기존 계정으로 로그인했을 때는 존재하지 않는다.
 * 반면 `sub` 는 로그인·재발급 어느 쪽 토큰에도 항상 들어 있어 **모든 진입 경로에서 같은 값**을 준다.
 *
 * **서명은 검증하지 않는다.** 이 값은 화면 표시·자기 식별에만 쓰이고 권한 판정에는 절대 쓰이지 않는다
 * — 권한은 서버가 서명을 검증한 뒤 정한다. 앱이 서명 키를 가질 수도 없으므로 검증할 방법 자체가 없다.
 * 토큰을 위조해 얻는 것은 "내 화면에 남의 UUID 가 보인다"뿐이라 공격 가치가 없다.
 *
 * base64url 디코딩에 okio 를 쓰는 이유: `java.util.Base64` 는 API 26+ 이고
 * (minSdk 24, 디슈가링 대상도 아니다) `android.util.Base64` 는 JVM 단위 테스트에서 동작하지 않는다.
 * okio 의 [decodeBase64] 는 표준/URL-safe 알파벳을 모두 받고 패딩이 없어도 동작한다.
 *
 * @return `sub` 값, 또는 토큰 형식이 JWT 가 아니거나 `sub` 가 비어 있으면 null
 */
internal fun jwtSubject(token: String): String? {
    val parts = token.split('.')
    if (parts.size != 3) return null

    val payload = parts[1].decodeBase64()?.utf8() ?: return null

    return runCatching { payloadJson.decodeFromString(JwtPayload.serializer(), payload) }
        .getOrNull()
        ?.sub
        ?.takeIf { it.isNotBlank() }
}
