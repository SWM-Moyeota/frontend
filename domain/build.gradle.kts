plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.moyeota.domain"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // minSdk 24 에서 java.time(LocalDate 등)을 쓰기 위한 코어 라이브러리 디슈가링.
        // 인증의 birthDate 를 도메인에서 LocalDate 로 다루려면 필요하다.
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    // api 로 노출: AuthRepository.authState 가 StateFlow 라 presentation/data 가 그대로 본다.
    api(libs.kotlinx.coroutines.core)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
