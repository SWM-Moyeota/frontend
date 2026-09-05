plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
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
 * 백엔드 base URL. 기본값 10.0.2.2 는 에뮬레이터에서 호스트 로컬 백엔드를 가리키는 주소라
 * 실기기에서는 통하지 않는다. 실기기 테스트는 local.properties 나 환경변수에
 * MOYEOTA_BASE_URL=http://<개발PC LAN IP>:8080/ 을 지정해 오버라이드한다.
 * Retrofit baseUrl 규약상 반드시 '/' 로 끝나야 하므로 보정해 둔다.
 */
val moyeotaBaseUrl: String =
    buildSetting("MOYEOTA_BASE_URL", default = "http://10.0.2.2:8080/")
        .let { if (it.endsWith("/")) it else "$it/" }

android {
    namespace = "com.moyeota.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.moyeota"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["naverMapsClientId"] = naverMapsClientId
        buildConfigField("String", "BASE_URL", "\"$moyeotaBaseUrl\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    buildFeatures {
        compose = true
        // BuildConfig.BASE_URL 생성을 위해 필요.
        buildConfig = true
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
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(project(":presentation"))
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // MoyeotaApplication 이 FCM 토큰 서버 등록을 앱 수명 스코프에서 fire-and-forget 으로 띄운다.
    // data 가 코루틴을 implementation 으로 쓰고 있어 여기까지 전이되지 않으므로 직접 건다.
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
