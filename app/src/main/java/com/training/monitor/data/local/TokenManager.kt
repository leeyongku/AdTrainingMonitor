// JWT 토큰을 암호화하여 로컬에 안전하게 저장하는 매니저

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

    // AndroidKeyStore에 보관되는 마스터 키로 파일 전체를 암호화하는 SharedPreferences.
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "auth_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /** API 요청 Authorization 헤더에 실리는 단기 토큰. */
    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS, null)
        set(value) = prefs.edit().putString(KEY_ACCESS, value).apply()

    /** access token 만료 시 재발급에 사용되는 장기 토큰. */
    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)
        set(value) = prefs.edit().putString(KEY_REFRESH, value).apply()

    /** 로그인 성공 시 서버가 내려준 역할("ADMIN" 또는 "MEMBER"). 네비게이션 그래프 분기에 사용. */
    var role: String?
        get() = prefs.getString(KEY_ROLE, null)
        set(value) = prefs.edit().putString(KEY_ROLE, value).apply()

    /**
     * 다음 로그인 시 비밀번호 변경이 강제되는 계정인지 여부(서버의 User.MustChangePassword를 그대로 반영).
     * [com.training.monitor.ui.login.LoginActivity]가 이 값으로 MainActivity 대신 비밀번호
     * 변경 화면으로 보낼지 판단한다.
     */
    var mustChangePassword: Boolean
        get() = prefs.getBoolean(KEY_MUST_CHANGE_PASSWORD, false)
        set(value) = prefs.edit().putBoolean(KEY_MUST_CHANGE_PASSWORD, value).apply()

    /** accessToken 존재 여부로 로그인 상태를 판단 (LoginActivity 진입 시 자동 스킵 여부 결정). */
    val isLoggedIn: Boolean get() = accessToken != null

    /** 현재 로그인한 사용자가 관리자인지 여부. */
    val isAdmin: Boolean get() = role == "ADMIN"

    /** 로그아웃 시 저장된 토큰/역할을 전부 지운다. */
    fun clear() = prefs.edit().clear().apply()

    companion object {
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_ROLE = "role"
        private const val KEY_MUST_CHANGE_PASSWORD = "must_change_password"
    }
}