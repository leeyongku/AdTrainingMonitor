// Android Studio 프로젝트 생성 시 만들어진 기본 템플릿 화면 (AndroidManifest 미등록, 실사용 안 됨)

package com.training.monitor.ui.main2

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.training.monitor.R
import com.training.monitor.databinding.ActivityMain2Binding

/**
 * Android Studio가 프로젝트 생성 시 기본으로 만들어주는 템플릿 Activity.
 *
 * [MVVM 변경] AndroidManifest에 등록되지 않은 죽은 코드라 실제 상태/로직이 없지만,
 * 프로젝트 컨벤션에 맞춰 대응하는 ViewModel([MainActivity2ViewModel])을 연결해뒀다.
 */
class MainActivity2 : AppCompatActivity() {

    private lateinit var binding: ActivityMain2Binding
    private val viewModel: MainActivity2ViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMain2Binding.inflate(layoutInflater)
        setContentView(binding.root)

        val goMainBtn = binding.btnGoMain

        goMainBtn.setOnClickListener{
            val intent = Intent(this, MainActivity2::class.java)
            startActivity(intent)
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}