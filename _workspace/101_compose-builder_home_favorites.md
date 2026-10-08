# 101 compose-builder — 홈(14) 자주 가는 곳 제거 + 즐겨찾기 목록

입력: _workspace/100_input_home_favorites.md

## 변경 파일
- presentation/src/main/kotlin/com/moyeota/presentation/feature/home/HomeScreen.kt
- presentation/src/main/kotlin/com/moyeota/presentation/feature/home/HomeRoute.kt
- MainNavGraph.kt 변경 없음 (HomeRoute 시그니처 불변). DestinationScreen.kt·domain·data 미변경.

## 제거
- 「자주 가는 곳」 섹션(가로 카드 Row) · FavoritePlaceCard · HouseIcon/SchoolIcon/BagIcon(카드 전용)
- UI 더미 `data class FavoritePlace(label, address)` (domain FavoritePlace 로 대체)
- 「최근 목적지」 제목 Row 의 「전체」 버튼, 파라미터 recentPlaces / onRecentPlaceClick / onRecentAllClick, RecentPlaceRow
- 목 데이터 기본값(서면 롯데·서면역 1번 출구 등) — favoritePlaces 기본값은 emptyList()
- HomeViewModel 의 UI 모델 매핑 (domain FavoritePlace 를 sequence 정렬만 해서 노출)
- 미사용 import(Arrangement, Size, Path, StrokeJoin)
- RecentPlace 클래스는 DestinationScreen 의 최근 검색 더미가 쓰므로 유지, 홈 미사용 사실을 주석으로 명시

## 새 섹션 구조 (sheetDetail)
- 제목 "즐겨찾기" (13sp Bold GrayMute)
- 비어 있음 → "즐겨찾기를 등록하면 여기서 바로 부를 수 있어요" (12sp GrayAsh)
- 있음 → 흰 카드(라운드 18, 그림자) 안 세로 목록, sequence 순 전부. 행 사이 Hairline 디바이더
- 행 FavoritePlaceRow: 점 · 이름(name) / 도로명주소(roadName) · 거리(있을 때만)
- 탭 → onFavoritePlaceClick(place) → HomeRoute 에서 onPlaceQuery(place.roadName) → 15 목적지

## 거리 계산
- private fun distanceLabel(from: UserCoordinates?, place): String?
- android.location.Location.distanceBetween (DestinationScreen.isSameSpot 과 같은 방식) 로 직선 거리(m)
- < 1000m → "850m" (roundToInt), 이상 → "%.1fkm" (Locale.US, 예 "6.2km")
- myLocation null 또는 비유한 좌표면 null → 거리 칸 생략

## 기타
- KDoc 이동/상태 설명·시트 주석 갱신, 프리뷰 2종(목록+실위치 / 빈 상태)
- 하드코딩 색 2개(점·그림자)를 파일 상단 private val(RowDot, CardShadow)로 정리

## 빌드
- `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:assembleDebug --console=plain -q` → EXIT 0, 출력 없음
- :presentation:compileDebugKotlin --rerun-tasks 에서 HomeScreen/HomeRoute 경고 0건
- 에뮬레이터 설치 안 함

## 진입 경로
- 앱 실행 → 로그인 → 홈(14) 시트 펼침 상태에서 검색 바 아래 「즐겨찾기」 목록
