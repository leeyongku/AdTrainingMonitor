// 비밀번호 변경 화면(강제/자율 공통)의 상태와 서버 통신을 담당하는 ViewModel

package com.training.monitor.ui.password

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.training.monitor.data.api.ApiService
import com.training.monitor.data.local.TokenManager
import com.training.monitor.data.model.ChangePasswordRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [ChangePasswordActivity]의 ViewModel.
 *
 * [LoginViewModel]과 동일한 패턴: 입력값 검증 + API 호출 + 로딩/결과 상태 보관을 여기서
 * 전담하고, Activity는 그 상태를 관찰(observe)해서 화면만 갱신한다.
 */
@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    application: Application,
    private val apiService: ApiService,
    private val tokenManager: TokenManager
) : AndroidViewModel(application) {

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _toastMessage = MutableLiveData<String?>(null)
    val toastMessage: LiveData<String?> = _toastMessage

    // 성공 시 화면 전환(MainActivity로 이동)은 View의 책임이므로, ViewModel은 "성공했다"는
    // 사실만 일회성 이벤트로 알린다.
    private val _changeSuccess = MutableLiveData(false)
    val changeSuccess: LiveData<Boolean> = _changeSuccess

    /**
     * 입력값을 검증하고 비밀번호 변경 API를 호출한다.
     * 성공 시 로컬에 저장된 mustChangePassword 플래그도 함께 내려서, 다음부터는 이 화면을 거치지 않게 한다.
     */
    fun changePassword(currentPassword: String, newPassword: String, newPasswordConfirm: String) {
        if (currentPassword.isEmpty() || newPassword.isEmpty() || newPasswordConfirm.isEmpty()) {
            _toastMessage.value = "모든 항목을 입력하세요."
            return
        }
        if (newPassword != newPasswordConfirm) {
            _toastMessage.value = "새 비밀번호가 서로 일치하지 않습니다."
            return
        }
        if (newPassword == currentPassword) {
            _toastMessage.value = "현재 비밀번호와 다른 값을 입력하세요."
            return
        }

        _loading.value = true

        viewModelScope.launch {
            try {
                val response = apiService.changePassword(ChangePasswordRequest(currentPassword, newPassword))
                if (response.isSuccessful) {
                    tokenManager.mustChangePassword = false
                    _changeSuccess.value = true
                } else if (response.code() == 401) {
                    _toastMessage.value = "현재 비밀번호가 올바르지 않습니다."
                } else {
                    _toastMessage.value = "비밀번호 변경 실패"
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
    fun onChangeHandled() {
        _changeSuccess.value = false
    }

    /**
     * 비밀번호를 바꾸지 않고 이 화면을 벗어나려 할 때(로그아웃 버튼, 뒤로가기) 호출한다.
     * 저장된 토큰을 모두 삭제한다 — 다시 로그인해도 서버의 MustChangePassword는 그대로 true라
     * 결국 이 화면으로 재진입하게 된다. 화면 전환(LoginActivity로 이동)은 Activity가 담당한다.
     */
    fun logout() {
        tokenManager.clear()
    }
}
