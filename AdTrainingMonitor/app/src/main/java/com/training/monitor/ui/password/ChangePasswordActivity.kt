// 비밀번호 변경 화면 - 강제 진입(임시 비밀번호 교체)과 대원의 자율적인 비밀번호 변경을 함께 처리

package com.training.monitor.ui.password

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.training.monitor.databinding.ActivityChangePasswordBinding
import com.training.monitor.ui.login.LoginActivity
import com.training.monitor.ui.main.MainActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * 비밀번호 변경 화면. 두 가지 경로로 진입한다.
 *
 * 1. **강제 모드** (`EXTRA_FORCED=true`, 기본값) — [LoginActivity]가 로그인 직후(또는 "이미
 *    로그인됨" 진입 시) `TokenManager.mustChangePassword`가 true인 계정을 [MainActivity] 대신
 *    이 화면으로 보낸다. 대원 신규 등록이나 관리자의 비밀번호 재설정 직후처럼, 관리자가 정한
 *    임시 비밀번호를 본인이 직접 바꿔야만 다음부터 이 화면을 거치지 않는다. 뒤로가기/보조 버튼은
 *    "로그아웃"으로 동작해 화면에 갇히지 않게 하고, 성공 시 새로 [MainActivity]를 띄운다.
 * 2. **자율 모드** (`EXTRA_FORCED=false`) — 대원이 로그인 후 언제든 메인 화면 툴바의
 *    "비밀번호 변경" 메뉴로 직접 연다. 뒤로가기/보조 버튼은 그냥 "취소"로 동작해 원래
 *    화면(MainActivity)으로 돌아가고, 성공 시에도 새 Activity를 띄우지 않고 그대로 finish()한다.
 */
@AndroidEntryPoint
class ChangePasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChangePasswordBinding
    private val viewModel: ChangePasswordViewModel by viewModels()

    /** true면 강제 모드(임시 비밀번호 교체), false면 대원이 메뉴에서 직접 연 자율 모드. */
    private var forced = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChangePasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        forced = intent.getBooleanExtra(EXTRA_FORCED, true)
        applyModeToViews()

        binding.btnChangePassword.setOnClickListener {
            viewModel.changePassword(
                binding.etCurrentPassword.text.toString(),
                binding.etNewPassword.text.toString(),
                binding.etNewPasswordConfirm.text.toString()
            )
        }

        binding.btnSecondaryAction.setOnClickListener { leaveWithoutChanging() }

        // 물리 뒤로가기도 보조 버튼과 동일하게 처리 — 강제 모드에서 화면에 갇히는 것을 막는다.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                leaveWithoutChanging()
            }
        })

        viewModel.loading.observe(this) { loading -> setLoading(loading) }

        viewModel.toastMessage.observe(this) { message ->
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                viewModel.onToastMessageShown()
            }
        }

        viewModel.changeSuccess.observe(this) { success ->
            if (success) {
                viewModel.onChangeHandled()
                Toast.makeText(this, "비밀번호를 변경했습니다.", Toast.LENGTH_SHORT).show()
                if (forced) {
                    // 강제 모드는 MainActivity를 거치지 않고 바로 이 화면으로 왔으므로 새로 띄운다.
                    startActivity(Intent(this, MainActivity::class.java))
                }
                // 자율 모드는 이미 떠 있던 MainActivity로 그냥 되돌아간다.
                finish()
            }
        }
    }

    /** 화면 진입 모드(강제/자율)에 맞춰 안내 문구와 보조 버튼 텍스트를 맞춘다. */
    private fun applyModeToViews() {
        if (forced) {
            binding.tvSubtitle.text = "관리자가 부여한 임시 비밀번호입니다. 계속 진행하려면 새 비밀번호로 변경해주세요."
            binding.btnSecondaryAction.text = "로그아웃"
        } else {
            binding.tvSubtitle.text = "현재 비밀번호를 확인한 뒤 새 비밀번호로 변경합니다."
            binding.btnSecondaryAction.text = "취소"
        }
    }

    /**
     * 비밀번호를 바꾸지 않고 이 화면을 벗어난다. 강제 모드에서는 로그아웃 처리해 로그인 화면으로
     * 보낸다 — 다시 로그인해도 서버의 mustChangePassword는 그대로 true라 결국 이 화면으로
     * 재진입하게 된다. 자율 모드에서는 아무것도 바꾸지 않고 원래 화면(MainActivity)으로 돌아간다.
     */
    private fun leaveWithoutChanging() {
        if (forced) {
            viewModel.logout()
            startActivity(Intent(this, LoginActivity::class.java))
        }
        finish()
    }

    /** 변경 버튼 비활성화 + 진행 표시줄 토글로 중복 클릭/요청을 방지한다. */
    private fun setLoading(loading: Boolean) {
        binding.btnChangePassword.isEnabled = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    companion object {
        private const val EXTRA_FORCED = "forced"

        /** 대원이 메인 화면 메뉴 등에서 자율적으로 비밀번호를 바꾸려 할 때 이 인텐트로 연다. */
        fun voluntaryIntent(context: Context): Intent =
            Intent(context, ChangePasswordActivity::class.java).putExtra(EXTRA_FORCED, false)
    }
}
