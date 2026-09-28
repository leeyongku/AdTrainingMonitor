// 메인 화면 - 역할(관리자/대원)에 따라 하단 네비게이션 구성

package com.training.monitor.ui.main

import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.training.monitor.R
import com.training.monitor.databinding.ActivityMainBinding
import com.training.monitor.ui.login.LoginActivity
import com.training.monitor.ui.password.ChangePasswordActivity
import com.training.monitor.ui.photo.PhotoViewActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 로그인 이후 진입하는 메인 컨테이너 Activity.
 *
 * 화면 자체는 상단 툴바 + 하단 네비게이션 + [NavHostFragment]로 구성된 껍데기이고,
 * 실제 탭별 화면(대원 목록/기록 입력/통계/내 기록 등)은 로그인한 사용자의 역할에 따라
 * 서로 다른 네비게이션 그래프(nav_admin.xml / nav_member.xml)를 로드해서 채운다.
 *
 * [MVVM 변경] 역할 판별(isAdmin)과 로그아웃 처리는 [MainViewModel]로 옮겼다. 이 클래스는
 * NavController/BottomNavigationView/Toolbar 같은 View 객체를 다루는 역할만 담당한다.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // [MVVM 변경] 기존의 `private lateinit var tokenManager: TokenManager` 필드를 대체한다.
    private val viewModel: MainViewModel by viewModels()

    // 카메라로 촬영 중인 임시 파일의 Uri. 카메라 앱에 요청을 보낼 때 저장해뒀다가
    // 결과 콜백(cameraLauncher)에서 읽어 갤러리 저장에 사용한다.
    private var pendingCameraUri: Uri? = null

    // 시스템 카메라 앱을 띄워 pendingCameraUri 위치에 사진을 저장하게 하고, 성공 여부만 돌려받는다.
    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            savePhotoAndShow(uri)
        }
    }

    // API 28 이하에서 갤러리 저장에 필요한 WRITE_EXTERNAL_STORAGE 권한을 요청한다.
    private val storagePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            launchCamera()
        } else {
            Toast.makeText(this, "사진 저장 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // targetSdk 35+부터 강제되는 엣지투엣지 렌더링 대응 — 상태바 높이만큼 툴바 위에
        // 패딩을 줘서 상태바 아이콘과 툴바 타이틀이 겹치지 않게 한다.
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBarInsets.top)
            insets
        }

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHost.navController

        // 역할에 따라 네비게이션 그래프 분기
        // 관리자: 대원 관리/기록 입력/통계, 대원: 내 기록 조회 등 — 화면 구성 자체가 다름
        val navGraph: NavGraph
        val menuRes: Int
        if (viewModel.isAdmin) {
            navGraph = navController.navInflater.inflate(R.navigation.nav_admin)   // 관리자용
            menuRes = R.menu.menu_admin
        } else {
            navGraph = navController.navInflater.inflate(R.navigation.nav_member)  // 대원용
            menuRes = R.menu.menu_member
        }
        navController.graph = navGraph

        // 하단 네비게이션에 역할별 메뉴를 채운 뒤, 탭 클릭 시 위 그래프의 목적지로 자동 전환되도록 연결
        binding.bottomNav.inflateMenu(menuRes)
        binding.bottomNav.setupWithNavController(navController)

        // 툴바 우측 액션 아이콘 — 로그아웃은 공통, "비밀번호 변경"은 대원 전용 메뉴에만 있다
        // (관리자는 관리자 계정을 직접 관리하는 별도 화면이 없어 이번 범위에서 제외했다).
        binding.toolbar.inflateMenu(if (viewModel.isAdmin) R.menu.menu_toolbar else R.menu.menu_toolbar_member)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.actionCamera -> {
                    onCameraMenuClicked()
                    true
                }
                R.id.actionChangePassword -> {
                    startActivity(ChangePasswordActivity.voluntaryIntent(this))
                    true
                }
                R.id.actionLogout -> {
                    // [MVVM 변경] 기존에는 여기서 tokenManager.clear()를 직접 호출했다.
                    viewModel.logout()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    // API 29+는 Scoped Storage 덕분에 권한 없이 갤러리에 쓸 수 있으므로 바로 카메라를 띄우고,
    // API 28 이하는 WRITE_EXTERNAL_STORAGE 권한이 있는지 확인한 뒤 없으면 먼저 요청한다.
    private fun onCameraMenuClicked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            launchCamera()
            return
        }
        val permission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            storagePermissionLauncher.launch(permission)
        }
    }

    // 앱 캐시 폴더에 임시 파일을 만들고, 그 파일의 Uri를 FileProvider로 카메라 앱에 넘겨 촬영을 요청한다.
    private fun launchCamera() {
        val imagesDir = File(cacheDir, "images").apply { mkdirs() }
        val tempFile = File.createTempFile("capture_", ".jpg", imagesDir)
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", tempFile)
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    // 카메라 앱이 임시 파일에 써준 사진을 기기 갤러리(Pictures/훈련모니터링)로 복사 저장한 뒤,
    // 갤러리에 저장된 Uri로 전체화면 보기 화면을 띄운다.
    private fun savePhotoAndShow(tempUri: Uri) {
        lifecycleScope.launch {
            val galleryUri = withContext(Dispatchers.IO) { copyToGallery(tempUri) }
            if (galleryUri != null) {
                startActivity(PhotoViewActivity.newIntent(this@MainActivity, galleryUri))
            } else {
                Toast.makeText(this@MainActivity, "사진 저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyToGallery(sourceUri: Uri): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/훈련모니터링")
            }
        }
        val targetUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        contentResolver.openOutputStream(targetUri)?.use { output ->
            contentResolver.openInputStream(sourceUri)?.use { input -> input.copyTo(output) }
        }
        return targetUri
    }
}
