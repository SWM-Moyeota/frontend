plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.moyeota.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // domain 과 동일 — 매퍼가 LocalDate → Instant 변환에 java.time 을 쓴다.
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    implementation(project(":domain"))
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    // 채팅 실시간 수신(STOMP over WebSocket). 전송 계층은 이미 쓰는 OkHttp 를 그대로 쓴다
    implementation(libs.krossbow.stomp.core)
    implementation(libs.krossbow.websocket.okhttp)
    // 토큰 영속화(access/refresh/userUuid).
    implementation(libs.androidx.datastore.preferences)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
