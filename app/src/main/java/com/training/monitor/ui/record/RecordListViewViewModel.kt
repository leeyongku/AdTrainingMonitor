// 관리자 - 특정 대원의 전체 기록 조회 화면의 상태와 서버 통신을 담당하는 ViewModel

package com.training.monitor.ui.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.training.monitor.data.api.ApiService
import com.training.monitor.data.model.RecordDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [RecordListViewFragment]의 ViewModel. 특정 대원(userId)의 전체 측정 기록 목록을
 * 서버에서 불러와 화면에 노출할 상태([records], [toastMessage])로 보관한다.
 * 실제 기록 목록을 그리는 일(RecyclerView 갱신)은 Fragment의 책임으로 남겨둔다.
 */
@HiltViewModel
class RecordListViewViewModel @Inject constructor(
    application: Application,
    private val apiService: ApiService
) : AndroidViewModel(application) {

    private val _records = MutableLiveData<List<RecordDto>>(emptyList())
    val records: LiveData<List<RecordDto>> = _records

    private val _toastMessage = MutableLiveData<String?>(null)
    val toastMessage: LiveData<String?> = _toastMessage

    /** 지정한 대원(userId)의 전체 측정 기록을 불러와 [records]를 갱신한다. */
    fun loadRecords(userId: Long) {
        viewModelScope.launch {
            try {
                val response = apiService.userRecords(userId)
                _records.value = if (response.isSuccessful) response.body() ?: emptyList() else emptyList()
            } catch (e: Exception) {
                _toastMessage.value = "기록 로딩 실패"
            }
        }
    }

    /** 기록 하나를 삭제하고, 성공 시 목록을 새로고침한다. */
    fun deleteRecord(recordId: Long, userId: Long) {
        viewModelScope.launch {
            try {
                val response = apiService.deleteRecord(recordId)
                if (response.isSuccessful) {
                    _toastMessage.value = "기록을 삭제했습니다."
                    loadRecords(userId)
                } else {
                    _toastMessage.value = "기록 삭제 실패"
                }
            } catch (e: Exception) {
                _toastMessage.value = "서버 연결 실패: ${e.message}"
            }
        }
    }

    /** 지정한 대원(userId)의 전체 측정 기록을 삭제하고, 성공 시 목록을 새로고침한다. */
    fun deleteAllRecords(userId: Long) {
        viewModelScope.launch {
            try {
                val response = apiService.deleteAllUserRecords(userId)
                if (response.isSuccessful) {
                    _toastMessage.value = "전체 기록을 삭제했습니다."
                    loadRecords(userId)
                } else {
                    _toastMessage.value = "전체 삭제 실패"
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
