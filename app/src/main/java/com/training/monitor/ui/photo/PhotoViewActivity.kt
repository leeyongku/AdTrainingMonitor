// 촬영한 사진을 전체화면으로 보여주는 화면 — 로컬 Uri(촬영 직후 미리보기) 또는
// 서버에 저장된 기록의 recordId(사후 열람) 두 방식으로 열 수 있다.

package com.training.monitor.ui.photo

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.training.monitor.data.api.ApiService
import com.training.monitor.databinding.ActivityPhotoViewBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 사진을 전체화면으로 보여주는 화면. 진입 방식이 두 가지라 [onCreate]에서 인텐트에
 * 실려온 extra 종류로 분기한다.
 *
 * 1. **로컬 미리보기** — [newIntent]로 생성. 카메라로 방금 찍었지만 아직 서버에 저장하지
 *    않은 사진을 `content://` [Uri]로 바로 보여준다 (기기 안에 이미 있는 파일이라
 *    네트워크 요청 없이 [android.widget.ImageView.setImageURI]로 즉시 표시 가능).
 * 2. **서버 조회** — [newIntentForRecord]로 생성. 이미 저장된 기록의 사진을 recordId로
 *    받아와야 하므로, [loadPhotoFromServer]가 API를 호출해 바이트를 받아온 뒤 비트맵으로
 *    디코딩해서 보여준다.
 *
 * 두 경우 모두 같은 레이아웃(`activity_photo_view.xml`)과 같은 닫기 버튼을 공유한다.
 */
@AndroidEntryPoint
class PhotoViewActivity : AppCompatActivity() {

    @Inject lateinit var apiService: ApiService

    private lateinit var binding: ActivityPhotoViewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // [엣지투엣지(Edge-to-Edge) 대응]
        // targetSdk 35 이상부터는 시스템이 앱 화면을 상태바/내비게이션바 "뒤"까지 그리도록
        // 강제한다(옵트아웃 불가). 즉 액티비티의 루트 레이아웃이 화면 맨 위(y=0)부터 시작하고,
        // 상태바는 그 위에 반투명하게 "겹쳐서" 그려지는 것뿐이다.
        //
        // 문제: 레이아웃 XML에서 btnClose는 `layout_gravity="top|end"` +
        // `layout_margin="16dp"`로 배치돼 있는데, 이 16dp는 "화면 맨 위(y=0)"를 기준으로
        // 잰 값이다. 엣지투엣지 때문에 화면 맨 위는 곧 상태바가 겹쳐 그려지는 영역이므로,
        // 버튼이 상태바 뒤에 깔려서 상태바가 터치를 먼저 가로채 버튼이 눌리지 않는다.
        //
        // 해결: WindowInsets(시스템 UI가 차지하는 영역 정보)를 받아서, 상태바 높이만큼을
        // 버튼의 "여백(margin)"에 더해준다. 반드시 margin을 바꿔야 한다 — padding을
        // 바꾸면 버튼의 시작 위치(왼쪽 위 모서리)는 그대로인 채 버튼 안쪽 내용만 밀려서
        // 정작 겹쳐 있는 상단 영역은 여전히 클릭 영역으로 남는다. margin은 뷰 자체를
        // 그 자리(여기서는 화면 아래쪽)로 실제로 이동시키므로, 버튼 전체가 상태바 아래
        // 안전한 위치로 내려가 정상적으로 탭이 가능해진다.
        //
        // 리스너는 시스템이 인셋 값을 계산해 알려줄 때(레이아웃 확정 시점, 회전 시에도
        // 다시)마다 호출된다. 매번 호출될 때 "원래 XML에 적혀 있던 16dp"에 그때그때의
        // 상태바 높이를 "더해야" 하므로, 리스너 등록 전에 원래 margin 값을
        // [originalTopMargin]으로 딱 한 번 저장해두고, 콜백 안에서는 항상 이 원본 값 +
        // 상태바 높이로 다시 계산한다 (콜백이 여러 번 불려도 계속 누적되지 않도록).
        val originalTopMargin = (binding.btnClose.layoutParams as ViewGroup.MarginLayoutParams).topMargin
        ViewCompat.setOnApplyWindowInsetsListener(binding.btnClose) { view, insets ->
            // WindowInsetsCompat.Type.statusBars(): 상태바가 차지하는 영역의 크기.
            // .top 값이 곧 상태바의 높이(px)다.
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            (view.layoutParams as ViewGroup.MarginLayoutParams).topMargin = originalTopMargin + statusBarInsets.top
            // layoutParams를 직접 수정한 것만으로는 화면에 바로 반영되지 않으므로,
            // requestLayout()으로 다시 배치(measure/layout)하도록 명시적으로 요청한다.
            view.requestLayout()
            // 콜백은 자신이 "소비하지 않은" insets를 그대로 반환해야, 이 값이 필요한
            // 다른 뷰(있다면)에게도 인셋 정보가 계속 전달된다.
            insets
        }

        binding.btnClose.setOnClickListener { finish() }

        // 인텐트에 어떤 extra가 실려왔는지로 "로컬 미리보기"인지 "서버 조회"인지 판단한다.
        // 두 값이 동시에 오는 경우는 없다 — newIntent()/newIntentForRecord() 둘 중
        // 하나만 항상 사용되므로 둘 중 하나만 세팅되어 들어온다.
        val photoUri = intent.getStringExtra(EXTRA_PHOTO_URI)?.toUri()
        val recordId = intent.getLongExtra(EXTRA_RECORD_ID, -1L)
        when {
            // 로컬 파일은 이미 기기 안에 있으므로 네트워크 없이 즉시 렌더링 가능.
            photoUri != null -> binding.ivPhoto.setImageURI(photoUri)
            // -1L(getLongExtra의 기본값)이 아니라는 것은 실제 recordId가 실려왔다는 뜻.
            recordId != -1L -> loadPhotoFromServer(recordId)
        }
    }

    /**
     * recordId로 `GET /api/records/{id}/photo`를 호출해 사진 원본 바이트를 받아온 뒤,
     * [BitmapFactory]로 디코딩해서 화면에 표시한다.
     *
     * 실패 케이스(네트워크 오류, 서버가 403/404를 응답, 응답 바디가 비었거나 손상돼
     * 디코딩이 안 되는 경우)를 전부 하나의 분기로 묶어 처리한다 — 사용자 입장에서는
     * "사진을 못 봤다"는 사실만 중요하지 정확한 실패 사유는 구분할 필요가 없기 때문이다.
     * 실패 시 안내 토스트를 띄우고 화면을 닫아, 검은 화면만 남는 상황을 피한다.
     */
    private fun loadPhotoFromServer(recordId: Long) {
        // lifecycleScope: 이 Activity의 생명주기에 묶인 코루틴 스코프. Activity가
        // 파괴되면 진행 중이던 코루틴도 자동으로 취소되어, 이미 없어진 화면의
        // View에 접근하려다 나는 오류(예: 회전 중 응답이 늦게 와서 binding이
        // 무효화된 뒤 접근하는 상황)를 방지해준다.
        lifecycleScope.launch {
            val bitmap = try {
                // apiService: Hilt가 @Inject lateinit var로 주입한 싱글톤(NetworkModule 제공).
                val response = apiService.recordPhoto(recordId)
                // HTTP 자체가 실패(403 권한 없음, 404 사진 없음 등)했으면 바이트를 읽지 않는다.
                val bytes = if (response.isSuccessful) response.body()?.bytes() else null
                // 바이트가 있어도 JPEG로서 유효하지 않으면 decodeByteArray가 null을 반환한다.
                bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            } catch (e: Exception) {
                // 네트워크 자체가 끊기는 등 요청이 아예 실패한 경우도 "사진 없음"과
                // 동일하게 취급한다.
                null
            }

            if (bitmap != null) {
                binding.ivPhoto.setImageBitmap(bitmap)
            } else {
                Toast.makeText(this@PhotoViewActivity, "사진을 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        // 두 진입 방식을 구분하는 인텐트 extra 키. private으로 감춰서 호출부(다른 화면들)가
        // 이 키 문자열을 직접 다루지 않고, 항상 아래 팩토리 함수를 통해서만 인텐트를
        // 만들도록 강제한다 (오타로 인한 키 불일치를 원천 차단).
        private const val EXTRA_PHOTO_URI = "photoUri"
        private const val EXTRA_RECORD_ID = "recordId"

        /**
         * 아직 서버에 저장하지 않은 로컬 촬영 파일을 바로 보여줄 때 사용한다.
         * 기록 입력 화면(RecordInputFragment)에서 사진을 찍은 직후, 저장 버튼을
         * 누르기 전에 미리보기로 열 때 쓰인다.
         */
        fun newIntent(context: Context, photoUri: Uri): Intent =
            Intent(context, PhotoViewActivity::class.java)
                .putExtra(EXTRA_PHOTO_URI, photoUri.toString())

        /**
         * 서버에 이미 저장된 기록의 사진을 recordId로 받아와 보여줄 때 사용한다.
         * 대원의 "내 기록" 화면, 관리자의 "대원 기록 보기" 화면 양쪽에서 사진 아이콘을
         * 탭했을 때 이 함수로 열린다.
         */
        fun newIntentForRecord(context: Context, recordId: Long): Intent =
            Intent(context, PhotoViewActivity::class.java)
                .putExtra(EXTRA_RECORD_ID, recordId)
    }
}
