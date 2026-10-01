// 서버 API 요청/응답 DTO 모음
//
// [DTO(Data Transfer Object)란?]
// 화면(Fragment/Activity)이나 앱 내부 로직에서 쓰는 모델이 아니라,
// "서버와 JSON을 주고받기 위한 전용 데이터 형태"를 정의한 클래스들입니다.
// 요청(Request)용은 클라이언트 -> 서버로 보내는 바디, 응답(~Dto)용은
// 서버 -> 클라이언트로 받는 바디를 나타냅니다.
//
// [Kotlin data class를 쓰는 이유]
// data class로 선언하면 컴파일러가 자동으로 만들어주는 것들:
//  - equals()/hashCode(): 필드 값이 전부 같으면 같은 객체로 취급 (예: MemberAdapter의
//    DiffUtil이 "내용이 바뀌었는지" 비교할 때 이 equals()를 사용함)
//  - toString(): 로그 찍을 때 필드 값이 보기 좋게 출력됨
//  - copy(): 일부 필드만 바꾼 새 인스턴스를 쉽게 만들 수 있음
// 즉 "그냥 값 담는 상자"라는 걸 명시적으로 표현하는 Kotlin 문법입니다.
//
// [Retrofit + Gson이 이 파일을 사용하는 방식]
// RetrofitClient가 GsonConverterFactory를 등록해뒀기 때문에,
// ApiService의 @Body/Response<T> 파라미터에 이 DTO들을 쓰면
// Gson이 자동으로 "Kotlin 객체 <-> JSON 문자열" 변환을 해줍니다.
//  - 요청 시: data class 인스턴스 -> JSON 문자열로 직렬화(serialize)해서 HTTP 바디에 담음
//  - 응답 시: 서버가 준 JSON 문자열 -> data class 인스턴스로 역직렬화(deserialize)
//
// [왜 모든 필드에 @SerializedName이 필요한가]
// Gson은 기본적으로 "Kotlin 프로퍼티 이름 그대로"를 JSON 키로 사용합니다
// (예: militaryId 프로퍼티는 별다른 설정이 없으면 JSON에서도 "militaryId" 키를 찾음).
// 하지만 이 프로젝트의 서버(TrainingMonitor, ASP.NET Core)는 JSON 키를
// snake_case(예: "military_id")로 주고받기로 약속되어 있습니다
// (서버 쪽 Program.cs에 등록된 SnakeCaseNamingPolicy 때문).
// 그래서 Kotlin 관례인 camelCase 프로퍼티명과 실제 JSON 키가 다르기 때문에,
// @SerializedName("실제_json_키")로 "이 프로퍼티는 JSON에서 이 이름으로 주고받아라"라고
// 명시적으로 지정해줘야 합니다. id/name/value처럼 원래부터 한 단어라 camelCase와
// snake_case 표기가 똑같은 필드는 굳이 @SerializedName을 안 붙여도 무방합니다.
//
// [nullable(?) 필드에 대해]
// 타입 뒤에 ?가 붙은 필드(예: rank: String?)는 서버 응답에서 값이 없을 수 있다는 뜻입니다
// (DB 컬럼이 nullable이거나, 아직 값이 채워지지 않은 선택 항목). 화면에서 이런 필드를
// 쓸 때는 항상 널 처리(예: `member.rank ?: ""`)를 해줘야 NPE 없이 안전합니다.

package com.training.monitor.data.model

import com.google.gson.annotations.SerializedName

// =============================================
// 인증 — LoginActivity, RetrofitClient(토큰 자동 갱신)에서 사용
// =============================================

/**
 * 로그인 요청 바디. POST /api/auth/login 호출 시 [com.training.monitor.data.api.ApiService.login]의
 * 파라미터로 사용된다.
 *
 * 서버는 이 두 값으로 users 테이블에서 military_id가 일치하고 활성 상태(is_active)인 사용자를 찾은 뒤,
 * password를 BCrypt로 검증한다. 두 필드 모두 필수값이라 nullable이 아니다.
 */
data class LoginRequest(
    // 군번. 로그인 화면의 "군번" 입력창(etMilitaryId) 값이 그대로 들어온다.
    @SerializedName("military_id") val militaryId: String,
    // 평문 비밀번호. HTTPS 구간에서만 안전하며, 서버에 도착한 뒤 즉시 BCrypt 해시와 비교되고 저장되지 않는다.
    @SerializedName("password") val password: String
)

/**
 * access token이 만료되어 401을 받았을 때, refresh token으로 재발급을 요청하는 바디.
 * POST /api/auth/refresh 호출 시 사용되며, 실제 호출 지점은 화면 코드가 아니라
 * [com.training.monitor.data.api.RetrofitClient]의 인증 인터셉터 내부(자동 재시도 로직)이다.
 */
data class RefreshRequest(
    @SerializedName("refresh_token") val refreshToken: String
)

/**
 * 로그인(POST /api/auth/login)과 토큰 재발급(POST /api/auth/refresh) 두 API가 공통으로 돌려주는 응답 바디.
 *
 * 로그인 성공 시 이 값들은 [com.training.monitor.data.local.TokenManager]의
 * accessToken/refreshToken/role 프로퍼티에 각각 저장되어, 이후 모든 API 호출과
 * "관리자/대원 어느 화면을 보여줄지" 분기에 사용된다.
 */
data class TokenResponse(
    // API 호출마다 Authorization: Bearer {accessToken} 헤더로 실려서 신원을 증명하는 단기 토큰.
    @SerializedName("access_token") val accessToken: String,
    // accessToken 만료 시 재발급용으로만 쓰이는 장기 토큰. 매 요청에 실리지 않는다.
    @SerializedName("refresh_token") val refreshToken: String,
    // "ADMIN" 또는 "MEMBER" 문자열 (서버의 UserRole enum이 문자열로 직렬화된 것).
    // MainActivity가 이 값으로 관리자용/대원용 네비게이션 그래프를 선택한다.
    @SerializedName("role") val role: String,   // ADMIN or MEMBER
    // 관리자가 임시 비밀번호를 부여한 직후(신규 등록/비밀번호 재설정) true. LoginActivity가 이
    // 값을 보고 MainActivity 대신 비밀번호 변경 화면으로 먼저 보낼지 판단한다.
    @SerializedName("must_change_password") val mustChangePassword: Boolean
)

/**
 * 본인 비밀번호 변경 요청 바디. PATCH /api/auth/password 호출 시 사용되며, 로그인 상태(JWT)만
 * 있으면 역할 구분 없이 누구나 호출할 수 있다. 서버가 currentPassword를 BCrypt로 검증한 뒤에만
 * newPassword로 교체하고, 성공 시 서버 쪽 MustChangePassword 플래그도 함께 false로 풀린다.
 */
data class ChangePasswordRequest(
    @SerializedName("current_password") val currentPassword: String,
    @SerializedName("new_password") val newPassword: String
)

// =============================================
// 대원 — MemberListFragment, MemberAdapter, RecordInputFragment(대원 선택)에서 사용
// =============================================

/**
 * 대원 목록 조회(GET /api/members) 응답의 개별 항목.
 * MemberListFragment가 목록/검색에 쓰고, RecordInputFragment가 기록 입력 대상 대원을 고를 때도 재사용한다.
 */
data class MemberDto(
    // 서버 DB의 users.id (PK). 목록 갱신 시 MemberAdapter.DiffCallback이 "같은 항목인지" 판단하는 키.
    val id: Long,
    val name: String,
    val role: String,
    // 군번. id와 별개로 사람이 식별하는 값이라 검색(이름 또는 군번)에도 쓰인다.
    @SerializedName("military_id") val militaryId: String,
    // 계급. 아직 부여되지 않았거나 값이 없는 대원이 있을 수 있어 nullable.
    val rank: String?,
    // 소속 부대명. 서버가 Unit 테이블을 조인해서 이름만 내려준다 (unit_id 자체는 내려주지 않음).
    @SerializedName("unit_name") val unitName: String?,
    // 얼굴 사진 (Base64, JPEG). 등록 안 했으면 null — 목록 아이콘에 표시할 작은 이미지라 바로 내려받는다.
    @SerializedName("photo_base64") val photoBase64: String?
)

/**
 * 신규 대원 등록(POST /api/members) 요청 바디. 서버 MembersController는 관리자(ADMIN) 권한만
 * 허용하므로, 이 요청은 항상 로그인 토큰에 ADMIN role이 실려 있어야 성공한다.
 */
data class CreateMemberRequest(
    // 군번은 서버에서 유니크 제약이 걸려 있어, 이미 등록된 군번이면 409 Conflict가 돌아온다.
    @SerializedName("military_id") val militaryId: String,
    val name: String,
    // 최초 비밀번호. 서버가 BCrypt로 해시해서 저장하고 평문은 남기지 않는다.
    val password: String,
    val rank: String,
    // 어느 부대 소속으로 등록할지. 계급과 마찬가지로 필수.
    @SerializedName("unit_id") val unitId: Long,
    // 얼굴 사진 (Base64, JPEG, 선택). MemberListViewModel.createMember()가 인코딩해서 채워 넣는다.
    @SerializedName("photo_base64") val photoBase64: String? = null
)

/**
 * 기존 대원 정보 수정(PATCH /api/members/{id}) 요청 바디. 군번/비밀번호는 이 요청으로
 * 바꿀 수 없다 (군번은 로그인 식별자, 비밀번호는 [ResetPasswordRequest]가 별도로 담당).
 */
data class UpdateMemberRequest(
    val name: String,
    val rank: String,
    @SerializedName("unit_id") val unitId: Long,
    // 새로 촬영/선택한 사진 (Base64, JPEG). null이면 "사진을 바꾸지 않음"을 의미한다.
    @SerializedName("photo_base64") val photoBase64: String? = null,
    // true면 기존 사진을 지운다 (photoBase64와 동시에 true일 수 없다 — 삭제가 우선 적용됨).
    @SerializedName("remove_photo") val removePhoto: Boolean = false
)

/** 대원 비밀번호 재설정(PATCH /api/members/{id}/password) 요청 바디. CreateMemberRequest와 마찬가지로 관리자 권한이 필요하다. */
data class ResetPasswordRequest(
    @SerializedName("new_password") val newPassword: String
)

/**
 * 등록 가능한 소속 부대 목록 조회(GET /api/members/units) 응답의 개별 항목.
 * "대원 추가" 화면의 소속 부대 스피너가 이 목록으로 채워지며, 선택된 id가 CreateMemberRequest.unitId로 전송된다.
 */
data class UnitDto(
    val id: Long,
    val name: String,
    // 부대 계층 깊이. 0=여단, 1=대대, 2=중대, 3=소대, 4=분대 (관리자 통계 화면의 계층 스피너가 이 값으로 필터링한다).
    val level: Int
)

// =============================================
// 세션 — RecordInputFragment(세션 선택 스피너)에서 사용
// =============================================

/**
 * 측정 세션(특정 일시/장소에 실시한 체력 측정 회차) 목록 조회 응답의 개별 항목.
 * RecordInputFragment가 스피너에 "측정일 장소" 형태 라벨로 나열할 때 쓴다.
 */
data class SessionDto(
    val id: Long,
    // "yyyy-MM-dd" 형식의 측정일 문자열. 서버의 DateOnly 타입이 문자열로 직렬화된 것.
    @SerializedName("measured_at") val measuredAt: String,
    // 측정 장소. 선택 입력이라 비어있을 수 있음.
    val location: String?,
    // 세션을 생성한 관리자 이름. 목록에 부가 정보로만 쓰이고 별도 로직에는 관여하지 않는다.
    @SerializedName("admin_name") val adminName: String?,
    @SerializedName("unit_name") val unitName: String?
)

/**
 * 새 측정 세션 생성(POST /api/sessions) 요청 바디.
 * 응답은 [com.training.monitor.data.api.ApiService.createSession]에서 `Map<String, Long>` 형태로
 * 생성된 세션의 id만 돌려받는다 (별도 DTO 없이 간단한 키-값 형태로 처리).
 */
data class SessionRequest(
    @SerializedName("unit_id") val unitId: Long?,
    @SerializedName("measured_at") val measuredAt: String,   // "yyyy-MM-dd"
    val location: String?,
    val note: String?
)

// =============================================
// 기록 — MyRecordFragment(내 기록), RecordInputFragment(기록 저장)에서 사용
// =============================================

/**
 * 측정 기록 조회 응답의 개별 항목. 아래 세 API가 형태가 동일해서 하나의 DTO를 공유한다.
 * - GET /api/records/my (본인 기록)
 * - GET /api/records/user/{userId} (특정 대원 기록)
 * - GET /api/records/session/{sessionId} (특정 세션의 전체 기록)
 */
data class RecordDto(
    val id: Long,
    // 서버가 User/Category를 조인해서 이름만 미리 내려준다 — 클라이언트가 별도로 id를 들고
    // 다시 조회할 필요가 없도록 편의상 펼쳐서(flatten) 내려주는 형태.
    @SerializedName("user_name") val userName: String,
    @SerializedName("category_name") val categoryName: String,
    // 측정값 (종목에 따라 초/회 등 단위가 다름 — 실제 단위는 아래 unit 필드로 구분)
    val value: Double,
    // 측정 단위 텍스트 (예: "초", "회"). Category.Unit 컬럼 값.
    val unit: String,
    // 서버가 산출한 등급("특급"~"불합격"). 등급 기준표가 아직 없는 조합이면 null일 수 있음.
    val grade: String?,
    // 이 기록이 어느 세션에서 측정됐는지의 날짜. 세션 없이 등록된 기록이면 null.
    @SerializedName("measured_at") val measuredAt: String?,
    // 사진 첨부 여부. true면 GET /api/records/{id}/photo로 원본을 받아올 수 있다.
    @SerializedName("has_photo") val hasPhoto: Boolean
)

/**
 * 측정 기록 저장(POST /api/records) 요청 바디. RecordInputFragment의 저장 버튼에서 사용.
 * 등급(grade)은 서버가 GradeCriteria 기준표를 조회해 자동 산출하므로 클라이언트가 보내지 않는다
 * — 응답 바디(Map<String, Any>)의 "grade" 키로 결과만 돌려받는다.
 */
data class RecordRequest(
    // 어느 측정 세션에 속한 기록인지. 세션 없이 개별 등록도 허용하는 경우를 위해 nullable.
    @SerializedName("session_id") val sessionId: Long?,
    // 대상 대원의 id. RecordInputFragment의 대원 스피너 선택값에서 가져온다.
    @SerializedName("user_id") val userId: Long,
    // 측정 종목 id (1: 3km 달리기, 2: 팔굽혀펴기, 3: 윗몸일으키기 — RecordInputFragment.categoryMap 참고).
    @SerializedName("category_id") val categoryId: Long,
    val value: Double,
    // 특이사항 메모. 빈 문자열이면 화면단에서 null로 변환해 보낸다(ifBlank { null }).
    val note: String?,
    // 촬영한 사진을 리사이즈/압축 후 Base64로 인코딩한 문자열. 사진이 없으면 null.
    @SerializedName("photo_base64") val photoBase64: String? = null
)

/**
 * 추이 그래프(GET /api/records/trend)의 데이터 포인트 하나. MyRecordFragment가
 * 시간순으로 나열된 이 리스트를 MPAndroidChart의 Entry 목록으로 변환해 꺾은선 그래프를 그린다.
 */
data class TrendPoint(
    @SerializedName("measured_at") val measuredAt: String,
    val value: Double,
    val grade: String?
)

/**
 * 등급 기준표(GET /api/records/grade-criteria) 응답의 개별 항목. 특정 대원·종목에 실제로
 * 적용되는(계급군 반영 + 공통 기준 폴백까지 서버가 처리한) 기준을 SortOrder순으로 내려준다.
 * RecordInputViewModel이 이 리스트를 그대로 순서대로 매칭해 등급 미리보기를 계산한다
 * (값이 없는 필드는 무제한 — 예: minValue가 null이면 하한 없음).
 */
data class GradeCriteriaDto(
    val grade: String,
    @SerializedName("min_value") val minValue: Double?,
    @SerializedName("max_value") val maxValue: Double?
)

// =============================================
// 통계 — StatsFragment(관리자 통계 화면)에서 사용
// =============================================

/**
 * 부대 단위 통계(GET /api/stats/unit) 응답. StatsFragment가 조회 기간(from~to)을 지정해 호출하고,
 * 결과로 원형 그래프(등급 분포)와 막대 그래프(종목별 등급 분포)를 그린다.
 */
data class UnitStatsDto(
    // 통계 대상 기간 내 활동한 것과 무관하게, 현재 소속된 활성 대원 총원.
    @SerializedName("member_count") val memberCount: Int,
    // 조회 기간 내 등록된 전체 측정 기록 건수.
    @SerializedName("record_count") val recordCount: Int,
    // 등급명 -> 인원수. 예: {"특급": 3, "1급": 10, "2급": 8, "3급": 5, "불합격": 2}
    // StatsFragment.drawPieChart()가 이 맵을 그대로 PieEntry 리스트로 변환한다.
    @SerializedName("grade_distribution") val gradeDistribution: Map<String, Int>,
    // 종목명 -> (등급명 -> 인원수)의 중첩 맵. 예:
    // {"3km 달리기": {"특급": 2, "1급": 5, ...}, "팔굽혀펴기": {...}, ...}
    // StatsFragment.drawBarChart()가 바깥 키(종목)를 x축, 안쪽 키(등급)를 계열(그룹 막대)로 사용한다.
    @SerializedName("by_category") val byCategory: Map<String, Map<String, Int>>
)

/**
 * 개인 최고 기록(GET /api/stats/personal/{userId}) 응답의 종목별 항목.
 * 종목마다 지금까지 측정한 값 중 최고 기록과, 그 기록이 속한(혹은 가장 최근) 등급을 담는다.
 * (현재 UI 코드에서는 아직 이 DTO를 사용하는 화면이 연결되어 있지 않다 — API 명세만 준비된 상태.)
 */
data class PersonalBestDto(
    // 종목명 (예: "3km 달리기"). 종목 id가 아니라 이름으로 내려온다는 점에 주의.
    val category: String,
    @SerializedName("best_value") val bestValue: Double,
    @SerializedName("latest_grade") val latestGrade: String?
)