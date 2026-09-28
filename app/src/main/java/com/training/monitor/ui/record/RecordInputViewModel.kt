// 관리자 - 체력 측정 기록 입력 화면의 상태와 서버 통신을 담당하는 ViewModel

package com.training.monitor.ui.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.training.monitor.data.api.RetrofitClient
import com.training.monitor.data.model.GradeCriteriaDto
import com.training.monitor.data.model.MemberDto
import com.training.monitor.data.model.RecordRequest
import com.training.monitor.data.model.SessionDto
import com.training.monitor.data.model.SessionRequest
import kotlinx.coroutines.launch

/**
 * [RecordInputFragment]의 ViewModel.
 *
 * [MVVM 변경] 기존에는 Fragment가 세션/대원 목록을 직접 조회해 스피너 어댑터에 꽂고,
 * 등급 미리보기 계산과 저장 API 호출까지 전부 담당했다. ViewModel은 목록 데이터 보관,
 * 저장 요청, 그리고 "등급 미리보기 계산"처럼 View를 몰라도 되는 순수 판정 로직까지 맡고,
 * 스피너 어댑터 구성이나 텍스트 색상 같은 실제 화면 렌더링만 Fragment에 남긴다.
 */
class RecordInputViewModel(application: Application) : AndroidViewModel(application) {

    private val _sessions = MutableLiveData<List<SessionDto>>(emptyList())
    val sessions: LiveData<List<SessionDto>> = _sessions

    private val _members = MutableLiveData<List<MemberDto>>(emptyList())
    val members: LiveData<List<MemberDto>> = _members

    private val _toastMessage = MutableLiveData<String?>(null)
    val toastMessage: LiveData<String?> = _toastMessage

    // [MVVM 변경] 저장 성공 시 "입력 필드 초기화"는 View의 일이므로, ViewModel은 성공 여부만
    // 일회성 이벤트로 알리고 실제 EditText.clear()는 Fragment가 수행한다.
    private val _saveSuccess = MutableLiveData(false)
    val saveSuccess: LiveData<Boolean> = _saveSuccess

    // 현재 선택된 대원·종목에 실제로 적용되는 등급 기준표(계급군 반영 + 공통 기준 폴백까지 서버가
    // 처리해서 내려줌). [previewGrade]가 이 값을 그대로 순서대로 매칭해 미리보기를 계산한다.
    private val _gradeCriteria = MutableLiveData<List<GradeCriteriaDto>>(emptyList())
    val gradeCriteria: LiveData<List<GradeCriteriaDto>> = _gradeCriteria

    /** 측정 세션 목록을 불러와 [sessions]를 갱신한다. */
    fun loadSessions() {
        val api = RetrofitClient.create(getApplication())
        viewModelScope.launch {
            try {
                val response = api.getSessions()
                if (response.isSuccessful) {
                    _sessions.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                _toastMessage.value = "세션 로딩 실패"
            }
        }
    }

    /** 소속 부대 대원 목록을 불러와 [members]를 갱신한다. */
    fun loadMembers() {
        val api = RetrofitClient.create(getApplication())
        viewModelScope.launch {
            try {
                val response = api.getMembers()
                if (response.isSuccessful) {
                    _members.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                _toastMessage.value = "대원 목록 로딩 실패"
            }
        }
    }

    /**
     * 새 측정 세션을 생성하고, 성공하면 [sessions] 목록을 새로고침한다.
     * unitId를 비워서 보내면 서버가 요청한 관리자의 소속 부대로 자동 설정한다.
     */
    fun createSession(measuredAt: String, location: String?, note: String?) {
        val req = SessionRequest(unitId = null, measuredAt = measuredAt, location = location, note = note)
        val api = RetrofitClient.create(getApplication())
        viewModelScope.launch {
            try {
                val response = api.createSession(req)
                if (response.isSuccessful) {
                    _toastMessage.value = "측정 세션을 추가했습니다."
                    loadSessions()
                } else {
                    _toastMessage.value = "세션 추가 실패"
                }
            } catch (e: Exception) {
                _toastMessage.value = "오류: ${e.message}"
            }
        }
    }

    /**
     * 선택된 대원·종목에 실제로 적용되는 등급 기준표(서버의 GradeCriteria 테이블 값)를 불러와
     * [gradeCriteria]를 갱신한다. 종목이나 대원 선택이 바뀔 때마다 Fragment가 호출한다.
     */
    fun loadGradeCriteria(categoryId: Long, userId: Long) {
        val api = RetrofitClient.create(getApplication())
        viewModelScope.launch {
            try {
                val response = api.gradeCriteria(categoryId, userId)
                _gradeCriteria.value = if (response.isSuccessful) response.body() ?: emptyList() else emptyList()
            } catch (e: Exception) {
                _gradeCriteria.value = emptyList()
            }
        }
    }

    /**
     * 입력값을 [gradeCriteria]의 기준(서버가 SortOrder순으로 내려준 순서)과 매칭해 예상 등급을
     * 계산한다 (순수 함수). 서버 저장 없이 즉각적인 피드백을 주기 위한 용도이며, 최종 등급은
     * 저장 시 서버 응답값을 신뢰한다.
     *
     * [MVVM 변경] 기존에는 이 계산과 동시에 `binding.tvGradePreview`의 텍스트/색상까지 이 함수
     * 안에서 직접 바꿨다. 여기서는 "등급 문자열"만 반환하고, 그것을 어떤 색으로 보여줄지는
     * View의 스타일 관심사이므로 Fragment에 남겨둔다.
     */
    fun previewGrade(value: Double?): String {
        if (value == null) return "-"
        val criteria = _gradeCriteria.value ?: emptyList()
        return criteria.firstOrNull { c ->
            (c.minValue == null || value >= c.minValue) && (c.maxValue == null || value <= c.maxValue)
        }?.grade ?: "-"
    }

    /** 선택된 세션/대원/종목과 입력값으로 기록 저장 API를 호출한다. photoBase64가 있으면 함께 전송한다. */
    fun saveRecord(sessionId: Long, userId: Long, categoryId: Long, value: Double, note: String?, photoBase64: String? = null) {
        val req = RecordRequest(sessionId, userId, categoryId, value, note, photoBase64)
        val api = RetrofitClient.create(getApplication())
        viewModelScope.launch {
            try {
                val response = api.createRecord(req)
                if (response.isSuccessful) {
                    // 서버가 산출한 최종 등급을 응답 바디에서 꺼내 안내 메시지에 표시
                    val grade = response.body()?.get("grade")
                    _toastMessage.value = "저장 완료 (등급: $grade)"
                    _saveSuccess.value = true
                } else {
                    _toastMessage.value = "저장 실패"
                }
            } catch (e: Exception) {
                _toastMessage.value = "오류: ${e.message}"
            }
        }
    }

    /** Fragment가 메시지를 Toast로 보여준 뒤 호출한다. */
    fun onToastMessageShown() {
        _toastMessage.value = null
    }

    /** Fragment가 입력 필드 초기화를 마친 뒤 호출한다. */
    fun onSaveHandled() {
        _saveSuccess.value = false
    }
}
