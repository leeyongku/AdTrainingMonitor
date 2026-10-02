# Hilt 의존성 주입(DI) 마이그레이션 설계

## 배경 및 목표

현재 `AdTrainingMonitor` 앱은 의존성을 수동으로 생성한다:

- `RetrofitClient`(싱글톤 `object`)가 API 호출이 필요한 **모든 지점에서** `RetrofitClient.create(context)`로 호출되며, 호출할 때마다 `TokenManager`, `OkHttpClient`, `Retrofit`, `ApiService`를 **매번 새로 생성**한다.
- ViewModel 9개(아래 목록)는 전부 `AndroidViewModel(application)`을 상속해 `getApplication()`으로 Context를 얻고, 메서드 안에서 직접 `RetrofitClient.create(getApplication())`을 호출한다.
- `PhotoViewActivity`는 ViewModel 없이 자기 자신이 직접 `RetrofitClient.create(this)`를 호출한다.

이 설계는 이 수동 생성 방식을 **Hilt**로 교체한다. 목표는 두 가지다.

1. 의존성 생성/주입을 Hilt가 관리하도록 바꾼다 (요청하신 작업 자체).
2. 부수 효과로, API 호출마다 새로 만들어지던 `ApiService`/`OkHttpClient`를 앱 전체에서 재사용하는 싱글톤으로 바꾼다.

**세션 만료 시 자동 로그아웃 로직(401 → refresh 시도 → 실패 시 토큰 삭제 + `LoginActivity`로 강제 이동)은 동작을 한 글자도 바꾸지 않는다** — 코드가 있는 위치만 `RetrofitClient.kt`에서 `di/NetworkModule.kt`로 옮겨진다.

## Global Constraints

- Hilt/KSP 버전은 구현 시점에 현재 Kotlin `2.3.21` / AGP `9.4.1`과 호환되는 조합을 Maven Central에서 직접 확인해 선택한다 (이 문서에 버전을 고정하지 않는다 — 잘못된 버전을 박아 넣으면 빌드가 깨진다).
- 어노테이션 프로세서는 **KSP**를 사용한다 (KAPT 아님).
- ViewModel은 **`AndroidViewModel` 상속을 유지**한다 (일부 ViewModel이 사진 인코딩에 `ContentResolver`를 필요로 함). `AndroidViewModel`도 Hilt의 `@HiltViewModel` + `@Inject constructor`와 완전히 호환된다 — `Application`은 Hilt가 자동으로 넘겨준다.
- `ui/main2/MainActivity2.kt`, `ui/main2/MainActivity2ViewModel.kt`는 **마이그레이션 대상에서 제외**한다. `AndroidManifest.xml`에 등록되지 않아 실제 앱 흐름에서 절대 실행되지 않는 Android Studio 기본 템플릿 잔재이기 때문이다 (기존 `docs/java-폴더-구성.md`에도 명시됨). 손대도 효과가 없고, 손대지 않아도 위험이 없다.
- 기존 동작(로그인, 세션 만료 자동 로그아웃, 대원 CRUD, 기록 CRUD, 사진 촬영/업로드, 통계)은 전부 그대로 유지되어야 한다. 이번 작업은 순수 리팩터링이며 기능 변경이 아니다.

## 1. 빌드 설정 변경

### `gradle/libs.versions.toml`
- `[versions]`에 `hilt`, `ksp` 버전 추가 (Kotlin 2.3.21과 호환되는 조합으로 구현 시 확인).
- `[libraries]`에 `hilt-android`, `hilt-compiler` 추가.
- `[plugins]`에 `hilt-android`(`com.google.dagger.hilt.android`), `ksp`(`com.google.devtools.ksp`) 추가.

### 루트 `build.gradle.kts`
- `alias(libs.plugins.hilt.android) apply false`, `alias(libs.plugins.ksp) apply false` 추가.

### `app/build.gradle.kts`
- `plugins { }`에 `alias(libs.plugins.hilt.android)`, `alias(libs.plugins.ksp)` 추가.
- `dependencies { }`에 `implementation(libs.hilt.android)`, `ksp(libs.hilt.compiler)` 추가.

## 2. `TrainingMonitorApp.kt` (신규)

**파일 위치**: `app/src/main/java/com/training/monitor/TrainingMonitorApp.kt`

```kotlin
// Hilt 의존성 그래프의 진입점 — 앱 전역에서 @Inject로 주입받을 수 있게 하는 Application
package com.training.monitor

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TrainingMonitorApp : Application()
```

`AndroidManifest.xml`의 `<application>` 태그에 `android:name=".TrainingMonitorApp"` 추가.

## 3. `TokenManager` — `@Singleton` 전환

**파일 위치**: `app/src/main/java/com/training/monitor/data/local/TokenManager.kt`

클래스 선언부만 변경:

```kotlin
@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext context: Context
) {
    // 기존 본문(accessToken/refreshToken 프로퍼티, EncryptedSharedPreferences 생성 로직) 그대로 유지
}
```

생성자 시그니처(파라미터 이름 `context`)는 기존과 동일하게 유지해 본문 코드를 바꾸지 않는다.

## 4. `di/NetworkModule.kt` (신규) — `RetrofitClient.kt` 대체

**파일 위치**: `app/src/main/java/com/training/monitor/di/NetworkModule.kt`
**삭제**: `app/src/main/java/com/training/monitor/data/api/RetrofitClient.kt`

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

## 5. ViewModel 9개 — `@HiltViewModel` 전환

대상 파일 (전부 동일 패턴 적용):

1. `ui/login/LoginViewModel.kt`
2. `ui/main/MainViewModel.kt`
3. `ui/member/MemberListViewModel.kt`
4. `ui/member/MemberAdapterViewModel.kt`
5. `ui/password/ChangePasswordViewModel.kt`
6. `ui/record/RecordInputViewModel.kt`
7. `ui/record/RecordListViewViewModel.kt`
8. `ui/record/MyRecordViewModel.kt`
9. `ui/stats/StatsViewModel.kt`

**변경 패턴** (`AndroidViewModel` 기반, 8개 — `MemberAdapterViewModel` 제외):

```kotlin
// 변경 전
class XViewModel(application: Application) : AndroidViewModel(application) {
    fun loadX() {
        val api = RetrofitClient.create(getApplication())
        viewModelScope.launch { ... }
    }
}

// 변경 후
@HiltViewModel
class XViewModel @Inject constructor(
    application: Application,
    private val apiService: ApiService
) : AndroidViewModel(application) {
    fun loadX() {
        viewModelScope.launch {
            val response = apiService.xxx(...)   // val api = RetrofitClient.create(...) 줄 삭제, apiService 직접 사용
            ...
        }
    }
}
```

모든 메서드에서 `val api = RetrofitClient.create(getApplication())` 줄을 제거하고 그 다음 줄의 `api.xxx(...)` 호출을 `apiService.xxx(...)`로 바꾼다. 그 외 로직(LiveData 갱신, 에러 처리, 토스트 메시지)은 전혀 건드리지 않는다.

**`MemberAdapterViewModel`** (플레인 `ViewModel`, API 호출 없음):

```kotlin
@HiltViewModel
class MemberAdapterViewModel @Inject constructor() : ViewModel()
```

## 6. Activity/Fragment 9개 — `@AndroidEntryPoint` 추가

대상 파일:

- `ui/login/LoginActivity.kt`
- `ui/main/MainActivity.kt`
- `ui/password/ChangePasswordActivity.kt`
- `ui/photo/PhotoViewActivity.kt`
- `ui/member/MemberListFragment.kt`
- `ui/record/RecordInputFragment.kt`
- `ui/record/RecordListViewFragment.kt`
- `ui/record/MyRecordFragment.kt`
- `ui/stats/StatsFragment.kt`

각 클래스 선언 바로 위에 `@AndroidEntryPoint` 어노테이션만 추가한다 (`dagger.hilt.android.AndroidEntryPoint` import). 클래스 본문은 변경하지 않는다 — `by viewModels()`는 대상 ViewModel이 `@HiltViewModel`이기만 하면 그대로 Hilt가 주입한 인스턴스를 돌려준다.

### `PhotoViewActivity` 추가 작업

ViewModel이 없는 유일한 케이스. `@AndroidEntryPoint` 추가 외에, 클래스 본문에 필드 주입을 추가하고 기존 `RetrofitClient.create(this@PhotoViewActivity)` 호출부를 주입받은 필드로 교체한다.

```kotlin
@AndroidEntryPoint
class PhotoViewActivity : AppCompatActivity() {
    @Inject lateinit var apiService: ApiService
    // 기존 `RetrofitClient.create(this@PhotoViewActivity).recordPhoto(recordId)` 호출을
    // `apiService.recordPhoto(recordId)`로 교체
}
```

## 7. 마이그레이션에서 제외되는 것

- `ui/main2/MainActivity2.kt`, `ui/main2/MainActivity2ViewModel.kt` — Global Constraints 참고.
- `data/model/Dtos.kt`, `data/api/ApiService.kt`(인터페이스 자체) — 변경 없음.
- 백엔드(`TrainingMonitor` ASP.NET Core 프로젝트) — 이번 작업은 안드로이드 DI 구조만 다룬다.

## 테스트 / 검증 계획

1. `./gradlew assembleDebug` 빌드 성공 (KSP 어노테이션 처리 오류 없이).
2. 에뮬레이터 설치 후 다음 흐름을 실제로 확인한다:
   - 로그인 (관리자/대원 각 1회)
   - 대원 목록 조회 → 대원 추가(사진 포함) → 정보 수정 → 비밀번호 재설정
   - 기록 입력(사진 포함) → 대원별 기록 조회(개별/전체 삭제) → 내 기록 조회(종목별/전체 삭제, 추이 그래프)
   - 통계 화면
   - **세션 만료 시 자동 로그아웃**: 기존에 검증했던 방식(백엔드 JWT 시크릿 교체 + refresh token DB 삭제)으로 재확인 — 이 로직이 `NetworkModule`로 옮겨진 뒤에도 그대로 동작해야 함
3. 위 흐름 중 하나라도 깨지면, 리팩터링 범위를 벗어난 로직 변경이 섞여 들어간 것이므로 원인을 찾아 수정한다 (새 기능 추가 금지, 순수 동작 보존이 목표).
