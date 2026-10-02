// 관리자 - 대원 목록 화면의 상태와 서버 통신을 담당하는 ViewModel

package com.training.monitor.ui.member

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.training.monitor.data.api.ApiService
import com.training.monitor.data.model.CreateMemberRequest
import com.training.monitor.data.model.MemberDto
import com.training.monitor.data.model.ResetPasswordRequest
import com.training.monitor.data.model.UnitDto
import com.training.monitor.data.model.UpdateMemberRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * [MemberListFragment]의 ViewModel.
 *
 * [MVVM 변경] 기존에는 Fragment가 RetrofitClient를 직접 호출하고 결과를 자신의 프로퍼티
 * (`allMembers`)에 저장했다. MVVM에서는 "서버와 통신하고 상태를 들고 있는" 책임을
 * ViewModel로 옮기고, Fragment는 여기서 노출하는 LiveData를 관찰(observe)해 화면만
 * 갱신하는 역할로 축소된다.
 *
 * [MVVM 변경] 대원 사진을 Base64로 인코딩하는 `encodePhotoBase64()` 메서드에서 ContentResolver를 사용해야 하므로 일반 `ViewModel`이
 * 아니라 [AndroidViewModel]을 상속해 Application Context(`getApplication()`)를 사용한다.
 */
@HiltViewModel
class MemberListViewModel @Inject constructor(
    application: Application,
    private val apiService: ApiService
) : AndroidViewModel(application) {

    // [MVVM 변경] 기존 Fragment의 `private var allMembers: List<MemberDto>`를 대체한다.
    // 외부(Fragment)에는 읽기 전용 LiveData만 노출하고, 값 변경은 이 클래스 내부에서만 한다.
    private val _members = MutableLiveData<List<MemberDto>>(emptyList())
    val members: LiveData<List<MemberDto>> = _members

    // [MVVM 변경] 기존에는 Fragment가 각 함수 안에서 바로 Toast.makeText(...)를 호출했다.
    // ViewModel은 Context/View를 알아서는 안 되므로 Toast를 직접 띄울 수 없다 — 대신 "보여줄
    // 메시지"만 LiveData로 흘려보내고, 실제로 Toast를 띄우는 일은 이를 관찰하는 Fragment가 맡는다.
    private val _toastMessage = MutableLiveData<String?>(null)
    val toastMessage: LiveData<String?> = _toastMessage

    // 서버가 인정하는 계급 목록(GET /api/members/ranks) — "대원 추가" 다이얼로그의 계급 스피너가
    // 이 목록으로 채워진다. 자유 입력을 막아, 서버의 등급 산출 로직이 인식하지 못하는 값이
    // 저장되는 것을 원천 차단한다.
    private val _ranks = MutableLiveData<List<String>>(emptyList())
    val ranks: LiveData<List<String>> = _ranks

    // 서버가 인정하는 소속 부대 목록(GET /api/members/units) — "대원 추가" 다이얼로그의 소속 부대 스피너가
    // 이 목록으로 채워진다. 부대 ID를 직접 입력하게 하는 대신 목록에서 고르게 해, 존재하지 않는 ID가
    // 저장되는 것을 막는다.
    private val _units = MutableLiveData<List<UnitDto>>(emptyList())
    val units: LiveData<List<UnitDto>> = _units

    /** 서버에서 대원 목록을 불러와 [members]를 갱신한다. */
    fun loadMembers() {
        // [MVVM 변경] Fragment의 생명주기에 묶인 lifecycleScope 대신 ViewModel의 생명주기에 묶인
        // viewModelScope를 사용한다. 화면 회전 등으로 Fragment의 View가 재생성되어도 ViewModel은
        // 살아있으므로, 이미 시작된 네트워크 요청이 중간에 끊기지 않는다.
        viewModelScope.launch {
            try {
                val response = apiService.getMembers()
                if (response.isSuccessful) {
                    _members.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                _toastMessage.value = "대원 목록 로딩 실패"
            }
        }
    }

    /** 서버에서 등록 가능한 계급 목록을 불러와 [ranks]를 갱신한다. */
    fun loadRanks() {
        viewModelScope.launch {
            try {
                val response = apiService.getRanks()
                if (response.isSuccessful) {
                    _ranks.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                _toastMessage.value = "계급 목록 로딩 실패"
            }
        }
    }

    /** 서버에서 등록 가능한 소속 부대 목록을 불러와 [units]를 갱신한다. */
    fun loadUnits() {
        viewModelScope.launch {
            try {
                val response = apiService.getUnits()
                if (response.isSuccessful) {
                    _units.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                _toastMessage.value = "소속 부대 목록 로딩 실패"
            }
        }
    }

    /**
     * 서버에 신규 대원 등록을 요청하고, 성공 시 목록을 새로고침한다.
     * [photoUri]가 있으면 축소/압축/Base64 인코딩한 뒤 요청에 실어 보낸다. 인코딩은 파일 디코드·압축이
     * 포함된 무거운 작업이라 [Dispatchers.IO]에서 실행해 메인 스레드가 멈추지 않게 한다.
     */
    fun createMember(req: CreateMemberRequest, photoUri: Uri? = null) {
        viewModelScope.launch {
            try {
                val photoBase64 = photoUri?.let { withContext(Dispatchers.IO) { encodePhotoBase64(it) } }
                val response = apiService.createMember(req.copy(photoBase64 = photoBase64))
                if (response.isSuccessful) {
                    _toastMessage.value = "${req.name} 대원을 등록했습니다."
                    loadMembers()
                } else if (response.code() == 409) {
                    _toastMessage.value = "이미 등록된 군번입니다."
                } else {
                    _toastMessage.value = "대원 등록 실패"
                }
            } catch (e: Exception) {
                _toastMessage.value = "서버 연결 실패: ${e.message}"
            }
        }
    }

    /**
     * 카메라 촬영(파일 Uri) 또는 갤러리 선택(콘텐츠 Uri) 결과를 긴 변 480px 이하로 축소하고
     * JPEG 품질 75로 압축해 Base64로 인코딩한다. 목록 아이콘 표시용이라 기록 사진(1280px)보다 작게 줄인다.
     */
    private fun encodePhotoBase64(uri: Uri): String? {
        val resolver = getApplication<Application>().contentResolver

        // inJustDecodeBounds=true 모드에서는 decodeStream이 항상 null을 반환하므로(치수만 bounds에 채움),
        // 반환값이 아니라 bounds.outWidth/outHeight로 디코딩 성공 여부를 판단한다.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > 480 || bounds.outHeight / sampleSize > 480) {
            sampleSize *= 2
        }

        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } ?: return null

        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, output)
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    /**
     * 서버에 기존 대원 정보(이름/계급/소속 부대/얼굴 사진) 수정을 요청하고, 성공 시 목록을 새로고침한다.
     * [photoUri]가 있으면 새 사진으로 교체하고, [removePhoto]가 true면 기존 사진을 지운다
     * (둘 다 지정된 경우 삭제가 우선 적용됨 — [encodePhotoBase64]는 [removePhoto]가 true면 호출하지 않는다).
     */
    fun updateMember(memberId: Long, req: UpdateMemberRequest, photoUri: Uri? = null, removePhoto: Boolean = false) {
        viewModelScope.launch {
            try {
                val photoBase64 = if (removePhoto) null
                    else photoUri?.let { withContext(Dispatchers.IO) { encodePhotoBase64(it) } }
                val response = apiService.updateMember(memberId, req.copy(photoBase64 = photoBase64, removePhoto = removePhoto))
                if (response.isSuccessful) {
                    _toastMessage.value = "${req.name} 정보를 수정했습니다."
                    loadMembers()
                } else {
                    _toastMessage.value = "대원 정보 수정 실패"
                }
            } catch (e: Exception) {
                _toastMessage.value = "서버 연결 실패: ${e.message}"
            }
        }
    }

    /** 서버에 비밀번호 재설정을 요청한다. */
    fun resetPassword(member: MemberDto, newPassword: String) {
        viewModelScope.launch {
            try {
                val response = apiService.resetPassword(member.id, ResetPasswordRequest(newPassword))
                _toastMessage.value = if (response.isSuccessful) {
                    "${member.name}의 비밀번호를 재설정했습니다."
                } else {
                    "비밀번호 재설정 실패"
                }
            } catch (e: Exception) {
                _toastMessage.value = "서버 연결 실패: ${e.message}"
            }
        }
    }

    /** Fragment가 메시지를 Toast로 보여준 뒤 호출한다 — 값을 비워야 화면 회전 시 같은 메시지가 다시 뜨지 않는다. */
    fun onToastMessageShown() {
        _toastMessage.value = null
    }
}
