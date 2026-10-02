// 서버 REST API 인터페이스 정의

package com.training.monitor.data.api

import com.training.monitor.data.model.*
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit이 런타임에 구현체를 자동 생성해주는 API 명세 인터페이스.
 *
 * - 실제 호출 시에는 [com.training.monitor.data.api.RetrofitClient.create]로 생성한 구현체를 사용한다.
 * - 모든 요청은 JWT Authorization 헤더가 자동으로 첨부되고(RetrofitClient의 인터셉터),
 *   401 응답 시 refresh token으로 자동 재시도된다.
 * - 요청/응답 바디의 JSON 필드명은 서버가 snake_case로 주고받기 때문에,
 *   [com.training.monitor.data.model] 쪽 DTO들이 @SerializedName으로 매핑을 담당한다.
 * - `Response<T>`로 감싸져 있으므로 호출부에서는 `response.isSuccessful`,
 *   `response.body()`로 성공/실패와 데이터를 직접 확인해야 한다 (예외는 네트워크 오류일 때만 발생).
 */
interface ApiService {

    // =============================================
    // 인증
    // =============================================

    /** 군번/비밀번호로 로그인하여 access/refresh 토큰과 역할(role)을 발급받는다. */
    @POST("api/auth/login")
    suspend fun login(@Body req: LoginRequest): Response<TokenResponse>

    /** 만료된 access token을 refresh token으로 재발급한다. (401 자동 재시도 시 내부적으로 사용) */
    @POST("api/auth/refresh")
    suspend fun refresh(@Body req: RefreshRequest): Response<TokenResponse>

    /** 서버에 저장된 refresh token을 무효화한다 (로그아웃). */
    @POST("api/auth/logout")
    suspend fun logout(): Response<Unit>

    /**
     * 로그인한 본인의 비밀번호를 변경한다 (현재 비밀번호 검증 후 교체).
     * 성공 시 서버의 MustChangePassword 플래그도 함께 false로 풀린다.
     */
    @PATCH("api/auth/password")
    suspend fun changePassword(@Body req: ChangePasswordRequest): Response<Unit>

    /** 로그인한 본인의 이름/군번/계급을 조회한다. 역할 구분 없이 호출할 수 있다. */
    @GET("api/auth/me")
    suspend fun getMyInfo(): Response<MyInfoDto>

    // =============================================
    // 대원 관리 (관리자)
    // =============================================

    /**
     * 소속 부대의 대원 목록을 조회한다.
     * @param unitId 특정 부대를 지정하고 싶을 때 사용, 생략 시 로그인한 관리자의 소속 부대 기준으로 조회.
     */
    @GET("api/members")
    suspend fun getMembers(@Query("unitId") unitId: Long? = null): Response<List<MemberDto>>

    /** 신규 대원을 등록한다 (군번 중복 시 서버에서 409 반환). */
    @POST("api/members")
    suspend fun createMember(@Body req: CreateMemberRequest): Response<Unit>

    /** 대원을 비활성화(제대/전역 처리) 한다. 실제 삭제가 아니라 is_active 플래그만 내린다. */
    @DELETE("api/members/{id}")
    suspend fun deactivateMember(@Path("id") id: Long): Response<Unit>

    /** 대원을 DB에서 완전히 삭제한다 (복구 불가능, 그 대원의 측정 기록도 함께 삭제됨). */
    @DELETE("api/members/{id}/permanent")
    suspend fun deleteMemberPermanently(@Path("id") id: Long): Response<Unit>

    /** 대원의 이름/계급/소속 부대/얼굴 사진을 수정한다. 군번/비밀번호는 이 API로 바꿀 수 없다. */
    @PATCH("api/members/{id}")
    suspend fun updateMember(@Path("id") id: Long, @Body req: UpdateMemberRequest): Response<Unit>

    /** 대원의 비밀번호를 관리자가 새 값으로 재설정한다. */
    @PATCH("api/members/{id}/password")
    suspend fun resetPassword(@Path("id") id: Long, @Body req: ResetPasswordRequest): Response<Unit>

    /**
     * 등록 가능한 계급 목록을 조회한다 (예: ["이병", "일병", ...]).
     * 서버의 등급 산출 로직(RecordsController.CalculateGrade)이 인식하는 계급과 동일한 목록이므로,
     * "대원 추가" 화면의 계급 스피너는 자유 입력 대신 이 목록에서 선택하도록 구성한다.
     */
    @GET("api/members/ranks")
    suspend fun getRanks(): Response<List<String>>

    /**
     * 등록 가능한 소속 부대 목록을 조회한다.
     * "대원 추가" 화면의 소속 부대 스피너는 자유 입력(부대 ID 직접 입력) 대신 이 목록에서 선택하도록 구성한다.
     */
    @GET("api/members/units")
    suspend fun getUnits(): Response<List<UnitDto>>

    // =============================================
    // 측정 세션 (관리자)
    // =============================================

    /** 측정 세션(특정 일시/장소에 실시한 측정 회차) 목록을 조회한다. */
    @GET("api/sessions")
    suspend fun getSessions(): Response<List<SessionDto>>

    /** 새 측정 세션을 생성한다. 응답 바디는 생성된 세션의 id를 담은 Map (예: {"id": 5}). */
    @POST("api/sessions")
    suspend fun createSession(@Body req: SessionRequest): Response<Map<String, Long>>

    // =============================================
    // 기록
    // =============================================

    /** 체력 측정 기록을 저장한다. 응답 바디에 서버가 자동 산출한 등급(grade)이 포함된다. */
    @POST("api/records")
    suspend fun createRecord(@Body req: RecordRequest): Response<Map<String, Any>>

    /** 로그인한 사용자 본인의 측정 기록 전체를 조회한다 (대원용 "내 기록" 화면). */
    @GET("api/records/my")
    suspend fun myRecords(): Response<List<RecordDto>>

    /** 로그인한 본인의 측정 기록을 전부 삭제한다. 대상이 항상 본인으로 고정되므로 ADMIN 권한이 필요 없다. */
    @DELETE("api/records/my")
    suspend fun deleteMyRecords(): Response<Map<String, Any>>

    /** 로그인한 본인의 특정 종목 측정 기록을 전부 삭제한다. */
    @DELETE("api/records/my/category/{categoryId}")
    suspend fun deleteMyRecordsByCategory(@Path("categoryId") categoryId: Long): Response<Map<String, Any>>

    /** 특정 대원의 측정 기록을 조회한다 (관리자가 대원 상세를 볼 때 사용). */
    @GET("api/records/user/{userId}")
    suspend fun userRecords(@Path("userId") userId: Long): Response<List<RecordDto>>

    /** 특정 측정 세션에 속한 모든 기록을 조회한다. */
    @GET("api/records/session/{sessionId}")
    suspend fun sessionRecords(@Path("sessionId") sessionId: Long): Response<List<RecordDto>>

    /** 특정 기록에 첨부된 사진 원본을 내려받는다. 본인 기록이거나 관리자만 조회 가능(서버가 검증). */
    @GET("api/records/{id}/photo")
    suspend fun recordPhoto(@Path("id") id: Long): Response<ResponseBody>

    /** 측정 기록 하나를 삭제한다 (관리자 전용). */
    @DELETE("api/records/{id}")
    suspend fun deleteRecord(@Path("id") id: Long): Response<Unit>

    /** 특정 대원의 측정 기록을 전부 삭제한다 (관리자 전용). */
    @DELETE("api/records/user/{userId}")
    suspend fun deleteAllUserRecords(@Path("userId") userId: Long): Response<Map<String, Any>>

    /** 특정 대원의 특정 종목 측정 기록을 전부 삭제한다 (관리자 전용). */
    @DELETE("api/records/user/{userId}/category/{categoryId}")
    suspend fun deleteUserRecordsByCategory(
        @Path("userId") userId: Long,
        @Path("categoryId") categoryId: Long
    ): Response<Map<String, Any>>

    /**
     * 특정 종목의 측정값 추이(시계열)를 조회한다. 그래프 그리기용.
     * @param userId 생략 시 로그인한 사용자 본인 기준.
     * @param categoryId 종목 ID (1: 3km 달리기, 2: 팔굽혀펴기, 3: 윗몸일으키기).
     */
    @GET("api/records/trend")
    suspend fun trend(
        @Query("userId") userId: Long? = null,
        @Query("categoryId") categoryId: Long
    ): Response<List<TrendPoint>>

    // =============================================
    // 통계
    // =============================================

    /**
     * 지정한 부대(및 그 하위 부대 전체)의 지정 기간(from~to) 통계(등급 분포, 종목별 분포 등)를 조회한다.
     * @param unitId 조회 대상 부대 id. 이 부대의 모든 하위 부대 인원까지 합산해서 내려온다.
     * @param from,@param to "yyyy-MM-dd" 형식의 날짜 문자열.
     */
    @GET("api/stats/unit")
    suspend fun unitStats(
        @Query("unitId") unitId: Long,
        @Query("from") from: String,
        @Query("to") to: String
    ): Response<UnitStatsDto>

    /** 특정 대원의 종목별 개인 최고 기록을 조회한다. */
    @GET("api/stats/personal/{userId}")
    suspend fun personalBest(@Path("userId") userId: Long): Response<List<PersonalBestDto>>

    /**
     * 특정 대원·종목에 실제로 적용되는 등급 기준표를 조회한다 (계급군 반영 + 공통 기준 폴백까지
     * 서버가 처리해서 내려줌). 기록 입력 화면의 등급 미리보기가 사용한다.
     */
    @GET("api/records/grade-criteria")
    suspend fun gradeCriteria(
        @Query("categoryId") categoryId: Long,
        @Query("userId") userId: Long
    ): Response<List<GradeCriteriaDto>>
}