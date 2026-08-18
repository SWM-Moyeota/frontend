---
name: compose-feature
description: "모여타 앱의 Compose 화면 구현 레시피. 화면 신규 생성, 기존 화면 수정, UI 변경, 컴포넌트 추가, 네비게이션 연결, 디자인 반영 등 presentation/ 또는 core:designsystem을 건드리는 모든 작업 시 반드시 이 스킬을 사용할 것. 화면 번호(01~35)나 화면 ID(S01 등)가 언급되면 이 스킬을 사용할 것."
---

# 모여타 화면 구현 레시피

## 모듈 지도

| 모듈 | 역할 | 규칙 |
|------|------|------|
| `presentation/` | 화면·ViewModel·네비게이션 | domain만 의존, data 직접 참조 금지 |
| `core/designsystem/` | 공용 컴포넌트·테마 | 도메인 모델 참조 금지 |
| `domain/` | 모델·Repository 인터페이스 | UI에서 보는 유일한 데이터 계약 |
| `app/` | MainActivity·AppContainer(수동 DI) | Repository 인스턴스 공급처 |

화면 스펙의 원천은 `WIREFRAME-MVP1.md` — 화면 번호(01~35)와 화면 ID(S01 등)로 요구사항을 확인한다.

## 화면 구현 순서

1. **스펙 확인**: WIREFRAME-MVP1.md에서 해당 화면 번호의 요구사항을 읽는다
2. **유사 화면 모방**: `presentation/feature/` 하위에서 가장 비슷한 기존 화면을 찾아 구조를 따른다. 새 스타일을 발명하지 않는다 — 35개 화면의 일관성이 이미 확립되어 있다
3. **Screen 작성**: 스테이트리스 Composable. 데이터·콜백은 전부 파라미터로 받는다
4. **Route 작성** (서버 데이터 필요 시): ViewModel + UiState를 같은 파일에 두는 `ExploreRoute.kt` 패턴
5. **라우트 등록**: `Routes.kt`에 상수 추가(화면 번호 주석 포함) → `MainNavGraph.kt`에 composable 등록
6. **빌드 확인**: `./gradlew :app:assembleDebug --console=plain`

## ViewModel 패턴 (ExploreRoute.kt 기준)

서버 연동 화면은 이 형태를 그대로 따른다:

```kotlin
class FooViewModel(private val repository: RideRepository) : ViewModel() {
    sealed interface UiState {
        data object Loading : UiState
        data class Success(val data: ...) : UiState
        data class Error(val message: String) : UiState   // 한국어 사용자 메시지
    }
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { refresh() }
    fun refresh() { viewModelScope.launch { /* try-catch로 Error 매핑 */ } }

    companion object {
        fun factory(repository: RideRepository) = viewModelFactory {
            initializer { FooViewModel(repository) }
        }
    }
}
```

- Hilt 미도입 상태 — `viewModel(factory = ...)` + `AppContainer`에서 내려온 repository 파라미터를 쓴다
- 로딩/에러 UI는 `presentation/core/LoadState.kt`의 `LoadingBox` / `ErrorBox`(onRetry 필수)를 재사용한다
- 주의: `init { refresh() }` 패턴은 화면 재진입 시에만 재로드된다. 액션 후 목록 갱신이 필요하면 액션 성공 콜백에서 명시적으로 refresh를 호출한다

## 디자인 시스템

- 색상은 `MoyeotaColor`(theme/Color.kt)만 사용. hex 하드코딩 금지 — 토큰이 바뀌면 전 화면이 함께 바뀌어야 한다
- 기존 컴포넌트 우선 재사용: `PrimaryCtaButton`, `SecondaryButton`, `MoyeotaTopBar`, `MoyeotaBottomBar`(+`MoyeotaTab`), `MoyeotaTextField`, `MoyeotaChip`, `StatusBadge`, `NoticeBanner`, `AvatarCircle`, `SheetHandle`, `PageDots`, `MapPlaceholder`, `SafetyButton`, `BackArrowIcon`, `StatusBarMock`
- 2개 이상 화면에서 쓰이는 새 UI 요소만 `core/designsystem/component/`로 승격한다. 단일 화면 전용이면 화면 파일 내 private Composable로 둔다

## 네비게이션 규칙

- `Routes.kt` 상수는 `"영역/화면"` 소문자-하이픈 형식, 오른쪽에 `// 화면번호 화면ID` 주석을 단다
- 모달성 화면은 `MainNavGraph.kt`에서 `dialog(...)`로 등록한다 (`DestinationConfirmModal` 참조)
- 하단탭 화면(홈/합승/채팅/마이)은 `MoyeotaBottomBar`의 `onTabSelect` 콜백으로 이동한다

## 완료 체크리스트

- [ ] `./gradlew :app:assembleDebug` 통과
- [ ] Routes 상수·NavGraph 등록·navigate 호출 3자 일치
- [ ] 하드코딩 색상 없음 (`MoyeotaColor` 외 Color(0x...) 검색으로 확인)
- [ ] 서버 화면이면 Loading/Error/Success 3상태 모두 렌더링 가능
- [ ] 변경 파일 목록 + 화면 진입 경로를 산출물 파일에 기록
