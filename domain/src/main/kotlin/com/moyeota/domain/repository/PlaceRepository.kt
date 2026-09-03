package com.moyeota.domain.repository

import com.moyeota.domain.model.FavoritePlace
import com.moyeota.domain.model.Place
import com.moyeota.domain.model.ReverseAddress

interface PlaceRepository {
    /** GET /api/v1/places?query=. 카카오 로컬 검색 프록시라 결과가 0건일 수 있다. */
    suspend fun searchPlaces(query: String): List<Place>

    /**
     * GET /api/v1/places/reverse?latitude=&longitude=. 지도 핀 위치의 주소를 얻는다.
     *
     * 주소가 없는 좌표(바다·비무장지대 등)면 **예외가 아니라 null** 이다.
     * 핀을 아무 데나 찍는 건 정상 유저 행동이라 에러 화면을 띄울 일이 아니다 —
     * 호출측은 null 이면 주소 표시 갱신만 생략하고 직전 값을 유지한다.
     *
     * 그 외 실패는 기존 컨벤션대로 예외:
     * - 400 INVALID_COORDINATES: 한국 영역(위 33~39, 경 124~132) 밖 좌표
     * - 502 REVERSE_GEOCODE_FAILED: 네이버 역지오코딩 호출 실패
     */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): ReverseAddress?

    /**
     * POST /api/v1/users/me/favorite-places. 응답 본문 없음(204).
     * 소유자는 로그인 토큰이 정한다(@CurrentUser) — userId 를 넘기지 않는다.
     *
     * 실패: 401 `UNAUTHORIZED`(미로그인) / 같은 이름 중복 등록 / 10개 초과.
     * 성공 후 [getFavoritePlaces] 재조회 필요.
     *
     * ⚠ 실서버 현재 상태: 이 POST 는 서버 내부 오류로 **항상 500** 이다(백엔드 결함, 리포트 22 참조).
     * 화면은 실패를 정상적으로 안내만 하면 되고 앱 쪽에 고칠 것은 없다.
     */
    suspend fun addFavoritePlace(place: Place)

    /** GET /api/v1/users/me/favorite-places. 토큰 주체의 목록. sequence 오름차순 정렬해서 돌려준다. */
    suspend fun getFavoritePlaces(): List<FavoritePlace>
}
