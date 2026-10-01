// MemberAdapter와 짝을 맞추기 위한 ViewModel (프로젝트 MVVM 컨벤션 일관성 목적)

package com.training.monitor.ui.member

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * [MemberAdapter]에 대응하는 ViewModel.
 *
 * [MVVM 변경] `RecyclerView.Adapter`는 데이터를 받아 화면에 그리기만 하는 순수 View 계층
 * 컴포넌트라 원래 MVVM에서도 자체 ViewModel을 갖지 않는다 — 목록 데이터/상태는 이미
 * [MemberListViewModel]이 갖고 있고, Adapter는 [MemberListFragment]가 `submitList()`로
 * 넘겨주는 값을 그리기만 한다. 이 클래스는 실제로 보관하는 상태가 없으며, "ui 폴더의 모든
 * 화면 컴포넌트에 대응하는 ViewModel을 둔다"는 프로젝트 컨벤션을 일관되게 유지하기 위한
 * 목적으로만 존재한다.
 */
@HiltViewModel
class MemberAdapterViewModel @Inject constructor() : ViewModel()
