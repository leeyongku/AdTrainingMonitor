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
