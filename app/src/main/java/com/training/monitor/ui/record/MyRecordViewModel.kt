// 대원 본인 기록 조회 화면의 상태와 서버 통신을 담당하는 ViewModel

package com.training.monitor.ui.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.training.monitor.data.api.ApiService
import com.training.monitor.data.model.MyInfoDto
import com.training.monitor.data.model.RecordDto
import com.training.monitor.data.model.TrendPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [MyRecordFragment]의 ViewModel.
 *
 * [MVVM 변경] 기존에는 Fragment가 본인 기록/추이 그래프 API를 직접 호출하고, 받은 데이터를
 * 곧바로 TextView/차트에 그렸다. ViewModel은 "조회한 데이터"까지만 LiveData로 보관하고,
 * 그 데이터를 화면에 어떻게 그릴지(요약 텍스트 포맷, 차트 Entry 변환/스타일)는 View의
 * 책임으로 남겨 Fragment에 그대로 둔다.
 */
@HiltViewModel
class MyRecordViewModel @Inject constructor(
    application: Application,
    private val apiService: ApiService
) : AndroidViewModel(application) {

    // [MVVM 변경] 기존에는 records 응답을 받는 즉시 개수만 텍스트로 만들어 버렸다.
    // 여기서는 원본 리스트 자체를 보관해, "몇 건인지"를 어떻게 표시할지는 Fragment가 결정하게 한다.
    private val _records = MutableLiveData<List<RecordDto>>(emptyList())
    val records: LiveData<List<RecordDto>> = _records

    private val _trendPoints = MutableLiveData<List<TrendPoint>>(emptyList())
    val trendPoints: LiveData<List<TrendPoint>> = _trendPoints

    private val _toastMessage = MutableLiveData<String?>(null)
    val toastMessage: LiveData<String?> = _toastMessage

    // 화면 상단에 "누구의 기록인지" 표시하기 위한 본인 이름/군번/계급.
    private val _myInfo = MutableLiveData<MyInfoDto?>(null)
    val myInfo: LiveData<MyInfoDto?> = _myInfo

    /** 로그인한 본인의 이름/군번/계급을 조회해 [myInfo]를 갱신한다. */
    fun loadMyInfo() {
        viewModelScope.launch {
            try {
                val response = apiService.getMyInfo()
                if (response.isSuccessful) {
                    _myInfo.value = response.body()
                }
            } catch (e: Exception) {
                _toastMessage.value = "내 정보 로딩 실패"
            }
        }
    }

    /** 본인 기록 전체를 조회해 [records]를 갱신한다. */
    fun loadRecords() {
        viewModelScope.launch {
            try {
                val response = apiService.myRecords()
                if (response.isSuccessful) {
                    _records.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                _toastMessage.value = "기록 로딩 실패"
            }
        }
    }

    /**
     * 지정한 종목의 측정값 추이를 조회해 [trendPoints]를 갱신한다.
     * @param categoryId 종목 ID (1: 3km 달리기, 2: 팔굽혀펴기, 3: 윗몸일으키기)
     */
    fun loadTrend(categoryId: Long) {
        viewModelScope.launch {
            try {
                val response = apiService.trend(categoryId = categoryId)
                if (response.isSuccessful) {
                    _trendPoints.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                _toastMessage.value = "그래프 로딩 실패"
            }
        }
    }

    /** 내 측정 기록을 전부 삭제하고, 성공 시 목록을 새로고침한다. */
    fun deleteAllMyRecords() {
        viewModelScope.launch {
            try {
                val response = apiService.deleteMyRecords()
                if (response.isSuccessful) {
                    _toastMessage.value = "전체 기록을 삭제했습니다."
                    loadRecords()
                } else {
                    _toastMessage.value = "전체 삭제 실패"
                }
            } catch (e: Exception) {
                _toastMessage.value = "서버 연결 실패: ${e.message}"
            }
        }
    }

    /** 내 측정 기록 중 특정 종목만 전부 삭제하고, 성공 시 목록을 새로고침한다. */
    fun deleteMyRecordsByCategory(categoryId: Long) {
        viewModelScope.launch {
            try {
                val response = apiService.deleteMyRecordsByCategory(categoryId)
                if (response.isSuccessful) {
                    _toastMessage.value = "선택한 종목의 기록을 삭제했습니다."
                    loadRecords()
                } else {
                    _toastMessage.value = "종목 기록 삭제 실패"
                }
            } catch (e: Exception) {
                _toastMessage.value = "서버 연결 실패: ${e.message}"
            }
        }
    }

    /** Fragment가 메시지를 Toast로 보여준 뒤 호출한다. */
    fun onToastMessageShown() {
        _toastMessage.value = null
    }
}
