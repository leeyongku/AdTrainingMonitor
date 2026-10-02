# Hilt DI 마이그레이션 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `AdTrainingMonitor` 앱의 수동 의존성 생성(`RetrofitClient.create(context)`를 호출부마다 반복)을 Hilt 기반 DI로 교체하고, 그 부수 효과로 `ApiService`/`OkHttpClient`를 앱 전체에서 재사용하는 싱글톤으로 바꾼다.

**Architecture:** `@HiltAndroidApp` Application → `di/NetworkModule.kt`(싱글톤 `OkHttpClient`/`Retrofit`/`ApiService` 제공, 기존 JWT 인증 인터셉터 로직 그대로 이전) → `TokenManager`를 `@Singleton @Inject`로 전환 → ViewModel 9개를 `@HiltViewModel` + `@Inject constructor`로 전환 → Activity/Fragment 9개에 `@AndroidEntryPoint` 추가. 기존 `RetrofitClient.kt`는 모든 호출부가 이전된 마지막 태스크에서 삭제한다(그전까지는 미이전 화면이 계속 참조하므로 남겨둔다).

**Tech Stack:** Kotlin 2.3.21, AGP 9.4.1, Hilt 2.60.1(Dagger 기반, KSP 사용), KSP 2.3.12, Retrofit 3.0.0, OkHttp(logging-interceptor) 5.4.0.

**버전 근거**: 2026-10-01 기준 Maven Central/공식 릴리스 노트로 확인함 — Hilt 2.60.1이 최신이며 Hilt 2.59+부터 Gradle 플러그인이 AGP 9.0+을 지원한다. KSP는 2.3.12가 최신이며(Kotlin 언어 레벨 2.3 대상), 최근 KSP는 버전 문자열에 Kotlin 버전을 더 이상 접두사로 붙이지 않는 방식으로 바뀌었다.

**Spec:** `docs/superpowers/specs/2026-10-01-hilt-di-migration-design.md`

## Global Constraints

- 어노테이션 프로세서는 **KSP**를 사용한다 (KAPT 아님).
- ViewModel은 **`AndroidViewModel` 상속을 유지**한다. `AndroidViewModel`도 Hilt의 `@HiltViewModel` + `@Inject constructor`와 완전히 호환되며, `Application`은 Hilt가 자동으로 넘겨준다.
- `ui/main2/MainActivity2.kt`, `ui/main2/MainActivity2ViewModel.kt`는 **마이그레이션 대상에서 제외**한다 — `AndroidManifest.xml`에 등록되지 않아 실행되지 않는 템플릿 잔재이기 때문이다.
- 기존 동작(로그인, 세션 만료 자동 로그아웃, 대원 CRUD, 기록 CRUD, 사진 촬영/업로드, 통계)은 전부 그대로 유지되어야 한다 — 순수 리팩터링이며 기능 변경이 아니다.
- 세션 만료 시 자동 로그아웃 로직(401 → refresh 시도 → 실패 시 토큰 삭제 + `LoginActivity`로 강제 이동)은 동작을 한 글자도 바꾸지 않는다. 코드 위치만 `RetrofitClient.kt`에서 `di/NetworkModule.kt`로 옮긴다.
- 각 태스크가 끝날 때마다 프로젝트가 컴파일되어야 한다 (`./gradlew assembleDebug` 성공). 이를 위해 `RetrofitClient.kt`는 모든 호출부가 이전되기 전까지 삭제하지 않는다.

---

## Task 1: Hilt/KSP 빌드 설정 + Application 클래스 등록

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts` (루트)
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/training/monitor/TrainingMonitorApp.kt`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: `libs.plugins.hilt.android`, `libs.plugins.ksp`, `libs.hilt.android`, `libs.hilt.compiler` (버전 카탈로그 별칭, Task 2부터 사용), `TrainingMonitorApp` 클래스(이후 태스크에서 수정 없음).

- [ ] **Step 1: `gradle/libs.versions.toml`에 Hilt/KSP 버전·라이브러리·플러그인 추가**

`[versions]` 블록 마지막 줄(`fragmentKtx = "1.5.6"`) 바로 다음에 추가:

```toml
hilt = "2.60.1"
ksp = "2.3.12"
```

`[libraries]` 블록 마지막 줄(`androidx-fragment-ktx = ...`) 바로 다음에 추가:

```toml
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
```

`[plugins]` 블록 마지막 줄(`kotlin-compose = ...`) 바로 다음에 추가:

```toml
hilt-android = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 2: 루트 `build.gradle.kts`에 플러그인 추가**

전체 파일을 다음으로 교체:

```kotlin
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
}
```

- [ ] **Step 3: `app/build.gradle.kts`에 플러그인·의존성 추가**

`plugins { }` 블록을 다음으로 교체:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}
```

`dependencies { }` 블록에서 `implementation("androidx.security:security-crypto:1.1.0-alpha06")` 줄 바로 다음에 추가:

```kotlin

    // Hilt (의존성 주입)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
```

- [ ] **Step 4: `TrainingMonitorApp.kt` 생성**

`app/src/main/java/com/training/monitor/TrainingMonitorApp.kt` 신규 파일:

```kotlin
// Hilt 의존성 그래프의 진입점 — 앱 전역에서 @Inject로 주입받을 수 있게 하는 Application
package com.training.monitor

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TrainingMonitorApp : Application()
```

- [ ] **Step 5: `AndroidManifest.xml`에 Application 클래스 등록**

`<application ...>` 태그 속성 목록에 `android:name=".TrainingMonitorApp"`을 추가한다 (다른 속성과 같은 자리, 순서는 무관). 변경 전:

```xml
    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="훈련 모니터링"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.MaterialComponents.Light.NoActionBar"
        android:usesCleartextTraffic="true">
```

변경 후:

```xml
    <application
        android:name=".TrainingMonitorApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="훈련 모니터링"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.MaterialComponents.Light.NoActionBar"
        android:usesCleartextTraffic="true">
```

- [ ] **Step 6: 빌드 검증**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. KSP가 Hilt 어노테이션 프로세서를 돌리지만 아직 `@AndroidEntryPoint`/`@HiltViewModel`을 쓰는 곳이 없으므로, Hilt 컴포넌트 생성 관련 오류 없이 평소와 동일하게 빌드돼야 한다.

- [ ] **Step 7: 커밋**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts app/src/main/java/com/training/monitor/TrainingMonitorApp.kt app/src/main/AndroidManifest.xml
git commit -m "feat: Hilt/KSP 빌드 설정 및 Application 클래스 등록"
```

---

## Task 2: `TokenManager` → `@Singleton` 전환

**Files:**
- Modify: `app/src/main/java/com/training/monitor/data/local/TokenManager.kt`

**Interfaces:**
- Consumes: Task 1의 Hilt/KSP 플러그인.
- Produces: `TokenManager` 생성자가 `@Inject constructor(@ApplicationContext context: Context)`로 바뀜. 공개 API(프로퍼티/메서드 이름·시그니처)는 전혀 바뀌지 않으므로, 아직 수동 생성(`TokenManager(application)`)을 쓰는 `LoginViewModel`/`MainViewModel`/`ChangePasswordViewModel`(Task 4~6에서 전환 예정)도 이 태스크 이후 그대로 컴파일된다.

- [ ] **Step 1: `TokenManager.kt` 클래스 선언부 수정**

변경 전 (파일 전체 중 import 블록과 클래스 선언 줄만):

```kotlin
package com.training.monitor.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * access/refresh 토큰과 로그인 역할(role)을 [EncryptedSharedPreferences]에 저장/조회하는 클래스.
 *
 * 일반 SharedPreferences 대신 암호화 저장소를 쓰는 이유는 JWT가 탈취되면 계정이 그대로
 * 도용될 수 있는 민감 정보이기 때문이다 (AndroidKeyStore 기반 AES256-GCM으로 암호화됨).
 * 호출 비용이 크지 않으므로 매 API 호출([com.training.monitor.data.api.RetrofitClient])마다
 * 새 인스턴스를 생성해서 사용해도 무방하다.
 */
class TokenManager(context: Context) {
```

변경 후:

```kotlin
package com.training.monitor.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * access/refresh 토큰과 로그인 역할(role)을 [EncryptedSharedPreferences]에 저장/조회하는 클래스.
 *
 * 일반 SharedPreferences 대신 암호화 저장소를 쓰는 이유는 JWT가 탈취되면 계정이 그대로
 * 도용될 수 있는 민감 정보이기 때문이다 (AndroidKeyStore 기반 AES256-GCM으로 암호화됨).
 * Hilt가 앱 전체에서 공유하는 싱글톤 인스턴스 하나만 생성해 주입한다.
 */
@Singleton
class TokenManager @Inject constructor(@ApplicationContext context: Context) {
```

나머지 본문(프로퍼티 `accessToken`/`refreshToken`/`role`/`mustChangePassword`, `isLoggedIn`/`isAdmin`, `clear()`, `companion object`)은 한 글자도 바꾸지 않는다.

- [ ] **Step 2: 빌드 검증**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. `LoginViewModel`/`MainViewModel`/`ChangePasswordViewModel`이 여전히 `TokenManager(application)`으로 직접 생성하는 중이며, 공개 생성자 시그니처가 그대로이므로 영향이 없어야 한다.

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/java/com/training/monitor/data/local/TokenManager.kt
git commit -m "feat: TokenManager를 Hilt @Singleton으로 전환"
```

---

## Task 3: `di/NetworkModule.kt` 신규 생성 (`RetrofitClient.kt`는 아직 유지)

**Files:**
- Create: `app/src/main/java/com/training/monitor/di/NetworkModule.kt`

**Interfaces:**
- Consumes: `TokenManager`(Task 2에서 `@Singleton @Inject` 전환 완료).
- Produces: Hilt `SingletonComponent`에 `Interceptor`(인증), `HttpLoggingInterceptor`, `OkHttpClient`, `Retrofit`, `ApiService`가 전부 `@Singleton`으로 등록됨. 이후 태스크의 모든 `@HiltViewModel`과 `PhotoViewActivity`가 `ApiService`를 생성자/필드 주입으로 받는다.

- [ ] **Step 1: `NetworkModule.kt` 생성**

`app/src/main/java/com/training/monitor/di/NetworkModule.kt` 신규 파일:

```kotlin
// Retrofit/OkHttp 의존성을 싱글톤으로 제공하는 Hilt 모듈. 기존 RetrofitClient.kt의
// 인증 인터셉터(JWT 자동 첨부, 401 시 refresh 시도, 실패 시 세션 만료 처리)를 그대로 옮겼다.
package com.training.monitor.di

import android.content.Context
import android.content.Intent
import com.training.monitor.BuildConfig
import com.training.monitor.data.api.ApiService
import com.training.monitor.data.local.TokenManager
import com.training.monitor.ui.login.LoginActivity
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * 모든 요청에 Authorization 헤더를 자동으로 붙이고, 토큰 만료(401) 시 refresh token으로
     * 한 번 재발급받아 원래 요청을 재시도하는 인터셉터. 기존 RetrofitClient.kt와 로직 동일.
     */
    @Provides
    @Singleton
    fun provideAuthInterceptor(
        tokenManager: TokenManager,
        @ApplicationContext context: Context
    ): Interceptor = Interceptor { chain ->
        val token = tokenManager.accessToken
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else chain.request()

        var response = chain.proceed(request)

        if (response.code == 401) {
            response.close()
            val newToken = tokenManager.refreshToken?.let {
                runBlocking { refreshAccessToken(tokenManager) }
            }
            if (newToken != null) {
                tokenManager.accessToken = newToken
                val retryRequest = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $newToken")
                    .build()
                response = chain.proceed(retryRequest)
            } else {
                tokenManager.clear()
                context.startActivity(
                    Intent(context, LoginActivity::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
            }
        }
        response
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: Interceptor,
        logging: HttpLoggingInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(logging)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService =
        retrofit.create(ApiService::class.java)

    /**
     * refresh token으로 새 access token을 발급받는다. 인증 인터셉터가 재귀적으로 자기 자신을
     * 타지 않도록, Authorization 헤더가 없는 별도의(비인증) Retrofit 인스턴스로 호출한다.
     * (NetworkModule이 제공하는 싱글톤 Retrofit을 쓰면 인터셉터가 다시 걸려 무한 루프에 빠진다.)
     */
    private suspend fun refreshAccessToken(tokenManager: TokenManager): String? {
        return try {
            val refreshService = Retrofit.Builder()
                .baseUrl(BuildConfig.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)

            val response = refreshService.refresh(
                com.training.monitor.data.model.RefreshRequest(tokenManager.refreshToken!!)
            )
            if (response.isSuccessful) {
                tokenManager.refreshToken = response.body()?.refreshToken
                response.body()?.accessToken
            } else null
        } catch (e: Exception) { null }
    }
}
```

- [ ] **Step 2: 빌드 검증**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. 기존 `RetrofitClient.kt`와 신규 `NetworkModule`이 동시에 존재하지만 서로 다른 패키지/클래스라 충돌하지 않는다. 아직 아무도 Hilt가 제공하는 `ApiService`를 주입받지 않으므로 런타임 동작 변화도 없다.

- [ ] **Step 3: 커밋**

```bash
git add app/src/main/java/com/training/monitor/di/NetworkModule.kt
git commit -m "feat: Retrofit/ApiService를 제공하는 Hilt NetworkModule 추가"
```

---

## Task 4: Login 화면 전환 (`LoginActivity` + `LoginViewModel`)

**Files:**
- Modify: `app/src/main/java/com/training/monitor/ui/login/LoginViewModel.kt`
- Modify: `app/src/main/java/com/training/monitor/ui/login/LoginActivity.kt`

**Interfaces:**
- Consumes: `NetworkModule`이 제공하는 `ApiService`(Task 3), `TokenManager`(Task 2).
- Produces: `LoginViewModel`이 `@HiltViewModel`로 전환됨 — 이후 태스크나 코드가 참조할 새 시그니처 없음(순수 내부 리팩터링).

- [ ] **Step 1: `LoginViewModel.kt` 수정**

변경 전 (import 블록 + 클래스 선언 + `login()` 함수 도입부):

```kotlin
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.training.monitor.data.api.RetrofitClient
import com.training.monitor.data.local.TokenManager
import com.training.monitor.data.model.LoginRequest
import kotlinx.coroutines.launch

/**
 * [LoginActivity]의 ViewModel.
 *
 * [MVVM 변경] 기존에는 Activity가 `TokenManager`를 직접 들고 로그인 API를 호출하고,
 * 로딩 상태(ProgressBar)와 결과 Toast를 그 자리에서 바로 제어했다. MVVM에서는
 * "로그인 요청 자체 + 로딩/결과 상태 보관"을 ViewModel로 옮기고, Activity는 이 상태를
 * 관찰(observe)해서 화면(ProgressBar/Toast/화면 전환)만 갱신하는 역할로 축소된다.
 */
class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenManager = TokenManager(application)
```

변경 후:

```kotlin
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.training.monitor.data.api.ApiService
import com.training.monitor.data.local.TokenManager
import com.training.monitor.data.model.LoginRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [LoginActivity]의 ViewModel.
 *
 * [MVVM 변경] 기존에는 Activity가 `TokenManager`를 직접 들고 로그인 API를 호출하고,
 * 로딩 상태(ProgressBar)와 결과 Toast를 그 자리에서 바로 제어했다. MVVM에서는
 * "로그인 요청 자체 + 로딩/결과 상태 보관"을 ViewModel로 옮기고, Activity는 이 상태를
 * 관찰(observe)해서 화면(ProgressBar/Toast/화면 전환)만 갱신하는 역할로 축소된다.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    application: Application,
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) : AndroidViewModel(application) {
```

`login()` 함수 안의 다음 두 줄:

```kotlin
        _loading.value = true
        val api = RetrofitClient.create(getApplication())

        viewModelScope.launch {
            try {
                val response = api.login(LoginRequest(militaryId, password))
```

을 다음으로 교체:

```kotlin
        _loading.value = true

        viewModelScope.launch {
            try {
                val response = apiService.login(LoginRequest(militaryId, password))
```

그 외(`isAlreadyLoggedIn`, `mustChangePassword` getter, `onToastMessageShown()`, `onLoginHandled()` 등)는 전혀 바꾸지 않는다.

- [ ] **Step 2: `LoginActivity.kt`에 `@AndroidEntryPoint` 추가**

변경 전:

```kotlin
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.training.monitor.databinding.ActivityLoginBinding
import com.training.monitor.ui.main.MainActivity
import com.training.monitor.ui.password.ChangePasswordActivity

/**
 * 앱 진입점(AndroidManifest의 LAUNCHER Activity).
 * 군번/비밀번호를 입력받아 로그인 API를 호출하고, 성공 시 토큰을 저장한 뒤
 * [MainActivity]로 넘어간다. 이미 로그인되어 있으면(유효한 토큰 존재) 입력 화면을
 * 거치지 않고 바로 메인으로 이동한다.
 *
 * [MVVM 변경] 이 클래스는 이제 "화면을 그리고 사용자 입력을 받는 View" 역할만 한다.
 * 로그인 API 호출과 로딩/결과 상태 보관은 [LoginViewModel]이 담당하고, 여기서는
 * 그 결과를 관찰(observe)해 화면에 반영하기만 한다.
 */
class LoginActivity : AppCompatActivity() {
```

변경 후:

```kotlin
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.training.monitor.databinding.ActivityLoginBinding
import com.training.monitor.ui.main.MainActivity
import com.training.monitor.ui.password.ChangePasswordActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * 앱 진입점(AndroidManifest의 LAUNCHER Activity).
 * 군번/비밀번호를 입력받아 로그인 API를 호출하고, 성공 시 토큰을 저장한 뒤
 * [MainActivity]로 넘어간다. 이미 로그인되어 있으면(유효한 토큰 존재) 입력 화면을
 * 거치지 않고 바로 메인으로 이동한다.
 *
 * [MVVM 변경] 이 클래스는 이제 "화면을 그리고 사용자 입력을 받는 View" 역할만 한다.
 * 로그인 API 호출과 로딩/결과 상태 보관은 [LoginViewModel]이 담당하고, 여기서는
 * 그 결과를 관찰(observe)해 화면에 반영하기만 한다.
 */
@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {
```

나머지 본문은 바꾸지 않는다 — `by viewModels()`는 대상이 `@HiltViewModel`이기만 하면 그대로 동작한다.

- [ ] **Step 3: 빌드 검증**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/java/com/training/monitor/ui/login/LoginViewModel.kt app/src/main/java/com/training/monitor/ui/login/LoginActivity.kt
git commit -m "refactor: Login 화면을 Hilt DI로 전환"
```

---

## Task 5: Main 화면 전환 (`MainActivity` + `MainViewModel`)

**Files:**
- Modify: `app/src/main/java/com/training/monitor/ui/main/MainViewModel.kt`
- Modify: `app/src/main/java/com/training/monitor/ui/main/MainActivity.kt`

**Interfaces:**
- Consumes: `TokenManager`(Task 2). **`MainViewModel`은 API 호출이 전혀 없으므로 `ApiService`는 주입하지 않는다** (Task 4/6과 다른 패턴 — 명세 Section 5의 일반 패턴에서 유일하게 `ApiService` 없이 `TokenManager`만 받는 ViewModel).

- [ ] **Step 1: `MainViewModel.kt` 전체 교체**

변경 전 (전체 파일):

```kotlin
// 메인 화면(하단 네비게이션 컨테이너)의 역할 판별/로그아웃을 담당하는 ViewModel

package com.training.monitor.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.training.monitor.data.local.TokenManager

/**
 * [MainActivity]의 ViewModel.
 *
 * [MVVM 변경] MainActivity는 네트워크 호출이나 로딩 상태가 없는 "껍데기" 화면이라
 * LiveData로 흘려보낼 비동기 상태는 없지만, TokenManager를 직접 들고 있던 책임
 * (관리자 여부 판별, 로그아웃 시 토큰 삭제)을 ViewModel로 옮겨 Activity가 순수하게
 * 네비게이션/메뉴 연결 같은 View 관심사만 담당하도록 했다.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenManager = TokenManager(application)

    /** 로그인한 사용자가 관리자인지 여부 — Activity가 이 값으로 네비게이션 그래프/메뉴를 분기한다. */
    val isAdmin: Boolean get() = tokenManager.isAdmin

    /** 로그아웃 처리: 저장된 토큰을 모두 삭제한다. 화면 전환(LoginActivity로 이동)은 Activity가 담당한다. */
    fun logout() {
        tokenManager.clear()
    }
}
```

변경 후 (전체 파일):

```kotlin
// 메인 화면(하단 네비게이션 컨테이너)의 역할 판별/로그아웃을 담당하는 ViewModel

package com.training.monitor.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.training.monitor.data.local.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * [MainActivity]의 ViewModel.
 *
 * [MVVM 변경] MainActivity는 네트워크 호출이나 로딩 상태가 없는 "껍데기" 화면이라
 * LiveData로 흘려보낼 비동기 상태는 없지만, TokenManager를 직접 들고 있던 책임
 * (관리자 여부 판별, 로그아웃 시 토큰 삭제)을 ViewModel로 옮겨 Activity가 순수하게
 * 네비게이션/메뉴 연결 같은 View 관심사만 담당하도록 했다.
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val tokenManager: TokenManager
) : AndroidViewModel(application) {

    /** 로그인한 사용자가 관리자인지 여부 — Activity가 이 값으로 네비게이션 그래프/메뉴를 분기한다. */
    val isAdmin: Boolean get() = tokenManager.isAdmin

    /** 로그아웃 처리: 저장된 토큰을 모두 삭제한다. 화면 전환(LoginActivity로 이동)은 Activity가 담당한다. */
    fun logout() {
        tokenManager.clear()
    }
}
```

- [ ] **Step 2: `MainActivity.kt`에 `@AndroidEntryPoint` 추가**

변경 전:

```kotlin
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.training.monitor.R
import com.training.monitor.databinding.ActivityMainBinding
import com.training.monitor.ui.login.LoginActivity
import com.training.monitor.ui.password.ChangePasswordActivity
import com.training.monitor.ui.photo.PhotoViewActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
```

변경 후 (`dagger.hilt.android.AndroidEntryPoint` import 한 줄 추가):

```kotlin
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.training.monitor.R
import com.training.monitor.databinding.ActivityMainBinding
import com.training.monitor.ui.login.LoginActivity
import com.training.monitor.ui.password.ChangePasswordActivity
import com.training.monitor.ui.photo.PhotoViewActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
```

클래스 선언 변경 전:

```kotlin
class MainActivity : AppCompatActivity() {
```

변경 후:

```kotlin
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
```

- [ ] **Step 3: 빌드 검증**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/java/com/training/monitor/ui/main/MainViewModel.kt app/src/main/java/com/training/monitor/ui/main/MainActivity.kt
git commit -m "refactor: Main 화면을 Hilt DI로 전환"
```

---

## Task 6: ChangePassword 화면 전환 (`ChangePasswordActivity` + `ChangePasswordViewModel`)

**Files:**
- Modify: `app/src/main/java/com/training/monitor/ui/password/ChangePasswordViewModel.kt`
- Modify: `app/src/main/java/com/training/monitor/ui/password/ChangePasswordActivity.kt`

**Interfaces:**
- Consumes: `ApiService`(Task 3), `TokenManager`(Task 2) — Task 4의 `LoginViewModel`과 동일하게 둘 다 필요하다.

- [ ] **Step 1: `ChangePasswordViewModel.kt` 수정**

변경 전 (import 블록 + 클래스 선언):

```kotlin
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.training.monitor.data.api.RetrofitClient
import com.training.monitor.data.local.TokenManager
import com.training.monitor.data.model.ChangePasswordRequest
import kotlinx.coroutines.launch

/**
 * [ChangePasswordActivity]의 ViewModel.
 *
 * [LoginViewModel]과 동일한 패턴: 입력값 검증 + API 호출 + 로딩/결과 상태 보관을 여기서
 * 전담하고, Activity는 그 상태를 관찰(observe)해서 화면만 갱신한다.
 */
class ChangePasswordViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenManager = TokenManager(application)
```

변경 후:

```kotlin
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.training.monitor.data.api.ApiService
import com.training.monitor.data.local.TokenManager
import com.training.monitor.data.model.ChangePasswordRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [ChangePasswordActivity]의 ViewModel.
 *
 * [LoginViewModel]과 동일한 패턴: 입력값 검증 + API 호출 + 로딩/결과 상태 보관을 여기서
 * 전담하고, Activity는 그 상태를 관찰(observe)해서 화면만 갱신한다.
 */
@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    application: Application,
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) : AndroidViewModel(application) {
```

`changePassword()` 함수 안의 다음 두 줄:

```kotlin
        _loading.value = true
        val api = RetrofitClient.create(getApplication())

        viewModelScope.launch {
            try {
                val response = api.changePassword(ChangePasswordRequest(currentPassword, newPassword))
```

을 다음으로 교체:

```kotlin
        _loading.value = true

        viewModelScope.launch {
            try {
                val response = apiService.changePassword(ChangePasswordRequest(currentPassword, newPassword))
```

그 외(`onToastMessageShown()`, `onChangeHandled()`, `logout()` 등)는 바꾸지 않는다.

- [ ] **Step 2: `ChangePasswordActivity.kt`에 `@AndroidEntryPoint` 추가**

변경 전:

```kotlin
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.training.monitor.databinding.ActivityChangePasswordBinding
import com.training.monitor.ui.login.LoginActivity
import com.training.monitor.ui.main.MainActivity
```

변경 후:

```kotlin
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.training.monitor.databinding.ActivityChangePasswordBinding
import com.training.monitor.ui.login.LoginActivity
import com.training.monitor.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint
```

클래스 선언 변경 전:

```kotlin
class ChangePasswordActivity : AppCompatActivity() {
```

변경 후:

```kotlin
@AndroidEntryPoint
class ChangePasswordActivity : AppCompatActivity() {
```

- [ ] **Step 3: 빌드 검증**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/java/com/training/monitor/ui/password/ChangePasswordViewModel.kt app/src/main/java/com/training/monitor/ui/password/ChangePasswordActivity.kt
git commit -m "refactor: ChangePassword 화면을 Hilt DI로 전환"
```

---

## Task 7: Member 화면 전환 (`MemberListFragment` + `MemberListViewModel` + `MemberAdapterViewModel`)

**Files:**
- Modify: `app/src/main/java/com/training/monitor/ui/member/MemberListViewModel.kt`
- Modify: `app/src/main/java/com/training/monitor/ui/member/MemberAdapterViewModel.kt`
- Modify: `app/src/main/java/com/training/monitor/ui/member/MemberListFragment.kt`

**Interfaces:**
- Consumes: `ApiService`(Task 3).

- [ ] **Step 1: `MemberListViewModel.kt` 수정**

변경 전 (import 블록 + 클래스 선언):

```kotlin
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.training.monitor.data.api.RetrofitClient
import com.training.monitor.data.model.CreateMemberRequest
import com.training.monitor.data.model.MemberDto
import com.training.monitor.data.model.ResetPasswordRequest
import com.training.monitor.data.model.UnitDto
import com.training.monitor.data.model.UpdateMemberRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
```

변경 후:

```kotlin
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.training.monitor.data.api.ApiService
import com.training.monitor.data.model.CreateMemberRequest
import com.training.monitor.data.model.MemberDto
import com.training.monitor.data.model.ResetPasswordRequest
import com.training.monitor.data.model.UnitDto
import com.training.monitor.data.model.UpdateMemberRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
```

클래스 선언 변경 전:

```kotlin
class MemberListViewModel(application: Application) : AndroidViewModel(application) {
```

변경 후:

```kotlin
@HiltViewModel
class MemberListViewModel @Inject constructor(
    application: Application,
    private val apiService: ApiService
) : AndroidViewModel(application) {
```

다음 4곳에서 `val api = RetrofitClient.create(getApplication())` 줄을 삭제하고, 바로 다음 줄의 `api.xxx(...)` 호출을 `apiService.xxx(...)`로 바꾼다 (그 외 로직은 바꾸지 않는다):

1. `loadMembers()`:
```kotlin
    fun loadMembers() {
        // [MVVM 변경] Fragment의 생명주기에 묶인 lifecycleScope 대신 ViewModel의 생명주기에 묶인
        // viewModelScope를 사용한다. 화면 회전 등으로 Fragment의 View가 재생성되어도 ViewModel은
        // 살아있으므로, 이미 시작된 네트워크 요청이 중간에 끊기지 않는다.
        viewModelScope.launch {
            try {
                val response = apiService.getMembers()
```

2. `loadRanks()`:
```kotlin
    fun loadRanks() {
        viewModelScope.launch {
            try {
                val response = apiService.getRanks()
```

3. `loadUnits()`:
```kotlin
    fun loadUnits() {
        viewModelScope.launch {
            try {
                val response = apiService.getUnits()
```

4. `createMember()`:
```kotlin
    fun createMember(req: CreateMemberRequest, photoUri: Uri? = null) {
        viewModelScope.launch {
            try {
                val photoBase64 = photoUri?.let { withContext(Dispatchers.IO) { encodePhotoBase64(it) } }
                val response = apiService.createMember(req.copy(photoBase64 = photoBase64))
```

5. `updateMember()`:
```kotlin
    fun updateMember(memberId: Long, req: UpdateMemberRequest, photoUri: Uri? = null, removePhoto: Boolean = false) {
        viewModelScope.launch {
            try {
                val photoBase64 = if (removePhoto) null
                    else photoUri?.let { withContext(Dispatchers.IO) { encodePhotoBase64(it) } }
                val response = apiService.updateMember(memberId, req.copy(photoBase64 = photoBase64, removePhoto = removePhoto))
```

6. `resetPassword()`:
```kotlin
    fun resetPassword(member: MemberDto, newPassword: String) {
        viewModelScope.launch {
            try {
                val response = apiService.resetPassword(member.id, ResetPasswordRequest(newPassword))
```

`encodePhotoBase64()`는 `getApplication<Application>().contentResolver`를 그대로 사용하므로 바꾸지 않는다(이 때문에 `AndroidViewModel` 상속을 유지).

- [ ] **Step 2: `MemberAdapterViewModel.kt` 전체 교체**

변경 전 (전체 파일):

```kotlin
// MemberAdapter와 짝을 맞추기 위한 ViewModel (프로젝트 MVVM 컨벤션 일관성 목적)

package com.training.monitor.ui.member

import androidx.lifecycle.ViewModel

/**
 * [MemberAdapter]에 대응하는 ViewModel.
 *
 * [MVVM 변경] `RecyclerView.Adapter`는 데이터를 받아 화면에 그리기만 하는 순수 View 계층
 * 컴포넌트라 원래 MVVM에서도 자체 ViewModel을 갖지 않는다 — 목록 데이터/상태는 이미
 * [MemberListViewModel]이 갖고 있고, Adapter는 [MemberListFragment]가 `submitList()`로
 * 넘겨주는 값을 그리기만 한다. 이 클래스는 실제로 보관하는 상태가 없으며, "ui 폴더의 모든
 * 화면 컴포넌트에 대응하는 ViewModel을 둔다"는 프로젝트 컨벤션을 일관되게 유지하기 위한
 * 목적으로만 존재한다.
 */
class MemberAdapterViewModel : ViewModel()
```

변경 후 (전체 파일):

```kotlin
// MemberAdapter와 짝을 맞추기 위한 ViewModel (프로젝트 MVVM 컨벤션 일관성 목적)

package com.training.monitor.ui.member

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * [MemberAdapter]에 대응하는 ViewModel.
 *
 * [MVVM 변경] `RecyclerView.Adapter`는 데이터를 받아 화면에 그리기만 하는 순수 View 계층
 * 컴포넌트라 원래 MVVM에서도 자체 ViewModel을 갖지 않는다 — 목록 데이터/상태는 이미
 * [MemberListViewModel]이 갖고 있고, Adapter는 [MemberListFragment]가 `submitList()`로
 * 넘겨주는 값을 그리기만 한다. 이 클래스는 실제로 보관하는 상태가 없으며, "ui 폴더의 모든
 * 화면 컴포넌트에 대응하는 ViewModel을 둔다"는 프로젝트 컨벤션을 일관되게 유지하기 위한
 * 목적으로만 존재한다.
 */
@HiltViewModel
class MemberAdapterViewModel @Inject constructor() : ViewModel()
```

- [ ] **Step 3: `MemberListFragment.kt`에 `@AndroidEntryPoint` 추가**

변경 전 (import 블록 마지막 줄 부근):

```kotlin
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import java.io.File
```

변경 후:

```kotlin
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
```

클래스 선언 변경 전:

```kotlin
class MemberListFragment : Fragment() {
```

변경 후:

```kotlin
@AndroidEntryPoint
class MemberListFragment : Fragment() {
```

- [ ] **Step 4: 빌드 검증**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/training/monitor/ui/member/MemberListViewModel.kt app/src/main/java/com/training/monitor/ui/member/MemberAdapterViewModel.kt app/src/main/java/com/training/monitor/ui/member/MemberListFragment.kt
git commit -m "refactor: Member 화면을 Hilt DI로 전환"
```

---

## Task 8: RecordInput 화면 전환 (`RecordInputFragment` + `RecordInputViewModel`)

**Files:**
- Modify: `app/src/main/java/com/training/monitor/ui/record/RecordInputViewModel.kt`
- Modify: `app/src/main/java/com/training/monitor/ui/record/RecordInputFragment.kt`

**Interfaces:**
- Consumes: `ApiService`(Task 3).

- [ ] **Step 1: `RecordInputViewModel.kt` 수정**

변경 전 (import 블록 + 클래스 선언):

```kotlin
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.training.monitor.data.api.RetrofitClient
import com.training.monitor.data.model.GradeCriteriaDto
import com.training.monitor.data.model.MemberDto
import com.training.monitor.data.model.RecordRequest
import com.training.monitor.data.model.SessionDto
import com.training.monitor.data.model.SessionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
```

변경 후:

```kotlin
import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.training.monitor.data.api.ApiService
import com.training.monitor.data.model.GradeCriteriaDto
import com.training.monitor.data.model.MemberDto
import com.training.monitor.data.model.RecordRequest
import com.training.monitor.data.model.SessionDto
import com.training.monitor.data.model.SessionRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
```

클래스 선언 변경 전:

```kotlin
class RecordInputViewModel(application: Application) : AndroidViewModel(application) {
```

변경 후:

```kotlin
@HiltViewModel
class RecordInputViewModel @Inject constructor(
    application: Application,
    private val apiService: ApiService
) : AndroidViewModel(application) {
```

다음 5곳에서 `val api = RetrofitClient.create(getApplication())` 줄을 삭제하고, 바로 다음 줄의 `api.xxx(...)` 호출을 `apiService.xxx(...)`로 바꾼다:

1. `loadSessions()`:
```kotlin
    fun loadSessions() {
        viewModelScope.launch {
            try {
                val response = apiService.getSessions()
```

2. `loadMembers()`:
```kotlin
    fun loadMembers() {
        viewModelScope.launch {
            try {
                val response = apiService.getMembers()
```

3. `createSession()`:
```kotlin
    fun createSession(measuredAt: String, location: String?, note: String?) {
        val req = SessionRequest(unitId = null, measuredAt = measuredAt, location = location, note = note)
        viewModelScope.launch {
            try {
                val response = apiService.createSession(req)
```

4. `loadGradeCriteria()`:
```kotlin
    fun loadGradeCriteria(categoryId: Long, userId: Long) {
        viewModelScope.launch {
            try {
                val response = apiService.gradeCriteria(categoryId, userId)
```

5. `saveRecord()`:
```kotlin
    fun saveRecord(sessionId: Long, userId: Long, categoryId: Long, value: Double, note: String?, photoFile: File? = null) {
        viewModelScope.launch {
            try {
                val photoBase64 = photoFile?.let { withContext(Dispatchers.IO) { encodePhotoBase64(it) } }
                val req = RecordRequest(sessionId, userId, categoryId, value, note, photoBase64)
                val response = apiService.createRecord(req)
```

`previewGrade()`(순수 함수), `encodePhotoBase64()`(파일 디코드만 사용), `onToastMessageShown()`, `onSaveHandled()`는 바꾸지 않는다.

- [ ] **Step 2: `RecordInputFragment.kt`에 `@AndroidEntryPoint` 추가**

변경 전:

```kotlin
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.training.monitor.R
import com.training.monitor.databinding.DialogCreateSessionBinding
import com.training.monitor.databinding.FragmentRecordInputBinding
import com.training.monitor.ui.photo.PhotoViewActivity
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
```

변경 후:

```kotlin
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.training.monitor.R
import com.training.monitor.databinding.DialogCreateSessionBinding
import com.training.monitor.databinding.FragmentRecordInputBinding
import com.training.monitor.ui.photo.PhotoViewActivity
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
```

클래스 선언 변경 전:

```kotlin
class RecordInputFragment : Fragment() {
```

변경 후:

```kotlin
@AndroidEntryPoint
class RecordInputFragment : Fragment() {
```

- [ ] **Step 3: 빌드 검증**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/java/com/training/monitor/ui/record/RecordInputViewModel.kt app/src/main/java/com/training/monitor/ui/record/RecordInputFragment.kt
git commit -m "refactor: RecordInput 화면을 Hilt DI로 전환"
```

---

## Task 9: RecordListView 화면 전환 (`RecordListViewFragment` + `RecordListViewViewModel`)

**Files:**
- Modify: `app/src/main/java/com/training/monitor/ui/record/RecordListViewViewModel.kt`
- Modify: `app/src/main/java/com/training/monitor/ui/record/RecordListViewFragment.kt`

**Interfaces:**
- Consumes: `ApiService`(Task 3).

- [ ] **Step 1: `RecordListViewViewModel.kt` 수정**

변경 전 (import 블록 + 클래스 선언):

```kotlin
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.training.monitor.data.api.RetrofitClient
import com.training.monitor.data.model.RecordDto
import kotlinx.coroutines.launch

/**
 * [RecordListViewFragment]의 ViewModel. 특정 대원(userId)의 전체 측정 기록 목록을
 * 서버에서 불러와 화면에 노출할 상태([records], [toastMessage])로 보관한다.
 * 실제 기록 목록을 그리는 일(RecyclerView 갱신)은 Fragment의 책임으로 남겨둔다.
 */
class RecordListViewViewModel(application: Application) : AndroidViewModel(application) {
```

변경 후:

```kotlin
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.training.monitor.data.api.ApiService
import com.training.monitor.data.model.RecordDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [RecordListViewFragment]의 ViewModel. 특정 대원(userId)의 전체 측정 기록 목록을
 * 서버에서 불러와 화면에 노출할 상태([records], [toastMessage])로 보관한다.
 * 실제 기록 목록을 그리는 일(RecyclerView 갱신)은 Fragment의 책임으로 남겨둔다.
 */
@HiltViewModel
class RecordListViewViewModel @Inject constructor(
    application: Application,
    private val apiService: ApiService
) : AndroidViewModel(application) {
```

다음 3곳에서 `val api = RetrofitClient.create(getApplication())` 줄을 삭제하고, `api.xxx(...)` 호출을 `apiService.xxx(...)`로 바꾼다:

1. `loadRecords()`:
```kotlin
    fun loadRecords(userId: Long) {
        viewModelScope.launch {
            try {
                val response = apiService.userRecords(userId)
```

2. `deleteRecord()`:
```kotlin
    fun deleteRecord(recordId: Long, userId: Long) {
        viewModelScope.launch {
            try {
                val response = apiService.deleteRecord(recordId)
```

3. `deleteAllRecords()`:
```kotlin
    fun deleteAllRecords(userId: Long) {
        viewModelScope.launch {
            try {
                val response = apiService.deleteAllUserRecords(userId)
```

`onToastMessageShown()`은 바꾸지 않는다.

- [ ] **Step 2: `RecordListViewFragment.kt`에 `@AndroidEntryPoint` 추가**

변경 전:

```kotlin
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.training.monitor.data.model.RecordDto
import com.training.monitor.databinding.FragmentRecordListViewBinding
import com.training.monitor.ui.photo.PhotoViewActivity
```

변경 후:

```kotlin
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.training.monitor.data.model.RecordDto
import com.training.monitor.databinding.FragmentRecordListViewBinding
import com.training.monitor.ui.photo.PhotoViewActivity
import dagger.hilt.android.AndroidEntryPoint
```

클래스 선언 변경 전:

```kotlin
class RecordListViewFragment : Fragment() {
```

변경 후:

```kotlin
@AndroidEntryPoint
class RecordListViewFragment : Fragment() {
```

- [ ] **Step 3: 빌드 검증**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/java/com/training/monitor/ui/record/RecordListViewViewModel.kt app/src/main/java/com/training/monitor/ui/record/RecordListViewFragment.kt
git commit -m "refactor: RecordListView 화면을 Hilt DI로 전환"
```

---

## Task 10: MyRecord 화면 전환 (`MyRecordFragment` + `MyRecordViewModel`)

**Files:**
- Modify: `app/src/main/java/com/training/monitor/ui/record/MyRecordViewModel.kt`
- Modify: `app/src/main/java/com/training/monitor/ui/record/MyRecordFragment.kt`

**Interfaces:**
- Consumes: `ApiService`(Task 3).

- [ ] **Step 1: `MyRecordViewModel.kt` 수정**

변경 전 (import 블록 + 클래스 선언):

```kotlin
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.training.monitor.data.api.RetrofitClient
import com.training.monitor.data.model.RecordDto
import com.training.monitor.data.model.TrendPoint
import kotlinx.coroutines.launch

/**
 * [MyRecordFragment]의 ViewModel.
 *
 * [MVVM 변경] 기존에는 Fragment가 본인 기록/추이 그래프 API를 직접 호출하고, 받은 데이터를
 * 곧바로 TextView/차트에 그렸다. ViewModel은 "조회한 데이터"까지만 LiveData로 보관하고,
 * 그 데이터를 화면에 어떻게 그릴지(요약 텍스트 포맷, 차트 Entry 변환/스타일)는 View의
 * 책임으로 남겨 Fragment에 그대로 둔다.
 */
class MyRecordViewModel(application: Application) : AndroidViewModel(application) {
```

변경 후:

```kotlin
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.training.monitor.data.api.ApiService
import com.training.monitor.data.model.RecordDto
import com.training.monitor.data.model.TrendPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [MyRecordFragment]의 ViewModel.
 *
 * [MVVM 변경] 기존에는 Fragment가 본인 기록/추이 그래프 API를 직접 호출하고, 받은 데이터를
 * 곧바로 TextView/차트에 그렸다. ViewModel은 "조회한 데이터"까지만 LiveData로 보관하고,
 * 그 데이터를 화면에 어떻게 그릴지(요약 텍스트 포맷, 차트 Entry 변환/스타일)는 View의
 * 책임으로 남겨 Fragment에 그대로 둔다.
 */
@HiltViewModel
class MyRecordViewModel @Inject constructor(
    application: Application,
    private val apiService: ApiService
) : AndroidViewModel(application) {
```

다음 4곳에서 `val api = RetrofitClient.create(getApplication())` 줄을 삭제하고, `api.xxx(...)` 호출을 `apiService.xxx(...)`로 바꾼다:

1. `loadRecords()`:
```kotlin
    fun loadRecords() {
        viewModelScope.launch {
            try {
                val response = apiService.myRecords()
```

2. `loadTrend()`:
```kotlin
    fun loadTrend(categoryId: Long) {
        viewModelScope.launch {
            try {
                val response = apiService.trend(categoryId = categoryId)
```

3. `deleteAllMyRecords()`:
```kotlin
    fun deleteAllMyRecords() {
        viewModelScope.launch {
            try {
                val response = apiService.deleteMyRecords()
```

4. `deleteMyRecordsByCategory()`:
```kotlin
    fun deleteMyRecordsByCategory(categoryId: Long) {
        viewModelScope.launch {
            try {
                val response = apiService.deleteMyRecordsByCategory(categoryId)
```

`onToastMessageShown()`은 바꾸지 않는다.

- [ ] **Step 2: `MyRecordFragment.kt`에 `@AndroidEntryPoint` 추가**

변경 전:

```kotlin
import com.google.android.material.tabs.TabLayout
import com.training.monitor.data.model.RecordDto
import com.training.monitor.data.model.TrendPoint
import com.training.monitor.databinding.FragmentMyRecordBinding
import com.training.monitor.ui.photo.PhotoViewActivity
```

변경 후:

```kotlin
import com.google.android.material.tabs.TabLayout
import com.training.monitor.data.model.RecordDto
import com.training.monitor.data.model.TrendPoint
import com.training.monitor.databinding.FragmentMyRecordBinding
import com.training.monitor.ui.photo.PhotoViewActivity
import dagger.hilt.android.AndroidEntryPoint
```

클래스 선언 변경 전:

```kotlin
class MyRecordFragment : Fragment() {
```

변경 후:

```kotlin
@AndroidEntryPoint
class MyRecordFragment : Fragment() {
```

- [ ] **Step 3: 빌드 검증**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/java/com/training/monitor/ui/record/MyRecordViewModel.kt app/src/main/java/com/training/monitor/ui/record/MyRecordFragment.kt
git commit -m "refactor: MyRecord 화면을 Hilt DI로 전환"
```

---

## Task 11: Stats 화면 전환 (`StatsFragment` + `StatsViewModel`)

**Files:**
- Modify: `app/src/main/java/com/training/monitor/ui/stats/StatsViewModel.kt`
- Modify: `app/src/main/java/com/training/monitor/ui/stats/StatsFragment.kt`

**Interfaces:**
- Consumes: `ApiService`(Task 3).

- [ ] **Step 1: `StatsViewModel.kt` 수정**

변경 전 (import 블록 + 클래스 선언):

```kotlin
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.training.monitor.data.api.RetrofitClient
import com.training.monitor.data.model.UnitDto
import com.training.monitor.data.model.UnitStatsDto
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * [StatsFragment]의 ViewModel.
 *
 * [MVVM 변경] 기존에는 Fragment가 조회 기간(dateFrom/dateTo)을 자신의 프로퍼티로 들고 있다가,
 * 통계 API 응답을 받는 즉시 두 차트를 직접 그렸다. ViewModel은 "조회 기간"과 "조회 결과"를
 * 상태로 보관하고, 그 데이터를 실제 PieChart/BarChart로 그리는 일(MPAndroidChart API 호출)은
 * View의 책임이므로 Fragment에 남겨둔다.
 */
class StatsViewModel(application: Application) : AndroidViewModel(application) {
```

변경 후:

```kotlin
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.training.monitor.data.api.ApiService
import com.training.monitor.data.model.UnitDto
import com.training.monitor.data.model.UnitStatsDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * [StatsFragment]의 ViewModel.
 *
 * [MVVM 변경] 기존에는 Fragment가 조회 기간(dateFrom/dateTo)을 자신의 프로퍼티로 들고 있다가,
 * 통계 API 응답을 받는 즉시 두 차트를 직접 그렸다. ViewModel은 "조회 기간"과 "조회 결과"를
 * 상태로 보관하고, 그 데이터를 실제 PieChart/BarChart로 그리는 일(MPAndroidChart API 호출)은
 * View의 책임이므로 Fragment에 남겨둔다.
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    application: Application,
    private val apiService: ApiService
) : AndroidViewModel(application) {
```

다음 2곳에서 `val api = RetrofitClient.create(getApplication())` 줄을 삭제하고, `api.xxx(...)` 호출을 `apiService.xxx(...)`로 바꾼다:

1. `loadUnits()`:
```kotlin
    private fun loadUnits() {
        viewModelScope.launch {
            try {
                val response = apiService.getUnits()
```

2. `loadStats()`:
```kotlin
    fun loadStats(unitId: Long) {
        val from = _dateFrom.value ?: return
        val to = _dateTo.value ?: return
        viewModelScope.launch {
            try {
                val response = apiService.unitStats(unitId, from.format(fmt), to.format(fmt))
```

`init { loadUnits() }`, `setLevel()`, `setDateFrom()`, `setDateTo()`, `onToastMessageShown()`은 바꾸지 않는다.

- [ ] **Step 2: `StatsFragment.kt`에 `@AndroidEntryPoint` 추가**

변경 전:

```kotlin
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.github.mikephil.charting.data.*
import com.training.monitor.R
import com.training.monitor.databinding.FragmentStatsBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
```

변경 후:

```kotlin
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.github.mikephil.charting.data.*
import com.training.monitor.R
import com.training.monitor.databinding.FragmentStatsBinding
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
```

클래스 선언 변경 전:

```kotlin
class StatsFragment : Fragment() {
```

변경 후:

```kotlin
@AndroidEntryPoint
class StatsFragment : Fragment() {
```

- [ ] **Step 3: 빌드 검증**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/java/com/training/monitor/ui/stats/StatsViewModel.kt app/src/main/java/com/training/monitor/ui/stats/StatsFragment.kt
git commit -m "refactor: Stats 화면을 Hilt DI로 전환"
```

---

## Task 12: `PhotoViewActivity` 전환 + `RetrofitClient.kt` 삭제 + 최종 검증

이 시점에서 `PhotoViewActivity`가 `RetrofitClient.create(...)`를 호출하는 **마지막 남은 곳**이다. 이 태스크에서 필드 주입으로 바꾼 뒤 `RetrofitClient.kt`를 삭제한다.

**Files:**
- Modify: `app/src/main/java/com/training/monitor/ui/photo/PhotoViewActivity.kt`
- Delete: `app/src/main/java/com/training/monitor/data/api/RetrofitClient.kt`

**Interfaces:**
- Consumes: `ApiService`(Task 3), 필드 주입(`@Inject lateinit var`) — 이 화면은 ViewModel이 없는 유일한 케이스라 생성자 주입이 아니라 필드 주입을 쓴다.

- [ ] **Step 1: `PhotoViewActivity.kt` 수정**

변경 전 (import 블록 + 클래스 선언):

```kotlin
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.training.monitor.data.api.RetrofitClient
import com.training.monitor.databinding.ActivityPhotoViewBinding
import kotlinx.coroutines.launch
```

변경 후:

```kotlin
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.training.monitor.data.api.ApiService
import com.training.monitor.databinding.ActivityPhotoViewBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
```

클래스 선언 변경 전:

```kotlin
class PhotoViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoViewBinding
```

변경 후:

```kotlin
@AndroidEntryPoint
class PhotoViewActivity : AppCompatActivity() {

    @Inject lateinit var apiService: ApiService

    private lateinit var binding: ActivityPhotoViewBinding
```

`loadPhotoFromServer()` 안의 호출부 변경 전:

```kotlin
                // RetrofitClient.create(context): JWT 토큰 자동 첨부 + 401 자동 갱신이
                // 내장된 ApiService 구현체를 매번 새로 만들어 쓴다 (다른 화면들과 동일한 패턴).
                val response = RetrofitClient.create(this@PhotoViewActivity).recordPhoto(recordId)
```

변경 후:

```kotlin
                // apiService: Hilt가 @Inject lateinit var로 주입한 싱글톤(NetworkModule 제공).
                val response = apiService.recordPhoto(recordId)
```

- [ ] **Step 2: `RetrofitClient.kt` 삭제**

```bash
rm app/src/main/java/com/training/monitor/data/api/RetrofitClient.kt
```

삭제 전, 혹시 남은 참조가 없는지 확인:

Run: `grep -rn "RetrofitClient" app/src/main/java`
Expected: 결과 없음 (0 matches).

- [ ] **Step 3: 빌드 검증**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: 커밋**

```bash
git add app/src/main/java/com/training/monitor/ui/photo/PhotoViewActivity.kt
git rm app/src/main/java/com/training/monitor/data/api/RetrofitClient.kt
git commit -m "refactor: PhotoViewActivity를 Hilt DI로 전환하고 RetrofitClient 제거"
```

- [ ] **Step 5: 최종 수동 회귀 테스트 (에뮬레이터)**

Hilt 전환 자체는 순수 리팩터링이라 기존에 검증된 기능 동작이 전부 그대로 유지되어야 한다. 에뮬레이터에 `app-debug.apk`를 설치하고 다음을 전부 확인한다 (스펙의 "테스트/검증 계획" 절과 동일):

1. 로그인 — 관리자 계정 1회, 대원 계정 1회.
2. 관리자: 대원 목록 조회 → 대원 추가(사진 포함) → 정보 수정 → 비밀번호 재설정.
3. 관리자: 기록 입력(사진 포함) → 대원별 기록 조회(개별/전체 삭제).
4. 대원: 내 기록 조회(종목별/전체 삭제, 추이 그래프 X/Y축 라벨 정상 표시).
5. 관리자: 통계 화면(원형/막대 그래프).
6. **세션 만료 자동 로그아웃** — 백엔드 JWT 시크릿 교체 또는 refresh token DB 삭제로 세션을 강제 만료시킨 뒤, 다음 API 호출 시 자동으로 `LoginActivity`로 튕기는지 확인. 이 로직이 `NetworkModule`로 옮겨진 뒤에도 동일하게 동작해야 한다.

위 흐름 중 하나라도 기존과 다르게 동작하면, 리팩터링 범위를 벗어난 로직 변경이 섞인 것이므로 해당 태스크로 돌아가 원인을 찾아 수정한다 (새 기능 추가 금지 — 순수 동작 보존이 목표).
