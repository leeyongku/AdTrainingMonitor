// MainActivity2와 짝을 맞추기 위한 ViewModel (프로젝트 MVVM 컨벤션 일관성 목적)

package com.training.monitor.ui.main2

import androidx.lifecycle.ViewModel

/**
 * [MainActivity2]에 대응하는 ViewModel.
 *
 * [MVVM 변경] MainActivity2는 AndroidManifest에 등록되어 있지 않아 앱의 실제 화면 흐름에서는
 * 실행되지 않는, Android Studio 프로젝트 생성 시 만들어진 기본 템플릿 잔재(죽은 코드)다.
 * 버튼 클릭 시 자기 자신을 재실행하는 것 외에 서버 통신이나 보관할 상태가 전혀 없어 이
 * ViewModel도 실제로는 비어 있다. "ui 폴더의 모든 화면 컴포넌트에 대응하는 ViewModel을
 * 둔다"는 프로젝트 컨벤션을 일관되게 유지하기 위한 목적으로만 존재한다.
 */
class MainActivity2ViewModel : ViewModel()
