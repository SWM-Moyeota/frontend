# 근거 — API 29 에서 `imePadding()` 이 죽는 이유, 그리고 `adjustResize` 가 그걸 되살리는 이유

리더가 `androidx.core 1.19.0` 의 `classes.jar` 를 직접 디컴파일해 확인했다(추정 아님).

## 측정 방법
```
javap -p -c -classpath classes.jar 'androidx.core.view.WindowInsetsCompat$Impl20'
```
`Impl20` 은 **API 21~29** 용 구현이다(API 30+ 는 `Impl30` 이 플랫폼 `WindowInsets.getInsets(type)` 를 직접 쓴다).

## 찾은 것 — androidx 는 API 29 이하에서도 IME inset 을 **역산으로 백포트한다**

`getInsetsForType(int, boolean)` 의 `lookupswitch` 에서 `Type.IME`(= 8)은 오프셋 257 로 간다.
그 분기의 바이트코드를 읽으면 이런 뜻이다:

```java
// offset 257~284
if (mOverriddenInsets?.get(indexOf(IME)) != null) return 그 값;

// offset 285~321  ← **이게 핵심이다**
Insets systemWindow = getSystemWindowInsets();
Insets rootStable  = getRootStableInsets();
if (systemWindow.bottom > rootStable.bottom) {
    return Insets.of(0, 0, 0, systemWindow.bottom);   // 초과분을 키보드로 본다
}

// offset 322~  리플렉션 폴백(ViewRootImpl.mVisibleInsets → mRootViewVisibleInsets)
```

즉 **「시스템 윈도우 하단 inset 이 내비게이션 바(stable) 하단보다 크면, 그 초과분이 키보드다」**
로 역산한다.

## 그래서 `adjustNothing` 이 치명적이다

이 역산은 **키보드가 올라올 때 `systemWindowInsets.bottom` 이 실제로 커져야** 성립한다.
그런데 `adjustNothing` 은 **창을 아예 줄이지 않는다** → `systemWindowInsets.bottom` 은 내비바
높이에 그대로 머문다 → `systemWindow.bottom > rootStable.bottom` 이 **항상 false** →
IME inset = 0 → **`imePadding()` 이 아무 일도 하지 않는다.**

반대로 `adjustResize` 면 창이 줄면서 `systemWindowInsets.bottom` 에 키보드 높이가 실리고,
위 역산이 정확히 그 값을 꺼내 준다.

→ **`adjustResize` 는 「우연히 듣는 설정」이 아니라 androidx 백포트가 전제하는 바로 그 조건이다.**
   3번이 매니페스트 한 줄로 끝날 가능성이 높다는 근거다. 다만 **측정으로 확정해야 한다**(아래).

## API 30+ 회귀 위험이 낮은 이유 (그래도 확인은 한다)

- API 30+ 는 `Impl30` 이 플랫폼 API 로 IME inset 을 직접 받으므로 이 역산 경로를 **타지 않는다**.
- `enableEdgeToEdge()` 가 `decorFitsSystemWindows=false` 로 만든 창에서는 R+ 기준
  `SOFT_INPUT_ADJUST_RESIZE` 가 사실상 무시된다 → 지금 동작이 그대로 유지될 것으로 예상된다.
- **예상일 뿐이므로 API 37 비교군 측정은 생략하지 않는다.**

## 측정에서 꼭 봐야 하는 것

1. API 29 에서 `adjustNothing`(현재) → 입력창이 키보드에 **가려지는지** (= 결함 재현)
2. 같은 기기에서 `adjustResize` → 가려지지 않는지 (= 수정 확인)
3. API 37 에서 before/after 가 **동일한지** (= 회귀 없음)
4. 역산이 내비바를 **이중 계산**하지 않는지 — edge-to-edge + `SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION`
   조합에서 하단 여백이 과하게 잡히는 흔한 함정이다. 키보드를 **내린** 상태의 하단 여백도 같이 봐라.
