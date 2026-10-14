# 100 입력 — 홈(14) 「자주 가는 곳」 제거 + 「최근 목적지」 자리를 즐겨찾기 API 목록으로

작성: 2026-10-08 (브랜치 refactor/remove-unused-screens; 카카오 제거·마이페이지 변경이 미커밋 상태 — 건드리지 말 것)

## 사용자 요청
- 홈의 「자주 가는 곳」 섹션(가로 카드 3개) 제거
- 「최근 목적지」 섹션(목 데이터: 서면역 1번 출구·사상역 환승센터·부산역 광장)을 **즐겨찾기 API** 목록으로 교체

## 현재 코드
- presentation/.../feature/home/HomeScreen.kt
  - 81행 `data class FavoritePlace(label, address)` — 홈 전용 UI 더미 모델 (domain FavoritePlace 와 이름 충돌 주의)
  - 83행 `data class RecentPlace(name, address, distanceLabel)` — DestinationScreen.kt 에서도 사용 (그쪽은 이번 범위 밖, 클래스 유지)
  - 109~118행 favoritePlaces / recentPlaces 기본값이 목 데이터
  - 193~226행 자주 가는 곳 섹션 (FavoritePlaceCard 349행)
  - 228~270행 최근 목적지 섹션: 제목 Row(「전체」 버튼 onRecentAllClick 미연결) + 카드 Column(RecentPlaceRow 395행: 점·이름·주소·거리)
- presentation/.../feature/home/HomeRoute.kt
  - HomeViewModel.refresh(): `repository.getFavoritePlaces()` → sequence 정렬 → UI FavoritePlace(name, roadName) 로 매핑
  - HomeScreen 호출: favoritePlaces=favorites, onFavoritePlaceClick={ onPlaceQuery(it.address) }, onRecentPlaceClick={ onPlaceQuery(it.name) }
- domain FavoritePlace(name, roadName, latitude, longitude, sequence) — data/Remote 연동 완료, GET /api/v1/users/me/favorite-places
- HomeScreen 은 `myLocation: UserCoordinates?` 를 이미 받는다 (실위치, null 가능)

## 구현 방침
1. 자주 가는 곳 섹션·FavoritePlaceCard·UI 더미 `FavoritePlace(label,address)` 제거. HomeViewModel 은 domain `FavoritePlace` 를 그대로 노출(매핑 제거).
2. 최근 목적지 섹션을 「즐겨찾기」 로:
   - 제목 "즐겨찾기". 「전체」 버튼과 onRecentAllClick 은 제거 (연결될 화면이 없음). 목록은 세로 카드 그대로, 전체 즐겨찾기를 sequence 순으로 모두 표시.
   - 행: 이름(name) + 주소(roadName). 거리 표기는 `myLocation` 이 있으면 위경도로 계산해 "6.2km" 형식(1km 미만은 "850m"), 없으면 생략. 프로젝트에 거리 계산 유틸이 이미 있으면 재사용 (`grep -rn "haversine\|distanceBetween\|distanceKm\|Location.distanceBetween" presentation core domain`).
   - 비어 있으면 카드 대신 안내 문구 "즐겨찾기를 등록하면 여기서 바로 부를 수 있어요" (15 목적지 화면 ★ 로 등록 — 기존 자주 가는 곳 빈 상태 문구 참고).
   - 탭 → 기존 즐겨찾기 탭 동작 유지: `onPlaceQuery(place.roadName)`. 파라미터 이름은 `onFavoritePlaceClick: (FavoritePlace) -> Unit` 로 통일하고 recentPlaces/onRecentPlaceClick 파라미터는 제거.
3. HomeScreen 의 recentPlaces 기본값(목 데이터) 삭제. RecentPlace 클래스는 DestinationScreen 이 쓰므로 남기되, 홈에서 안 쓰면 그 사실을 주석으로.
4. KDoc(86~100행 부근 이동/상태 설명)과 프리뷰 갱신. 미사용 import·색상 정리.
5. 빌드 통과 후 보고. 에뮬레이터 설치는 리더가 한다.

## 검증 포인트 (리더)
- 로그인 계정(초코)에 즐겨찾기가 있으면 목록, 없으면 빈 문구. 목 데이터 문자열(서면역 1번 출구 등) 0건.
