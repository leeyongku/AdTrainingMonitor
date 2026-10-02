# `app/src/main/java/com/training/monitor` 폴더 구성

`AdTrainingMonitor` 앱의 소스 코드(실제로는 전부 Kotlin — 안드로이드 프로젝트 관례상 폴더 이름만
`java`)를 패키지별로 정리한 문서입니다. 이 프로젝트는 전체적으로 **MVVM 패턴**을 따르며,
화면(Activity/Fragment)마다 대응하는 ViewModel을 두는 컨벤션을 일관되게 유지합니다.

> **MVVM 역할 분리**: Activity/Fragment는 "화면을 그리고 사용자 입력을 받는" View 역할만
> 하고, 서버 통신(Retrofit 호출)과 상태 보관은 그 화면에 대응하는 ViewModel이 담당합니다.
> View는 ViewModel이 노출하는 `LiveData`를 관찰(observe)해서 화면을 갱신할 뿐, 직접
> `RetrofitClient`를 호출하거나 `Toast.makeText(...)`를 호출하지 않습니다.

---

## data/ — 서버 통신 · 로컬 저장 공통 계층

화면에 종속되지 않는, 앱 전체가 공유하는 데이터 계층입니다.

| 파일 | 설명 |
|---|---|
| `data/api/ApiService.kt` | 서버 REST API 인터페이스 정의(Retrofit). 인증, 대원 관리, 기록 CRUD, 부대/계급 조회 등 모든 엔드포인트가 여기 한 곳에 모여있다 |
| `data/api/RetrofitClient.kt` | Retrofit 클라이언트 싱글톤. 매 요청에 JWT를 자동으로 붙이고, 401 응답 시 refresh token으로 자동 재발급을 시도하며, 그마저 실패하면(세션 완전 만료) `LoginActivity`로 강제 이동시키는 인터셉터를 포함한다 |
| `data/local/TokenManager.kt` | JWT 액세스/리프레시 토큰을 `EncryptedSharedPreferences`로 암호화하여 로컬에 안전하게 저장하는 매니저 |
| `data/model/Dtos.kt` | 서버 API 요청/응답 DTO(데이터 클래스) 모음. `CreateMemberRequest`, `MemberDto`, `UpdateMemberRequest`, `RecordDto`, `UnitDto` 등 서버와 주고받는 JSON 구조가 전부 여기 정의돼 있다. 서버가 `SnakeCaseNamingPolicy`를 쓰므로 각 필드에 `@SerializedName("snake_case")`가 붙어있다 |

---

## ui/login/ — 로그인 화면

| 파일 | 설명 |
|---|---|
| `LoginActivity.kt` | 로그인 화면. 군번/비밀번호를 입력받아 로그인하고, 서버가 내려준 역할(ADMIN/MEMBER)에 따라 화면을 이동시킨다 |
| `LoginViewModel.kt` | 로그인 화면의 상태와 서버 통신(`/api/auth/login`)을 담당하는 ViewModel |

## ui/main/ — 메인 화면(하단 네비게이션 컨테이너)

| 파일 | 설명 |
|---|---|
| `MainActivity.kt` | 메인 화면. 로그인한 사용자의 역할(관리자/대원)에 따라 하단 네비게이션 메뉴와 Navigation 그래프(`nav_admin`/`nav_member`)를 다르게 구성한다. 툴바의 카메라 버튼(사진 촬영)도 여기서 처리 |
| `MainViewModel.kt` | 메인 화면(하단 네비게이션 컨테이너)의 역할 판별/로그아웃을 담당하는 ViewModel |

## ui/main2/ — ⚠️ 미사용 템플릿 잔재

| 파일 | 설명 |
|---|---|
| `MainActivity2.kt` | Android Studio 프로젝트 생성 시 만들어진 기본 템플릿 화면. `AndroidManifest.xml`에 등록돼 있지 않아 실제 앱 흐름에서는 진입할 방법이 없다 |
| `MainActivity2ViewModel.kt` | `MainActivity2`와 짝을 맞추기 위한 빈 ViewModel (프로젝트 MVVM 컨벤션 일관성 유지 목적일 뿐, 실제 값을 담고 있지 않음) |

## ui/member/ — 관리자: 대원 관리 화면

| 파일 | 설명 |
|---|---|
| `MemberListFragment.kt` | 관리자 전용 화면: 대원 목록 조회, 이름/군번 검색, "대원 추가"·"정보 수정"·"비밀번호 재설정" 다이얼로그를 모두 이 클래스가 띄운다 |
| `MemberListViewModel.kt` | 대원 목록 화면의 상태(목록/계급 목록/부대 목록/토스트 메시지)와 서버 통신(등록·수정·비밀번호 재설정·사진 인코딩 포함)을 담당하는 ViewModel |
| `MemberAdapter.kt` | 대원 목록 RecyclerView 어댑터. 얼굴 사진 디코딩·표시, 행 클릭(기록 보기)·연필(정보 수정)·열쇠(비밀번호 변경) 아이콘 클릭 콜백을 갖는다 |
| `MemberAdapterViewModel.kt` | `MemberAdapter`와 짝을 맞추기 위한 빈 ViewModel (프로젝트 컨벤션 유지 목적, 실제 상태 없음) |

## ui/password/ — 비밀번호 변경 화면

| 파일 | 설명 |
|---|---|
| `ChangePasswordActivity.kt` | 비밀번호 변경 화면. 강제 진입(임시 비밀번호 교체가 필요한 경우)과 대원이 메뉴에서 자율적으로 여는 경우를 하나의 화면으로 함께 처리한다 |
| `ChangePasswordViewModel.kt` | 비밀번호 변경 화면(강제/자율 공통)의 상태와 서버 통신을 담당하는 ViewModel |

## ui/photo/ — 사진 보기 화면

| 파일 | 설명 |
|---|---|
| `PhotoViewActivity.kt` | 촬영한 사진을 전체화면으로 보여주는 화면. 로컬 `Uri`(촬영 직후 미리보기)를 받는 경로와, 서버에 이미 저장된 기록 사진을 `recordId`로 조회해 보여주는 경로 두 가지를 모두 지원한다 |

## ui/record/ — 측정 기록 입력·조회 화면

| 파일 | 설명 |
|---|---|
| `RecordInputFragment.kt` | 관리자 전용 화면: 측정 세션·대원·종목을 선택하고 측정값을 입력해 기록을 저장한다. 저장 전 등급 미리보기, 사진 촬영도 여기서 처리 |
| `RecordInputViewModel.kt` | 기록 입력 화면의 상태(세션/대원 목록, 등급 기준표)와 서버 통신, 사진 리사이즈/압축/Base64 인코딩(`encodePhotoBase64`)을 담당하는 ViewModel |
| `RecordListViewFragment.kt` | 관리자 전용 화면: 특정 대원의 전체 측정 기록(사진 포함)을 조회한다. 항목별 삭제, 전체 삭제, 닫기(대원 목록으로 복귀) 버튼을 모두 갖는다 |
| `RecordListViewViewModel.kt` | 특정 대원의 기록 목록 조회/개별 삭제/전체 삭제 서버 통신을 담당하는 ViewModel |
| `MyRecordFragment.kt` | 대원 본인 전용 화면: 종목별 탭 + 추이 꺾은선 그래프(MPAndroidChart) + 개인 최고 기록 + 측정 기록 목록을 보여준다 |
| `MyRecordViewModel.kt` | 본인 기록 조회 화면의 상태(전체 기록, 추이 데이터)와 서버 통신을 담당하는 ViewModel |
| `RecordAdapter.kt` | 측정 기록 목록 RecyclerView 어댑터. `MyRecordFragment`와 `RecordListViewFragment`가 공유한다. 사진 보기 아이콘은 항상 있고, 삭제 아이콘은 `onDeleteClick` 콜백이 있을 때만(=관리자 화면에서만) 보인다 |

## ui/stats/ — 관리자: 부대 통계 화면

| 파일 | 설명 |
|---|---|
| `StatsFragment.kt` | 관리자 전용 화면: 부대/계급/종목별 통계를 스피너로 필터링해 그래프로 보여준다 |
| `StatsViewModel.kt` | 통계 화면의 상태와 서버 통신을 담당하는 ViewModel |

---

## 패키지 구조 요약

```
com.training.monitor
├── data
│   ├── api      — Retrofit 인터페이스, 인증/토큰 자동 갱신 인터셉터
│   ├── local    — 암호화된 토큰 로컬 저장소
│   └── model    — 서버 요청/응답 DTO
└── ui
    ├── login    — 로그인
    ├── main     — 메인(하단 네비게이션 컨테이너), main2는 미사용 템플릿 잔재
    ├── member   — 관리자: 대원 목록/추가/수정/비밀번호 재설정
    ├── password — 비밀번호 변경(강제/자율 공통)
    ├── photo    — 사진 전체화면 보기
    ├── record   — 기록 입력(관리자) / 기록 조회(관리자·대원 공용 어댑터)
    └── stats    — 관리자: 부대 통계
```

## 참고

- 화면 하나마다 ViewModel 하나를 두는 컨벤션이 `Fragment`/`Activity`뿐 아니라
  `RecyclerView.Adapter`(`MemberAdapter`, `RecordAdapter`)에도 적용되어 있습니다. 다만
  Adapter는 자체 상태가 없으므로, `MemberAdapterViewModel`처럼 실제로는 비어있는 ViewModel도
  존재합니다 — 새 Adapter를 만들 때 필수 관례는 아닙니다.
- `ui/main2` 패키지(`MainActivity2`, `MainActivity2ViewModel`)는 `res/res-폴더-구성.md`에서
  언급한 `activity_main2.xml`과 마찬가지로 실제 사용되지 않는 초기 템플릿 잔재입니다.
- 서버와 주고받는 데이터 모양(DTO)이 궁금하면 `data/model/Dtos.kt` 하나만 보면 되고, 호출
  가능한 API 목록이 궁금하면 `data/api/ApiService.kt` 하나만 보면 됩니다 — 두 파일 모두
  화면별로 나뉘어 있지 않고 전체를 한 파일에 모아두는 방식을 씁니다.
