// Retrofit 클라이언트 싱글톤 - JWT 자동 첨부 및 토큰 갱신 처리

package com.training.monitor.data.api

import android.content.Context
import android.content.Intent
import com.training.monitor.BuildConfig
import com.training.monitor.data.local.TokenManager
import com.training.monitor.ui.login.LoginActivity
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * [ApiService] 구현체를 만들어주는 팩토리 오브젝트.
 *
 * 매 호출마다 [create]로 새 인스턴스를 만들지만, 내부적으로 매번 동일한 설정
 * (JWT 자동 첨부 + 401 자동 갱신 + 로깅)을 가진 OkHttpClient/Retrofit을 구성한다.
 * DI 컨테이너 없이 간단히 쓰기 위한 구조라, 호출부는 `RetrofitClient.create(context)`만 하면 된다.
 */
object RetrofitClient {

    /** [context]로 [TokenManager]에 접근하며 인증 로직이 내장된 [ApiService] 인스턴스를 생성한다. */
    fun create(context: Context): ApiService {
        val tokenManager = TokenManager(context)

        // 모든 요청에 Authorization 헤더를 자동으로 붙이고, 토큰 만료(401) 시
        // refresh token으로 한 번 재발급받아 원래 요청을 재시도하는 인터셉터.
        val authInterceptor = Interceptor { chain ->
            val token = tokenManager.accessToken
            val request = if (token != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else chain.request()

            var response = chain.proceed(request)

            // 401 → refresh token으로 자동 갱신을 시도한다. refresh token 자체가 없으면
            // (에초에 로그인 안 된 상태 등) 재발급 시도 없이 바로 세션 만료로 취급한다.
            if (response.code == 401) {
                response.close()
                // OkHttp 인터셉터는 동기 컨텍스트이므로 suspend 함수인 refreshAccessToken을
                // runBlocking으로 감싸 동기적으로 대기한다.
                val newToken = tokenManager.refreshToken?.let { runBlocking { refreshAccessToken(tokenManager) } }
                if (newToken != null) {
                    tokenManager.accessToken = newToken
                    val retryRequest = chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $newToken")
                        .build()
                    response = chain.proceed(retryRequest)
                } else {
                    // refresh token도 없거나 만료됨 → 세션이 완전히 끊긴 상태.
                    // 로컬 토큰을 지우고, 지금 어느 화면에 있든 로그인 화면으로 강제 이동시킨다.
                    // FLAG_ACTIVITY_NEW_TASK: Application Context에서 액티비티를 띄우려면 필수.
                    // FLAG_ACTIVITY_CLEAR_TASK: 기존에 쌓여있던 화면 스택을 전부 지워서, 로그인
                    // 화면에서 뒤로가기를 눌러도 만료된 세션의 화면으로 못 돌아가게 한다.
                    tokenManager.clear()
                    context.startActivity(
                        Intent(context, LoginActivity::class.java)
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                }
            }
            response
        }

        // 요청/응답 전체(BODY 레벨)를 logcat에 출력 — 디버깅용. 운영 배포 시에는 레벨을 낮춰야 한다.
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)  // 에뮬레이터: 10.0.2.2, 실기기: 서버 실제 IP (build.gradle.kts에서 설정)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    /**
     * refresh token으로 새 access token을 발급받는다.
     * 인증 인터셉터가 재귀적으로 자기 자신을 타지 않도록, Authorization 헤더가 없는
     * 별도의(비인증) Retrofit 인스턴스를 새로 만들어 호출한다.
     * @return 갱신 성공 시 새 access token, 실패(만료 등) 시 null.
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
                // 서버가 refresh 시 refresh token도 함께 재발급(로테이션)하므로 같이 갱신해준다.
                tokenManager.refreshToken = response.body()?.refreshToken
                response.body()?.accessToken
            } else null
        } catch (e: Exception) { null }
    }
}