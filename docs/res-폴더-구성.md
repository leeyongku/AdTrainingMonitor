# `app/src/main/res` 폴더 구성

`AdTrainingMonitor` 앱의 안드로이드 리소스(`res`) 폴더에 있는 파일들을 하위 폴더별로 정리한 문서입니다.
각 파일이 무엇을 위한 것이고, 어느 Kotlin 클래스에서 사용하는지 한눈에 볼 수 있도록 정리했습니다.

---

## layout/ — 화면·다이얼로그·목록 항목 레이아웃

| 파일 | 설명 | 사용처 |
|---|---|---|
| `activity_login.xml` | 로그인 화면 (군번/비밀번호 입력 + 로그인 버튼) | `ui/login/LoginActivity.kt` |
| `activity_change_password.xml` | 비밀번호 변경 화면. 최초 로그인/관리자 재설정 직후의 강제 진입과, 대원이 메인 화면 메뉴에서 자율적으로 여는 경우를 같은 레이아웃으로 공유하고 문구·버튼 동작만 액티비티가 상황에 맞게 바꾼다 | `ui/password/ChangePasswordActivity.kt` |
| `activity_main.xml` | 메인 화면 — 상단 툴바 + 하단 네비게이션(BottomNavigationView) + Fragment 컨테이너. 관리자/대원 공용 진입점 | `ui/main/MainActivity.kt` |
| `activity_photo_view.xml` | 촬영한 사진을 전체화면(검은 배경)으로 보여주는 화면. 우측 상단 닫기 버튼으로 돌아간다 | `ui/photo/PhotoViewActivity.kt` |
| `activity_main2.xml` | ⚠️ **미사용** — Android Studio 프로젝트 생성 시의 기본 템플릿 화면("This is introl" 텍스트만 있음). `AndroidManifest.xml`에 액티비티가 등록돼 있지 않아 실제 앱 흐름에서는 진입할 방법이 없다 | `ui/main2/MainActivity2.kt` (미사용) |
| `fragment_member_list.xml` | 관리자 — 대원 목록 화면 (검색창 + RecyclerView + "대원 추가" FAB) | `ui/member/MemberListFragment.kt` |
| `fragment_record_input.xml` | 관리자 — 체력 측정 기록 입력 화면 (세션/대원/종목 선택 + 값 입력 + 사진 촬영) | `ui/record/RecordInputFragment.kt` |
| `fragment_record_list_view.xml` | 관리자 — 특정 대원의 전체 측정 기록(사진 포함) 조회 화면. 헤더에 "전체 삭제" 버튼과 닫기(X) 버튼이 있다 | `ui/record/RecordListViewFragment.kt` |
| `fragment_my_record.xml` | 대원 본인 — 종목별 탭 + 추이 꺾은선 그래프 + 측정 기록 목록 조회 화면 | `ui/record/MyRecordFragment.kt` |
| `fragment_stats.xml` | 관리자 — 부대 통계 화면 | `ui/stats/StatsFragment.kt` |
| `dialog_add_member.xml` | 관리자 — "대원 추가" 다이얼로그 입력 폼 (군번/이름/비밀번호/계급*/소속 부대*/얼굴 사진) | `ui/member/MemberListFragment.kt` |
| `dialog_edit_member.xml` | 관리자 — "대원 정보 수정" 다이얼로그 입력 폼. `dialog_add_member.xml`과 비슷하지만 군번/비밀번호 입력란은 없다 | `ui/member/MemberListFragment.kt` |
| `dialog_create_session.xml` | 관리자 — 측정 세션 추가 다이얼로그 입력 폼 (측정일/장소/메모) | `ui/record/RecordInputFragment.kt` |
| `item_member.xml` | RecyclerView 대원 목록의 행(row) 하나. 프로필 사진/이모지, 이름, 군번, 소속 부대, 수정(연필)·비밀번호 변경(열쇠) 아이콘 | `ui/member/MemberAdapter.kt` |
| `item_record.xml` | RecyclerView 측정 기록 목록의 행 하나. 등급 뱃지, 종목/날짜, 측정값, 사진 보기·삭제 아이콘 | `ui/record/RecordAdapter.kt` (`MyRecordFragment`/`RecordListViewFragment`가 공유) |

---

## menu/ — 툴바·하단 네비게이션 메뉴

| 파일 | 설명 |
|---|---|
| `menu_toolbar.xml` | 메인 화면 상단 툴바 메뉴 (관리자용) |
| `menu_toolbar_member.xml` | 대원 전용 메인 화면 상단 툴바 메뉴. `menu_toolbar.xml`과 달리 "비밀번호 변경" 항목이 추가된다 |
| `menu_admin.xml` | 관리자 하단 네비게이션 메뉴 (대원 관리 / 기록 입력 / 통계) |
| `menu_member.xml` | 대원 하단 네비게이션 메뉴 (내 기록 등) |

---

## navigation/ — Jetpack Navigation 그래프

| 파일 | 설명 |
|---|---|
| `nav_admin.xml` | 관리자 네비게이션 그래프. 시작 화면은 `memberListFragment`이며, 대원 행 클릭 시 `recordListViewFragment`로 이동하는 액션(`action_memberListFragment_to_recordListViewFragment`)을 포함 |
| `nav_member.xml` | 대원(MEMBER 역할) 네비게이션 그래프 |

---

## drawable/ — 배경/아이콘 도형 리소스

| 파일 | 설명 |
|---|---|
| `grade_badge_bg.xml` | 기록 목록의 등급 뱃지 배경 (남색 사각형, 모서리 둥글게). 실제 색상은 `RecordAdapter.gradeColor()`가 등급별로 런타임에 덮어씀 |
| `spinner_background.xml` | 계급/소속 부대 등 Spinner의 테두리 배경 (흰 배경 + 회색 테두리) |
| `ic_launcher_background.xml` | 앱 런처 아이콘의 배경 레이어 (Adaptive Icon) |
| `ic_launcher_foreground.xml` | 앱 런처 아이콘의 전경 레이어 (Adaptive Icon) |

---

## mipmap-*/ — 앱 런처 아이콘

`mipmap-mdpi` ~ `mipmap-xxxhdpi`(해상도별) 폴더에 `ic_launcher.webp`/`ic_launcher_round.webp`가 있고,
`mipmap-anydpi`에는 이를 조합하는 Adaptive Icon 정의(`ic_launcher.xml`, `ic_launcher_round.xml`)가 있습니다.
직접 수정할 일은 거의 없고, 안드로이드 스튜디오의 Image Asset 마법사로 교체하는 것이 일반적입니다.

---

## values/ , values-night/ — 공통 리소스

| 파일 | 설명 |
|---|---|
| `values/colors.xml` | 전역 색상 정의. 현재는 `black`/`white` 두 개뿐이고, 나머지 색상은 대부분 각 레이아웃 XML에 직접 하드코딩(`#1A237E` 등)되어 있다 |
| `values/strings.xml` | 전역 문자열 리소스. 현재는 `app_name`("TrainingMonitor")뿐이고, 화면 텍스트는 대부분 레이아웃 XML에 직접 하드코딩돼 있다 |
| `values/themes.xml` | 라이트 모드 앱 테마 |
| `values-night/themes.xml` | 다크 모드 앱 테마 (시스템이 다크 모드일 때 자동 적용) |

---

## xml/ — 앱 설정용 XML

| 파일 | 설명 |
|---|---|
| `file_paths.xml` | 카메라로 촬영한 임시 사진 파일을 `FileProvider`로 다른 앱(카메라 앱)에 공개하는 경로 설정. `cache-path name="camera_images" path="images/"` — 앱 캐시 폴더의 `images/` 하위를 노출한다 |
| `backup_rules.xml` | 안드로이드 12+ 자동 백업(Auto Backup) 규칙. 현재는 안드로이드 스튜디오 기본 템플릿 그대로(비어있음) |
| `data_extraction_rules.xml` | 안드로이드 12+ 데이터 추출(클라우드 백업/기기 간 전송) 규칙. 현재는 기본 템플릿 그대로(비어있음) |

---

## 참고

- 이 프로젝트는 문자열/색상을 `values/strings.xml`, `values/colors.xml`에 모으기보다 각 레이아웃 XML에 직접
  하드코딩하는 방식을 주로 쓰고 있습니다. 새 화면을 만들 때도 기존 관례를 따라 레이아웃에 직접 작성하면 됩니다.
- `activity_main2.xml`/`MainActivity2.kt`는 실제로 쓰이지 않는 초기 템플릿 잔재입니다. 삭제해도 앱 동작에는
  영향이 없지만, 이 문서 작성 시점 기준으로는 아직 프로젝트에 남아있어 참고용으로 표에 포함했습니다.
