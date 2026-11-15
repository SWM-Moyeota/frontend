plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    // google-services **뒤에** 와야 한다 — Crashlytics 플러그인이 google-services 가 만든
    // google_app_id 를 읽어 매핑 업로드 태스크를 구성한다.
    alias(libs.plugins.firebase.crashlytics)
}

/**
 * 빌드 설정값을 local.properties(개발자 로컬) → 환경변수(CI) → [default] 순으로 읽는다.
 * providers.* 를 쓰는 이유: configuration-cache 가 켜져 있어 입력이 추적돼야 한다.
 */
fun buildSetting(key: String, default: String): String = providers
    .fileContents(rootProject.layout.projectDirectory.file("local.properties"))
    .asText.orNull
    ?.lineSequence()
    ?.map(String::trim)
    ?.firstOrNull { it.startsWith("$key=") }
    ?.substringAfter('=')
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: providers.environmentVariable(key).orNull?.takeIf { it.isNotEmpty() }
    ?: default

/**
 * 네이버 지도 NCP client-id.
 * 키가 없으면 빈 문자열로 폴백한다 — 키 없는 팀원 로컬에서도 빌드는 성공하고
 * 지도만 런타임 인증 실패(401)한다.
 */
val naverMapsClientId: String = buildSetting("NAVER_MAPS_CLIENT_ID", default = "")

/**
 * 백엔드 base URL. 기본값은 **운영 서버**(api.moyeota.co.kr, ALB/HTTPS).
 *
 * 2026-10-15 에 `api.moyeota.p-e.kr` 에서 옮겼다. 옛 주소도 당분간 살아 있다 — 이미 배포된
 * 빌드가 그쪽을 보고 있어서, 테스터가 전부 새 버전으로 올라온 뒤에야 끊을 수 있다.
 * (ALB 리스너에 두 인증서가 함께 붙어 있어 SNI 로 갈린다.)
 *
 * 법적 페이지·랜딩은 **옮기지 않았다** — `legal.moyeota.p-e.kr`, `www.moyeota.p-e.kr` 그대로다.
 * API 와 정적 페이지는 인프라도 장애 범위도 달라 도메인을 나눠 둔다(API 가 죽어도 처리방침은
 * 떠 있어야 한다). 앱의 그 링크들은 `presentation/core/SupportLinks.kt` 에 있다.
 * 로컬 백엔드로 붙일 때는 local.properties 나 환경변수에
 * MOYEOTA_BASE_URL=http://10.0.2.2:8080/ (에뮬레이터) 또는 http://<개발PC LAN IP>:8080/ (실기기) 을 지정해 오버라이드한다.
 * Retrofit baseUrl 규약상 반드시 '/' 로 끝나야 하므로 보정해 둔다.
 */
val moyeotaBaseUrl: String =
    buildSetting("MOYEOTA_BASE_URL", default = "https://api.moyeota.co.kr/")
        .let { if (it.endsWith("/")) it else "$it/" }

/**
 * 릴리스 서명 정보. **키스토어와 비밀번호는 저장소에 두지 않는다** — local.properties(개발자 로컬) 나
 * 환경변수(CI 시크릿)로만 받는다. `.gitignore` 가 `*.jks`·`*.keystore`·`local.properties` 를 이미 막고 있다.
 *
 * 값이 없거나 키스토어 파일이 없으면 **서명을 붙이지 않는다**. 키가 없는 팀원도 `assembleRelease` 가
 * 실패하지 않게 하기 위해서다(서명 없는 결과물은 스토어에 올릴 수 없고 기기 설치도 막힌다 — 그래서
 * 실수로 서명 없는 빌드를 올릴 위험은 없다).
 *
 * 만드는 법과 전체 배포 절차는 `docs/RELEASE.md` 참고.
 */
/**
 * Mixpanel 프로젝트 토큰(codingforest 4080134 — iOS 와 같은 프로젝트). local.properties 또는 환경변수 MIXPANEL_TOKEN 에서 읽는다.
 * 비어 있으면 MixpanelAnalyticsReporter 가 SDK 도 sink 도 설치하지 않는다 — 토큰 없는 팀원 로컬·fork 빌드는
 * 이벤트를 보내지 않을 뿐 정상 동작한다. 토큰은 저장소에 커밋하지 않는다(CI 는 secrets.MIXPANEL_TOKEN).
 */
val mixpanelToken: String = buildSetting("MIXPANEL_TOKEN", default = "")

/**
 * Meta(Facebook) 광고 설치 측정. 앱 ID 는 메타 앱 「모여타」의 **공개 값**이라 기본값으로 둔다(iOS Base.xcconfig 와 같은 값).
 * 클라이언트 토큰은 저장소에 두지 않는다 — local.properties 또는 환경변수 FACEBOOK_CLIENT_TOKEN(CI 는 secrets).
 * 개발자 센터 → 앱 설정 → 고급 → 보안에 있다.
 *
 * 토큰이 비면 SDK 의 FacebookInitProvider 가 초기화 예외를 **스스로 잡고 로그만 남긴다**(19.0.1 소스 확인).
 * 즉 토큰 없는 팀원 로컬·fork 빌드는 메타로 아무것도 보내지 않을 뿐 정상 동작한다 — Mixpanel 과 같은 규칙.
 */
val facebookAppId: String = buildSetting("FACEBOOK_APP_ID", default = "2448080759263977")
val facebookClientToken: String = buildSetting("FACEBOOK_CLIENT_TOKEN", default = "")

val keystorePath: String = buildSetting("MOYEOTA_KEYSTORE_PATH", default = "")
val keystorePassword: String = buildSetting("MOYEOTA_KEYSTORE_PASSWORD", default = "")
val keyAliasValue: String = buildSetting("MOYEOTA_KEY_ALIAS", default = "")
val keyPasswordValue: String = buildSetting("MOYEOTA_KEY_PASSWORD", default = "")

/** 서명에 필요한 네 값이 모두 있고 키스토어 파일이 실제로 있을 때만 서명한다. */
val releaseSigningReady: Boolean = keystorePath.isNotEmpty() &&
    keystorePassword.isNotEmpty() &&
    keyAliasValue.isNotEmpty() &&
    keyPasswordValue.isNotEmpty() &&
    rootProject.file(keystorePath).exists()

/**
 * 스토어에 올리는 빌드 번호. **올릴 때마다 반드시 올려야 하고 같은 값으로는 두 번 못 올린다.**
 * CI 가 `MOYEOTA_VERSION_CODE` 로 덮어쓴다(예: 빌드 번호나 날짜 기반).
 */
val moyeotaVersionCode: Int = buildSetting("MOYEOTA_VERSION_CODE", default = "1").toInt()

/** 사용자에게 보이는 버전. 35 마이페이지 하단에 이 값이 그대로 뜬다. */
val moyeotaVersionName: String = buildSetting("MOYEOTA_VERSION_NAME", default = "1.0")

android {
    namespace = "com.moyeota.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.moyeota"
        minSdk = 24
        targetSdk = 36
        versionCode = moyeotaVersionCode
        versionName = moyeotaVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["naverMapsClientId"] = naverMapsClientId
        buildConfigField("String", "GOOGLE_SERVER_CLIENT_ID", "\"${buildSetting("GOOGLE_SERVER_CLIENT_ID", "568416567998-gd5lf8u4ohshrar4bjj18nrp8va01vrv.apps.googleusercontent.com")}\"")
        buildConfigField("String", "BASE_URL", "\"$moyeotaBaseUrl\"")
        buildConfigField("String", "MIXPANEL_TOKEN", "\"$mixpanelToken\"")

        // manifestPlaceholders 가 아니라 문자열 리소스로 넘긴다. meta-data 에 숫자만 있는 값을 직접 쓰면
        // 플랫폼이 Integer/Float 로 읽어 버리고, SDK 는 숫자형 앱 ID 를 거부한다(FacebookSdk.loadDefaultsFromMetadata).
        resValue("string", "facebook_app_id", facebookAppId)
        resValue("string", "facebook_client_token", facebookClientToken)
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                // 바깥 val 이름 뒤에 Value 를 붙인 이유: 이 블록 안에서는 keyAlias·keyPassword 가
                // **수신자의 프로퍼티**로 먼저 해석된다. `this.keyAlias = keyAlias` 로 쓰면 오른쪽이
                // 바깥 변수가 아니라 자기 자신(null)이 되어, 빌드가 아니라 **서명 단계에서**
                // NullPointerException 으로 터진다 — 이름을 겹치지 않게 두는 것이 유일한 방어다.
                storeFile = rootProject.file(keystorePath)
                storePassword = keystorePassword
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            optimization {
                // **R8 을 끈 상태로 둔다.** 켜는 순간 리플렉션 경로(Retrofit 서비스 인터페이스 ·
                // kotlinx.serialization 의 serializer)가 조용히 깨질 수 있고, 그 실패는 컴파일이 아니라
                // **릴리스 런타임에만** 보인다 — 켜려면 릴리스 빌드로 실기 QA 를 한 바퀴 돌려야 한다.
                // 즉 지금 릴리스가 안전한 근거는 「규칙이 잘 쓰여 있어서」가 아니라 「축소를 안 해서」다.
                // 그래서 규칙 파일을 미리 붙여 둔다(아래 proguardFiles) — AGP 9 가 R8 설정을 이
                // optimization 블록으로 옮긴 뒤라, 템플릿 갱신·AGP 업그레이드로 이 한 줄이 바뀌어도
                // 「규칙이 없어서」 터지는 일은 없게 한다. 자세한 근거는 app/proguard-rules.pro 머리말.
                enable = false
            }
            // 축소가 꺼져 있어 지금은 적용되지 않는다 — 켜질 때 함께 서는 자리다.
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 키가 없으면 null 로 남긴다 — 서명 없는 릴리스가 나오고, 그건 올릴 수도 설치할 수도 없다.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        // BuildConfig.BASE_URL 생성을 위해 필요.
        buildConfig = true
        // defaultConfig 의 resValue(메타 앱 ID·클라이언트 토큰) 생성을 위해 필요.
        resValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // minSdk 24 + java.time(도메인의 LocalDate). 실제 디슈가링은 app 의 dexing 단계에서 일어나므로
        // 라이브러리 모듈뿐 아니라 여기서도 반드시 켜져 있어야 한다.
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.0")
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":presentation"))
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // 긴급 신고에 싣는 실측 좌표(ReportLocationProvider) — FusedLocationProviderClient
    implementation(libs.play.services.location)
    // MoyeotaApplication 이 FCM 토큰 서버 등록을 앱 수명 스코프에서 fire-and-forget 으로 띄운다.
    // data 가 코루틴을 implementation 으로 쓰고 있어 여기까지 전이되지 않으므로 직접 건다.
    implementation(libs.kotlinx.coroutines.android)
    // 프로필 사진 로더. **구성이 여기 있으므로 app 에도 직접 걸어야 한다** —
    // presentation/designsystem 의 implementation 은 app 으로 전이되지 않아 coil3.* 가 안 보인다.
    // coil-network-okhttp 는 OkHttp 주입(MoyeotaApplication.newImageLoader)에 필요하고,
    // 이 모듈에서만 okhttp 5.1.0(data 의 것)과 같은 클래스패스에 올라온다.
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // 이미지 로더에 넘길 OkHttpClient 의 타입이 여기서 보여야 한다(AppContainer.imageClient).
    // 버전을 명시해 **컴파일 헤더와 런타임 구현을 5.1.0 으로 일치**시킨다 — 빼면 coil 이 끌고 온
    // 4.12.0 헤더로 컴파일된다.
    implementation(libs.okhttp)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.crashlytics)
    // 행동 이벤트 전송. Mixpanel 을 아는 파일은 MixpanelAnalyticsReporter 하나다.
    implementation(libs.mixpanel.android)
    // 메타 광고 설치 측정. 코드에서 부르지 않는다 — SDK 의 초기화 ContentProvider 가 매니페스트 값으로 켜고,
    // 자동 앱 이벤트(설치·실행)만 보낸다. 설정은 AndroidManifest.xml 의 com.facebook.sdk.* meta-data.
    implementation(libs.facebook.core)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
