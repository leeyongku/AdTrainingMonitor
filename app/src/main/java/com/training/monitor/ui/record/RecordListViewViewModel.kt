// 관리자 - 특정 대원의 전체 기록 조회 화면의 상태와 서버 통신을 담당하는 ViewModel

package com.training.monitor.ui.record

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.training.monitor.data.api.RetrofitClient
import com.training.monitor.data.model.RecordDto
import kotlinx.coroutines.launch

class RecordListViewViewModel(application: Application) : AndroidViewModel(application) {

    private val _records = MutableLiveData<List<RecordDto>>(emptyList())
    val records: LiveData<List<RecordDto>> = _records

    private val _toastMessage = MutableLiveData<String?>(null)
    val toastMessage: LiveData<String?> = _toastMessage

    /** 지정한 대원(userId)의 전체 측정 기록을 불러와 [records]를 갱신한다. */
    fun loadRecords(userId: Long) {
        val api = RetrofitClient.create(getApplication())
        viewModelScope.launch {
            try {
                val response = api.userRecords(userId)
                _records.value = if (response.isSuccessful) response.body() ?: emptyList() else emptyList()
            } catch (e: Exception) {
                _toastMessage.value = "기록 로딩 실패"
            }
        }
    }

    fun onToastMessageShown() {
        _toastMessage.value = null
    }
}
