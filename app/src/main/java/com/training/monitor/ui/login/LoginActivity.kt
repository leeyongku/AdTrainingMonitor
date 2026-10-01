// 로그인 화면 - 군번/비밀번호 입력 후 역할별 화면으로 이동

package com.training.monitor.ui.login

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.training.monitor.databinding.ActivityLoginBinding
import com.training.monitor.ui.main.MainActivity
import com.training.monitor.ui.password.ChangePasswordActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * 앱 진입점(AndroidManifest의 LAUNCHER Activity).
 * 군번/비밀번호를 입력받아 로그인 API를 호출하고, 성공 시 토큰을 저장한 뒤
 * [MainActivity]로 넘어간다. 이미 로그인되어 있으면(유효한 토큰 존재) 입력 화면을
 * 거치지 않고 바로 메인으로 이동한다.
 *
 * [MVVM 변경] 이 클래스는 이제 "화면을 그리고 사용자 입력을 받는 View" 역할만 한다.
 * 로그인 API 호출과 로딩/결과 상태 보관은 [LoginViewModel]이 담당하고, 여기서는
 * 그 결과를 관찰(observe)해 화면에 반영하기만 한다.
 */
@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {

    //lateinit 변수를 선언할 때 바로 초기화하지 않고, 나중에 값을 활달하겠다는 뜻
    //주로 onCrate() 같은 생명주기 메서드에서 초기화할때 사용
    private lateinit var binding: ActivityLoginBinding

    // [MVVM 변경] 기존의 `private lateinit var tokenManager: TokenManager` 필드가 사라지고,
    // 그 역할(로그인 여부 확인, 토큰 저장)은 모두 ViewModel 안으로 옮겨졌다.
    private val viewModel: LoginViewModel by viewModels()

    // 로컬 네트워크(사설 IP 대역) 접속 권한 요청 결과 콜백. 결과와 상관없이 로그인을 시도해서,
    // 거부됐다면 서버 연결 실패로 자연스럽게 안내되게 한다.
    private val localNetworkPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { attemptLogin() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 이미 로그인된 경우 바로 다음 화면으로 (비밀번호 변경이 강제된 계정이면 그 화면부터)
        if (viewModel.isAlreadyLoggedIn) proceedAfterLogin()

        binding.btnLogin.setOnClickListener { requestLocalNetworkPermissionThenLogin() }

        // [MVVM 변경] 로딩 상태를 관찰해 ProgressBar/버튼을 갱신한다 (기존 setLoading()과 동일한 효과).
        viewModel.loading.observe(this) { loading -> setLoading(loading) }

        // [MVVM 변경] 기존에는 각 실패 분기에서 바로 Toast를 띄웠지만, 이제는 ViewModel이 넘겨준
        // 메시지를 관찰해서 Toast로 보여준 뒤 onToastMessageShown()으로 소비 처리한다.
        viewModel.toastMessage.observe(this) { message ->
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                viewModel.onToastMessageShown()
            }
        }

        // [MVVM 변경] 로그인 성공 이벤트를 관찰해 화면 전환을 수행한다.
        viewModel.loginSuccess.observe(this) { success ->
            if (success) {
                viewModel.onLoginHandled()
                proceedAfterLogin()
            }
        }
    }

    /**
     * 로그인 화면 다음으로 이동한다. 관리자가 부여한 임시 비밀번호를 아직 안 바꿨다면
     * (`mustChangePassword`) [ChangePasswordActivity]로, 아니면 바로 [MainActivity]로 보낸다.
     * 두 경우 모두 현재 로그인 화면은 백스택에서 제거한다(뒤로가기로 못 돌아오게).
     */
    private fun proceedAfterLogin() {
        val next = if (viewModel.mustChangePassword) ChangePasswordActivity::class.java else MainActivity::class.java
        startActivity(Intent(this, next))
        finish()
    }

    /** 로그인 버튼 비활성화 + 진행 표시줄 토글로 중복 클릭/요청을 방지한다. */
    private fun setLoading(loading: Boolean) {
        binding.btnLogin.isEnabled = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    /**
     * API 37(Android 17)부터 사설 IP 대역(로컬 네트워크) 접속에 ACCESS_LOCAL_NETWORK 런타임 권한이
     * 필요하다. 개발용 서버 주소(10.0.2.2 등)도 이 대역에 해당하므로, 로그인 시도 전에 권한이
     * 있는지 확인하고 없으면 먼저 요청한다.
     */
    private fun requestLocalNetworkPermissionThenLogin() {
        val permission = "android.permission.ACCESS_LOCAL_NETWORK"
        val alreadyGranted = Build.VERSION.SDK_INT < 37 ||
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            attemptLogin()
        } else {
            localNetworkPermissionLauncher.launch(permission)
        }
    }

    private fun attemptLogin() {
        viewModel.login(
            binding.etMilitaryId.text.toString().trim(),
            binding.etPassword.text.toString()
        )
    }
}
