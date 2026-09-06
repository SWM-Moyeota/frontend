plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.moyeota.presentation"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        // domain 의 NewUser.birthDate 가 java.time.LocalDate 라 화면에서도 필요하다.
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    api(project(":core:designsystem"))
    implementation(project(":domain"))
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // 위치 권한 런타임 요청(rememberLauncherForActivityResult) · ON_RESUME 재확인
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // 현재 위치(Fused Provider). 없는 이미지에서는 프레임워크 LocationManager 로 폴백한다
    implementation(libs.play.services.location)
    // 커서 좌표 변환(OffsetMapping) 처럼 프레임워크 없이 검증 가능한 순수 로직용
    testImplementation(libs.junit)
}
