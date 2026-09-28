// 대원 본인 기록 조회 화면의 상태와 서버 통신을 담당하는 ViewModel

package com.training.monitor.ui.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.training.monitor.data.api.RetrofitClient
import com.training.monitor.data.model.RecordDto
import com.training.monitor.data.model.TrendPoint
import kotlinx.coroutines.launch

/**
 * [MyRecordFragment]의 ViewModel.
 *
 * [MVVM 변경] 기존에는 Fragment가 본인 기록/추이 그래프 API를 직접 호출하고, 받은 데이터를
 * 곧바로 TextView/차트에 그렸다. ViewModel은 "조회한 데이터"까지만 LiveData로 보관하고,
 * 그 데이터를 화면에 어떻게 그릴지(요약 텍스트 포맷, 차트 Entry 변환/스타일)는 View의
 * 책임으로 남겨 Fragment에 그대로 둔다.
 */
class MyRecordViewModel(application: Application) : AndroidViewModel(application) {

    // [MVVM 변경] 기존에는 records 응답을 받는 즉시 개수만 텍스트로 만들어 버렸다.
    // 여기서는 원본 리스트 자체를 보관해, "몇 건인지"를 어떻게 표시할지는 Fragment가 결정하게 한다.
    private val _records = MutableLiveData<List<RecordDto>>(emptyList())
    val records: LiveData<List<RecordDto>> = _records

    private val _trendPoints = MutableLiveData<List<TrendPoint>>(emptyList())
    val trendPoints: LiveData<List<TrendPoint>> = _trendPoints

    private val _toastMessage = MutableLiveData<String?>(null)
    val toastMessage: LiveData<String?> = _toastMessage

    /** 본인 기록 전체를 조회해 [records]를 갱신한다. */
    fun loadRecords() {
        val api = RetrofitClient.create(getApplication())
        viewModelScope.launch {
            try {
                val response = api.myRecords()
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
        val api = RetrofitClient.create(getApplication())
        viewModelScope.launch {
            try {
                val response = api.trend(categoryId = categoryId)
                if (response.isSuccessful) {
                    _trendPoints.value = response.body() ?: emptyList()
                }
            } catch (e: Exception) {
                _toastMessage.value = "그래프 로딩 실패"
            }
        }
    }

    /** Fragment가 메시지를 Toast로 보여준 뒤 호출한다. */
    fun onToastMessageShown() {
        _toastMessage.value = null
    }
}
