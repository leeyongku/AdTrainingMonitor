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
