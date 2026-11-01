# 모여타 릴리스 축소(R8) 규칙.
#
# **지금 이 파일은 적용되지 않는다.** app/build.gradle.kts 의 release 가
# `optimization { enable = false }` 라 R8 을 아예 돌리지 않기 때문이다. 그래도 미리 두는 이유는,
# 지금 릴리스가 멀쩡한 근거가 「규칙이 잘 쓰여 있어서」가 아니라 **「축소를 안 해서」뿐**이라는 데 있다.
# AGP 9 는 R8 설정을 `optimization {}` 로 옮겼으니 템플릿 갱신·AGP 업그레이드로 그 한 줄이 바뀌는 날
# 규칙이 없으면 **디버그는 멀쩡한데 릴리스에서만 전 API 가 터진다** — Retrofit 인터페이스가 지워지고
# (@retrofit2.http 메서드만 가진 인터페이스는 R8 full mode 에서 구현체 없는 타입으로 보인다),
# @Serializable DTO 의 `serializer()`·`Companion` 을 못 찾아 모든 요청·응답이 깨진다.
#
# 켜려면 `optimization { enable = true }` 로 바꾸고 **릴리스 빌드를 실기로 한 바퀴** 돌려라
# (로그인 → 방 생성 → 합류 → 채팅 → 지도 → 신고). 리플렉션 경로의 실패는 컴파일이 아니라
# 런타임에만 보이고, 릴리스에서만 보인다.
#
# 라이브러리가 **스스로 싣고 오는** 규칙은 여기 옮겨 적지 않아도 R8 이 읽는다
# (AAR 의 proguard.txt · JAR 의 META-INF/com.android.tools/{r8,proguard}):
#   - retrofit 3.0.0        → META-INF/proguard/retrofit2.pro
#   - kotlinx-serialization 1.9.0 → META-INF/com.android.tools/r8/kotlinx-serialization-*.pro
#   - naver map-sdk 3.23.3  → AAR 의 proguard.txt (NativeApi·geometry 키프)
#   - firebase-messaging · datastore · play-services-location → 각 AAR
# 아래는 ① 그 위에 **앱 쪽에서** 못 박아야 하는 것, ② 자기 규칙을 싣지 않는 라이브러리(okhttp 5.1.0),
# ③ 위 규칙을 `optimization { keepRules { ignoreFrom(...) } }` 로 무시하게 되는 날에도 남아야 하는
# 최소 안전망이다(그래서 일부는 의도적으로 중복이다 — 중복 규칙은 무해하다).


# ───────────────────── kotlinx.serialization (1.9.0) ─────────────────────
# 앱 DTO 는 전부 com.moyeota.** 한 뿌리에 있으니 생성된 serializer 를 이름째 못 박는다.
# R8 full mode 에서 Companion·serializer() 가 지워져 `SerializationException: Serializer for class
# ... not found` 가 나는 것이 이 묶음에서 가장 흔한 사고다.
-keep,includedescriptorclasses class com.moyeota.**$$serializer { *; }
-keepclassmembers class com.moyeota.** {
    *** Companion;
}
-keepclasseswithmembers class com.moyeota.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# @Serializable·@Polymorphic 은 런타임에 읽힌다
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
# core 가 캐시에 쓰는 java.lang.ClassValue 는 안드로이드에 없다 — 쓰이지도 않으니 경고를 끈다
-dontwarn kotlinx.serialization.internal.ClassValueReferences


# ───────────────────── Retrofit (3.0.0) ─────────────────────
# Retrofit 이 제네릭·애노테이션을 리플렉션으로 읽는다. Signature 를 잃으면 suspend 함수의
# 반환 타입(Continuation 의 타입 인자)이 사라져 응답 변환이 깨진다.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleParameterAnnotations
# R8 full mode 는 Proxy 로만 만들어지는 서비스 인터페이스의 구현체를 못 보고 값을 null 로 바꾼다
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface * extends <1>
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**


# ───────────────────── OkHttp (5.1.0) · Okio ─────────────────────
# okhttp 5.1.0 은 artifact 에 규칙을 싣지 않는다 — 여기가 유일한 자리다(공식 권장 그대로).
# 상대 경로로 읽는 리소스가 있어 이 클래스의 이름은 보존돼야 한다.
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
# classpath 에 있을 때만 쓰이는 보안 프로바이더들 — 없는 게 정상이라 경고만 끈다
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.codehaus.mojo.animal_sniffer.*


# ───────────────────── 네이버 지도 (map-sdk 3.23.3) ─────────────────────
# AAR 의 proguard.txt 가 이미 같은 내용을 싣고 온다. 안전망으로 남겨 두는 것은 **JNI 경계**다 —
# 네이티브에서 이름으로 찾는 멤버가 지워지면 지도가 뜨지 않고 그 실패는 릴리스에서만 보인다.
-keep,allowobfuscation @interface com.naver.maps.map.internal.NativeApi
-keep @com.naver.maps.map.internal.NativeApi class * {
    *;
}
-keepclasseswithmembers class * {
    @com.naver.maps.map.internal.NativeApi <methods>;
}
-keepclasseswithmembers class * {
    @com.naver.maps.map.internal.NativeApi <fields>;
}
-keepclasseswithmembers class * {
    native <methods>;
}
# 좌표는 네이티브가 필드·생성자로 직접 다룬다
-keep public class com.naver.maps.geometry.LatLng {
    <fields>;
    <init>(double, double);
}
-keep public class com.naver.maps.geometry.LatLngBounds {
    <fields>;
    <init>(double, double, double, double);
}


# ───────────────────── 쓰지 않는 것 ─────────────────────
# Compose·java.time 디슈가링·Krossbow(STOMP)는 리플렉션을 쓰지 않아 규칙이 필요 없다.
# enum 의 values()·valueOf(String) 는 getDefaultProguardFile("proguard-android-optimize.txt") 가
# 이미 지킨다(kotlinx.serialization 의 enum 처리가 그 둘에 기댄다) — 여기서 또 적지 않는다.
