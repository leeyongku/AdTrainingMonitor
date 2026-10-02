// 관리자 - 부대 통계 화면의 상태와 서버 통신을 담당하는 ViewModel

package com.training.monitor.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.training.monitor.data.api.ApiService
import com.training.monitor.data.model.UnitDto
import com.training.monitor.data.model.UnitStatsDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * [StatsFragment]의 ViewModel.
 *
 * [MVVM 변경] 기존에는 Fragment가 조회 기간(dateFrom/dateTo)을 자신의 프로퍼티로 들고 있다가,
 * 통계 API 응답을 받는 즉시 두 차트를 직접 그렸다. ViewModel은 "조회 기간"과 "조회 결과"를
 * 상태로 보관하고, 그 데이터를 실제 PieChart/BarChart로 그리는 일(MPAndroidChart API 호출)은
 * View의 책임이므로 Fragment에 남겨둔다.
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    application: Application,
    private val apiService: ApiService
) : AndroidViewModel(application) {

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // 기본 조회 기간: 최근 3개월
    //MutableLiveData 값이 변경될 수 있는 LiveData객체를 생성
    private val _dateFrom = MutableLiveData(LocalDate.now().minusMonths(3))

    val dateFrom: LiveData<LocalDate> = _dateFrom

    private val _dateTo = MutableLiveData(LocalDate.now())
    val dateTo: LiveData<LocalDate> = _dateTo

    // 전체 부대 목록 (레벨 필터링 전 원본). "대원 추가" 화면과 같은 GET /api/members/units를 재사용한다.
    private var allUnits: List<UnitDto> = emptyList()

    // 현재 선택된 계층(0=여단 ~ 4=분대)에 해당하는 부대만 걸러낸 목록. 부대 스피너가 이 값을 관찰해서
    // 어댑터를 다시 채우고, 저장 시점에는 spinnerUnit.selectedItemPosition으로 이 리스트에서 실제 부대를 찾는다.
    private val _unitsForLevel = MutableLiveData<List<UnitDto>>(emptyList())
    val unitsForLevel: LiveData<List<UnitDto>> = _unitsForLevel

    private val _stats = MutableLiveData<UnitStatsDto?>(null)
    val stats: LiveData<UnitStatsDto?> = _stats

    private val _toastMessage = MutableLiveData<String?>(null)
    val toastMessage: LiveData<String?> = _toastMessage

    init {
        loadUnits()
    }

    /** 부대 목록을 불러온 뒤, 기본 계층(여단)으로 [unitsForLevel]을 채운다. */
    private fun loadUnits() {
        viewModelScope.launch {
            try {
                val response = apiService.getUnits()
                if (response.isSuccessful) {
                    allUnits = response.body().orEmpty()
                    setLevel(0)
                }
            } catch (e: Exception) {
                _toastMessage.value = "부대 목록 로딩 실패"
            }
        }
    }

    /** 계층 스피너 선택이 바뀌면 해당 계층의 부대만 걸러 [unitsForLevel]을 갱신한다. */
    fun setLevel(level: Int) {
        _unitsForLevel.value = allUnits.filter { it.level == level }
    }

    /** 조회 시작일을 변경한다 (재조회는 하지 않음 — 기존 동작대로 "검색" 버튼을 눌러야 반영). */
    fun setDateFrom(date: LocalDate) {
        _dateFrom.value = date
    }

    /** 조회 종료일을 변경한다. */
    fun setDateTo(date: LocalDate) {
        _dateTo.value = date
    }

    /** 지정한 부대(및 하위 부대 전체)와 현재 기간(dateFrom~dateTo)으로 통계 API를 호출해 [stats]를 갱신한다. */
    fun loadStats(unitId: Long) {
        val from = _dateFrom.value ?: return
        val to = _dateTo.value ?: return
        viewModelScope.launch {
            try {
                val response = apiService.unitStats(unitId, from.format(fmt), to.format(fmt))
                if (response.isSuccessful) {
                    _stats.value = response.body()
                }
            } catch (e: Exception) {
                _toastMessage.value = "통계 로딩 실패"
            }
        }
    }

    /** Fragment가 메시지를 Toast로 보여준 뒤 호출한다. */
    fun onToastMessageShown() {
        _toastMessage.value = null
    }
}
