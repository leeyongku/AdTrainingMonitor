# LoginActivity 클래스 상세 설명

`app/src/main/java/com/training/monitor/ui/login/LoginActivity.kt`

---

## 1. 개요

| 항목 | 내용 |
|---|---|
| 역할 | 앱의 로그인 화면. 군번/비밀번호를 입력받아 로그인 API를 호출하고, 성공 시 역할(관리자/대원)에 맞는 화면으로 진입시킨다. |
| 앱 진입점 여부 | O — `AndroidManifest.xml`의 `<intent-filter>`에 `MAIN`/`LAUNCHER`가 걸린 유일한 Activity. 앱을 처음 열면 항상 이 화면이 뜬다. |
| 상위 클래스 | `androidx.appcompat.app.AppCompatActivity` |
| 아키텍처 패턴 | MVVM. 이 클래스는 "화면을 그리고 입력을 받는 View" 역할만 하고, 실제 로그인 API 호출·로딩 상태·에러 메시지 보관은 [LoginViewModel](#3-loginviewmodel-과의-관계)이 전담한다. |
| 연결된 레이아웃 | `res/layout/activity_login.xml` → ViewBinding으로 `ActivityLoginBinding` 자동 생성 |
| 다음 화면 | 로그인 성공 시 `com.training.monitor.ui.main.MainActivity`로 이동 (뒤로가기로 로그인 화면에 돌아오지 못하도록 `finish()` 호출) |

---

## 2. 클래스 구조

```kotlin
class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) { ... }
    private fun goToMain() { ... }
    private fun setLoading(loading: Boolean) { ... }
}
```

### 2-1. 필드

| 필드 | 타입 | 설명 |
|---|---|---|
| `binding` | `ActivityLoginBinding` | `activity_login.xml`의 뷰들(`etMilitaryId`, `etPassword`, `btnLogin`, `progressBar` 등)에 접근하기 위한 ViewBinding 객체. `lateinit`이라 `onCreate()`에서 반드시 초기화해야 한다. |
| `viewModel` | `LoginViewModel` | `by viewModels()` 델리게이트로 생성되는 ViewModel 인스턴스. 화면 회전 등으로 Activity가 재생성돼도 같은 인스턴스가 재사용된다 (진행 중이던 로그인 요청이 끊기지 않음). |

### 2-2. 메서드

| 메서드 | 접근 제한자 | 설명 |
|---|---|---|
| `onCreate(savedInstanceState: Bundle?)` | override | 화면 생성 시 1회 호출. 바인딩 초기화, 자동 로그인 체크, 클릭 리스너 연결, LiveData 관찰(observe) 등록을 모두 이 안에서 수행한다. |
| `goToMain()` | private | `MainActivity`로 전환하고 `finish()`로 현재 Activity를 백스택에서 제거한다. |
| `setLoading(loading: Boolean)` | private | 로그인 버튼 활성/비활성과 `ProgressBar` 표시 여부를 토글해 중복 클릭·중복 요청을 막는다. |

---

## 3. `LoginViewModel`과의 관계

파일: `app/src/main/java/com/training/monitor/ui/login/LoginViewModel.kt`
(`AndroidViewModel`을 상속 — `RetrofitClient.create()`/`TokenManager`가 `Context`를 필요로 하기 때문에 `getApplication()`으로 Application Context를 쓸 수 있는 `AndroidViewModel`을 사용한다.)

| ViewModel이 노출하는 것 | 타입 | LoginActivity의 사용 방식 |
|---|---|---|
| `isAlreadyLoggedIn` | `Boolean` (프로퍼티) | `onCreate()` 진입 직후 한 번 읽어서, 이미 유효한 토큰이 있으면 입력 화면을 보여주지 않고 바로 `goToMain()`. |
| `loading` | `LiveData<Boolean>` | `observe`해서 `setLoading()` 호출 — 로그인 요청 중에는 버튼 비활성화 + ProgressBar 표시. |
| `toastMessage` | `LiveData<String?>` | `observe`해서 값이 있으면 `Toast`로 띄운 뒤 `onToastMessageShown()`을 호출해 값을 비운다 (비우지 않으면 화면 회전 시 같은 메시지가 다시 뜬다). |
| `loginSuccess` | `LiveData<Boolean>` | `observe`해서 `true`가 되면 `onLoginHandled()`로 값을 되돌리고 `goToMain()` 호출. |
| `login(militaryId, password)` | 함수 | 로그인 버튼 클릭 시 두 입력값을 그대로 넘긴다. 입력값 검증(빈 값 체크), API 호출, 토큰 저장까지 전부 ViewModel 내부에서 처리된다. |
| `onToastMessageShown()` | 함수 | Toast를 보여준 직후 Activity가 호출 (1회성 이벤트 소비 처리). |
| `onLoginHandled()` | 함수 | 화면 전환을 마친 직후 Activity가 호출 (1회성 이벤트 소비 처리). |

`LoginViewModel.login()` 내부에서 실제로 일어나는 일 (참고용):

1. `militaryId`/`password` 둘 중 하나라도 비어있으면 `"군번과 비밀번호를 입력하세요."` Toast만 띄우고 종료.
2. `_loading.value = true`
3. `viewModelScope.launch { }` 안에서 `RetrofitClient.create(...).login(LoginRequest(...))` 호출 (`POST /api/auth/login`).
4. 성공(`response.isSuccessful`)하면 응답 바디(`TokenResponse`)의 `accessToken`/`refreshToken`/`role`을 `TokenManager`에 저장하고 `_loginSuccess.value = true`.
5. 실패(401 등)하면 `"군번 또는 비밀번호가 올바르지 않습니다."` Toast.
6. 예외(네트워크 오류 등) 발생 시 `"서버 연결 실패: ..."` Toast.
7. `finally`에서 `_loading.value = false` (성공/실패 모두).

---

## 4. 화면 흐름 (시퀀스)

```
[앱 실행]
    │
    ▼
LoginActivity.onCreate()
    │
    ├─ viewModel.isAlreadyLoggedIn == true ?  ──yes──▶ goToMain() ──▶ (LoginActivity 종료)
    │            │no
    │            ▼
    │   로그인 폼 표시, 사용자 입력 대기
    │
    ├─ btnLogin 클릭
    │       │
    │       ▼
    │   viewModel.login(militaryId, password)
    │       │
    │       ├─ 입력값 검증 실패 → toastMessage 발행
    │       │
    │       └─ 검증 통과 → loading=true → POST /api/auth/login
    │               │
    │               ├─ 성공 → 토큰 저장 → loginSuccess=true → loading=false
    │               │             │
    │               │             ▼
    │               │      LoginActivity가 observe로 감지 → goToMain()
    │               │
    │               └─ 실패/예외 → toastMessage 발행 → loading=false
    │
    └─ (observe들이 loading/toastMessage 값 변화를 계속 화면에 반영)
```

핵심은 **Activity는 어떤 분기에서도 직접 `Toast.makeText()`나 API 호출을 하지 않는다**는 점이다. 모든 분기가 ViewModel의 LiveData 값 변화로 귀결되고, Activity는 그 값들을 `observe`해서 화면(Toast/ProgressBar/화면 전환)만 갱신한다.

---

## 5. 연관 파일

| 파일 | 역할 |
|---|---|
| `res/layout/activity_login.xml` | 로그인 화면 레이아웃. `etMilitaryId`(군번 입력), `etPassword`(비밀번호 입력, 마스킹 토글 지원), `btnLogin`(로그인 버튼), `progressBar`(로딩 표시)를 포함. `tvMsg`라는 TextView도 정의돼 있지만 현재 `LoginActivity.kt`/`LoginViewModel.kt` 어디에서도 참조하지 않는 미사용 뷰다. |
| `data/local/TokenManager.kt` | `EncryptedSharedPreferences` 기반으로 `accessToken`/`refreshToken`/`role`을 암호화 저장. `isLoggedIn`(accessToken 존재 여부)과 `isAdmin`(role == "ADMIN") 판단도 여기서 제공한다. |
| `data/api/RetrofitClient.kt` | `ApiService` 구현체 생성 + JWT 자동 첨부 + 401 시 refresh token 자동 재시도 인터셉터. |
| `data/api/ApiService.kt` | `login()`, `refresh()`, `logout()` 등 인증 관련 API 선언 (`POST /api/auth/login` 등). |
| `data/model/Dtos.kt` | `LoginRequest`(military_id, password), `TokenResponse`(access_token, refresh_token, role) DTO 정의. |
| `ui/main/MainActivity.kt` | 로그인 성공 후 진입하는 다음 화면. `TokenManager.role`(ADMIN/MEMBER)에 따라 다른 네비게이션 그래프를 보여준다. |

---

## 6. 설계 메모

- **자동 로그인 스킵**: `isAlreadyLoggedIn`은 단순히 "accessToken이 로컬에 저장돼 있는지"만 확인한다 (`TokenManager.isLoggedIn`). 토큰이 서버에서 이미 만료됐는지는 확인하지 않으며, 이후 첫 API 호출에서 401을 받으면 `RetrofitClient`의 인증 인터셉터가 refresh token으로 자동 재발급을 시도한다.
- **1회성 이벤트 패턴**: `toastMessage`/`loginSuccess`는 값을 소비한 뒤 반드시 초기값(`null`/`false`)으로 되돌리는 `onXxxShown()`/`onXxxHandled()` 쌍을 갖는다. 이는 이 프로젝트의 다른 화면(`MemberListViewModel`, `RecordInputViewModel`, `StatsViewModel` 등)에서도 반복되는 표준 패턴이다.
- **에러 메시지 구분 없음**: 현재 로그인 실패는 401(인증 실패)이든 다른 4xx/5xx든 구분 없이 전부 `"군번 또는 비밀번호가 올바르지 않습니다."`로 안내한다 (네트워크 자체가 끊긴 경우만 `catch (e: Exception)`에서 별도 메시지).
- **미사용 뷰(`tvMsg`)**: 레이아웃에는 있지만 코드에서 참조하지 않는 죽은 요소. 기능에는 영향 없음.
