// 로그인 화면의 상태와 서버 통신을 담당하는 ViewModel

package com.training.monitor.ui.login

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.training.monitor.data.api.ApiService
import com.training.monitor.data.local.TokenManager
import com.training.monitor.data.model.LoginRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [LoginActivity]의 ViewModel.
 *
 * [MVVM 변경] 기존에는 Activity가 `TokenManager`를 직접 들고 로그인 API를 호출하고,
 * 로딩 상태(ProgressBar)와 결과 Toast를 그 자리에서 바로 제어했다. MVVM에서는
 * "로그인 요청 자체 + 로딩/결과 상태 보관"을 ViewModel로 옮기고, Activity는 이 상태를
 * 관찰(observe)해서 화면(ProgressBar/Toast/화면 전환)만 갱신하는 역할로 축소된다.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    application: Application,
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) : AndroidViewModel(application) {

    // [MVVM 변경] 기존 Activity의 setLoading()이 직접 만지던 "로딩 중" 상태를 LiveData로 노출한다.
    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    // [MVVM 변경] 기존에는 각 분기(입력값 누락/인증 실패/네트워크 오류)에서 바로 Toast.makeText(...)를
    // 호출했다. ViewModel은 Context/View를 몰라야 하므로 "보여줄 메시지"만 LiveData로 흘려보낸다.
    private val _toastMessage = MutableLiveData<String?>(null)
    val toastMessage: LiveData<String?> = _toastMessage

    // [MVVM 변경] 로그인 성공 시 화면 전환(goToMain)은 View의 책임이므로, ViewModel은
    // "성공했다"는 사실만 일회성 이벤트로 알리고 실제 Intent 실행은 Activity가 한다.
    private val _loginSuccess = MutableLiveData(false)
    val loginSuccess: LiveData<Boolean> = _loginSuccess

    /** 이미 유효한 토큰이 있어 로그인 화면을 건너뛰어야 하는지 여부 (기존 Activity의 onCreate 초입 체크). */
    val isAlreadyLoggedIn: Boolean get() = tokenManager.isLoggedIn

    /**
     * 다음 로그인 시 비밀번호 변경이 강제되는 계정인지 여부. [isAlreadyLoggedIn]로 바로 진입하는
     * 경우와 방금 로그인에 성공한 경우 모두, Activity가 이 값으로 MainActivity 대신 비밀번호
     * 변경 화면으로 보낼지 판단한다.
     */
    val mustChangePassword: Boolean get() = tokenManager.mustChangePassword

    /** 입력값을 검증하고 로그인 API를 호출한다. 성공 시 토큰 저장 후 [loginSuccess]를 true로 발행한다. */
    fun login(militaryId: String, password: String) {
        if (militaryId.isEmpty() || password.isEmpty()) {
            _toastMessage.value = "군번과 비밀번호를 입력하세요."
            return
        }

        _loading.value = true

        viewModelScope.launch {
            try {
                val response = apiService.login(LoginRequest(militaryId, password))
                if (response.isSuccessful) {
                    val body = response.body()!!
                    tokenManager.accessToken = body.accessToken
                    tokenManager.refreshToken = body.refreshToken
                    tokenManager.role = body.role
                    tokenManager.mustChangePassword = body.mustChangePassword
                    _loginSuccess.value = true
                } else {
                    _toastMessage.value = "군번 또는 비밀번호가 올바르지 않습니다."
                }
            } catch (e: Exception) {
                _toastMessage.value = "서버 연결 실패: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    /** Activity가 메시지를 Toast로 보여준 뒤 호출 — 화면 회전 시 같은 메시지가 다시 뜨는 것을 방지한다. */
    fun onToastMessageShown() {
        _toastMessage.value = null
    }

    /** Activity가 화면 전환을 마친 뒤 호출 — 값을 비워야 화면 재생성 시 다시 전환되지 않는다. */
    fun onLoginHandled() {
        _loginSuccess.value = false
    }
}
