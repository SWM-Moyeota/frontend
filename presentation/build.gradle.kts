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
    // 멤버 아바타가 서버 프로필 사진을 그린다(MemberAvatar). **coil-network-okhttp 를 여기 걸지 마라** —
    // 이 모듈의 컴파일 클래스패스에는 data 의 okhttp 5.1.0 이 올라오지 않아(implementation 이라 전이 안 됨)
    // coil 이 끌고 온 4.12.0 헤더로 컴파일되는데 런타임은 5.1.0 이다. OkHttp 주입은 app 모듈에 있다.
    implementation(libs.coil.compose)
    // 커서 좌표 변환(OffsetMapping) 처럼 프레임워크 없이 검증 가능한 순수 로직용
    testImplementation(libs.junit)
    // ViewModel 이 **무엇을 Repository 에 넘기는가**를 보는 테스트용(ProfileEditSaveTest).
    // `viewModelScope` 는 `Dispatchers.Main.immediate` 라 `Dispatchers.setMain` 없이는 돌지 않는다.
    // 화면을 그리는 테스트가 아니므로 Robolectric·compose-ui-test 는 여전히 필요 없다 — 순수 함수
    // 규칙 테스트로 못 박을 수 없는 「고른 key 가 서버 호출까지 가는가」만 이 의존성으로 본다.
    testImplementation(libs.kotlinx.coroutines.test)
}
