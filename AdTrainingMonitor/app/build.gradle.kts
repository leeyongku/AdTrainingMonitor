    import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

// 릴리스 서명 정보. keyStore/keystore.properties(git에는 올라가지 않음, .gitignore의 /keyStore/ 참고)에서
// storeFile/storePassword/keyAlias/keyPassword를 읽어온다. 파일이 없으면(=이 저장소를 새로 받은 경우)
// 릴리스 빌드에서만 실패하고 디버그 빌드는 영향받지 않는다.
val keystorePropertiesFile = rootProject.file("keyStore/keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.training.monitor"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.training.monitor"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 디버그 빌드 기본 서버 주소. adb reverse tcp:5133 tcp:5133로 기기의 localhost를 PC의
        // 5133 포트로 포워딩하므로, 에뮬레이터/실기기 모두 USB로 연결돼 있으면 이 주소로 동작한다.
        // 릴리스 빌드는 USB 연결 없이 Wi-Fi로 독립 동작해야 하므로 아래 buildTypes.release에서
        // PC의 실제 LAN IP로 덮어쓴다.
        buildConfigField("String", "BASE_URL", "\"http://127.0.0.1:5133/\"")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
        dataBinding=true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file("keyStore/${keystoreProperties["storeFile"]}")
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    // 사내 테스트용 APK라 릴리스 빌드 시 자동으로 도는 Lint 검사(lintVitalRelease)를 끈다.
    // (그대로 두면 Lint가 쓰는 외부 도구를 매번 인터넷에서 받아오다 실패할 수 있다.)
    lint {
        checkReleaseBuilds = false
    }

        buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            // 폰이 PC와 같은 Wi-Fi(같은 공유기)에 있을 때 독립적으로 접속할 PC의 LAN IP.
            // 네트워크가 바뀌면(PC의 IP가 바뀌면) 이 값도 같이 바꿔야 한다.
            buildConfigField("String", "BASE_URL", "\"http://192.168.25.81:5133/\"")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

dependencies {

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.legacy.support.v4)
    implementation(libs.core.ktx)
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx:2.9.8")
    implementation("androidx.navigation:navigation-ui-ktx:2.9.8")

    // ViewModel + LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.11.0")

    // Retrofit (네트워크)
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.4.0")

    // 코루틴
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // 그래프
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // 토큰 암호화 저장
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Hilt (의존성 주입)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.cardview)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}