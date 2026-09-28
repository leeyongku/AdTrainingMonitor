// 관리자 - 대원 목록 조회 화면

package com.training.monitor.ui.member

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.training.monitor.R
import com.training.monitor.data.model.CreateMemberRequest
import com.training.monitor.data.model.MemberDto
import com.training.monitor.databinding.DialogAddMemberBinding
import com.training.monitor.databinding.FragmentMemberListBinding
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController

/**
 * 관리자 전용 화면: 소속 부대 대원 전체 목록을 조회하고 이름/군번으로 검색한다.
 * (대원 등록/비활성화는 이 화면에서 트리거만 하고, 실제 처리는 서버의
 * MembersController가 담당 — 관리자 권한이 없으면 서버가 403을 반환한다.)
 *
 * [MVVM 변경] 이 클래스는 이제 "화면을 그리고 사용자 입력을 받는 View" 역할만 한다.
 * 서버 통신과 상태 보관은 [MemberListViewModel]이 담당하고, 여기서는 그 결과를
 * 관찰(observe)해 화면에 반영하기만 한다.
 */
class MemberListFragment : Fragment() {

    private var _binding: FragmentMemberListBinding? = null
    private val binding get() = _binding!!

    private val adapter = MemberAdapter()

    // [MVVM 변경] 화면 회전 등으로 Fragment가 재생성돼도 데이터가 유지되도록, 기존에 Fragment가
    // 직접 들고 있던 `allMembers` 필드와 네트워크 호출 함수들을 이 ViewModel로 옮겼다.
    // by viewModels()는 이 Fragment의 생명주기에 맞는 ViewModel 인스턴스를 만들어(또는 재사용해)준다.
    private val viewModel: MemberListViewModel by viewModels()

    // [MVVM 변경] MemberAdapter 자체는 상태가 없어 실제로 쓰이진 않지만, "화면 컴포넌트마다
    // 대응하는 ViewModel을 둔다"는 프로젝트 컨벤션에 맞춰 생성해 보관한다.
    private val adapterViewModel: MemberAdapterViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMemberListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvMembers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMembers.adapter = adapter
        adapter.onItemClick = { member -> showResetPasswordDialog(member) }
        adapter.onRecordsClick = { member ->
            findNavController().navigate(
                R.id.action_memberListFragment_to_recordListViewFragment,
                bundleOf(
                    "userId" to member.id,
                    "memberName" to "${member.rank ?: ""} ${member.name}".trim()
                )
            )
        }
        binding.fabAddMember.setOnClickListener { showAddMemberDialog() }

        // 검색 필터 — 입력할 때마다 서버 재호출 없이 viewModel.members의 최신값을 이름/군번 기준으로 필터링
        binding.etSearch.addTextChangedListener { text ->
            val query = text.toString().trim()
            // [MVVM 변경] 기존에는 Fragment의 `allMembers` 필드를 바로 읽었지만,
            // 이제 원본 목록은 ViewModel이 들고 있으므로 viewModel.members.value에서 읽어온다.
            val allMembers = viewModel.members.value ?: emptyList()
            val filtered = if (query.isEmpty()) allMembers
            else allMembers.filter { it.name.contains(query) || it.militaryId.contains(query) }
            adapter.submitList(filtered)
        }

        // [MVVM 변경] 기존에는 loadMembers()가 응답을 받는 시점에 어댑터/카운트 텍스트를 직접 갱신했다.
        // 이제는 viewModel.members를 관찰하기만 하면, 최초 로딩이든 이후 재조회(대원 등록 성공 후 등)든
        // 값이 바뀔 때마다 이 콜백 하나로 자동 처리된다.
        viewModel.members.observe(viewLifecycleOwner) { members ->
            adapter.submitList(members)
            binding.tvMemberCount.text = "총 ${members.size}명"
        }

        // [MVVM 변경] 기존에는 각 함수 안에서 Toast.makeText(...)를 직접 호출했다. 이제는 ViewModel이
        // "보여줄 메시지"만 LiveData로 넘겨주고, Fragment가 그 값을 관찰해 Toast로 띄운 뒤
        // onToastMessageShown()으로 값을 비운다 (비우지 않으면 화면 회전 시 같은 메시지가 다시 뜬다).
        viewModel.toastMessage.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                viewModel.onToastMessageShown()
            }
        }

        viewModel.loadMembers()
        viewModel.loadRanks()
        viewModel.loadUnits()
    }

    /** 군번/이름/초기 비밀번호 등을 입력받아 신규 대원을 등록하는 다이얼로그를 띄운다. */
    private fun showAddMemberDialog() {
        val dialogBinding = DialogAddMemberBinding.inflate(layoutInflater)

        // 계급 스피너 — 자유 입력 대신 서버가 인정하는 계급 목록(viewModel.ranks)에서만 고르게 한다.
        // 0번 항목("선택 안함")은 무계급(rank=null)을 의미한다.
        val ranks = viewModel.ranks.value ?: emptyList()
        val rankOptions = listOf("선택 안함") + ranks
        dialogBinding.spinnerRank.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, rankOptions
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // 소속 부대 스피너 — 부대 ID 직접 입력 대신 서버가 인정하는 부대 목록(viewModel.units)에서만 고르게 한다.
        // 0번 항목("선택 안함")은 무소속(unitId=null)을 의미한다.
        val units = viewModel.units.value ?: emptyList()
        val unitNames = listOf("선택 안함") + units.map { it.name }
        dialogBinding.spinnerUnit.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, unitNames
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        AlertDialog.Builder(requireContext())
            .setTitle("대원 추가")
            .setView(dialogBinding.root)
            .setPositiveButton("등록") { _, _ ->
                val militaryId = dialogBinding.etMilitaryId.text.toString().trim()
                val name = dialogBinding.etName.text.toString().trim()
                val password = dialogBinding.etPassword.text.toString()
                val rankIdx = dialogBinding.spinnerRank.selectedItemPosition
                val rank = if (rankIdx <= 0) null else rankOptions[rankIdx]
                val unitIdx = dialogBinding.spinnerUnit.selectedItemPosition
                val unitId = if (unitIdx <= 0) null else units[unitIdx - 1].id

                if (militaryId.isEmpty() || name.isEmpty() || password.isEmpty()) {
                    Toast.makeText(requireContext(), "군번, 이름, 비밀번호는 필수입니다.", Toast.LENGTH_SHORT).show()
                } else {
                    // [MVVM 변경] 기존에는 Fragment의 createMember()가 직접 API를 호출했다.
                    // 이제는 ViewModel에 요청만 위임하고, 결과(성공/실패 메시지, 목록 갱신)는
                    // 위에서 등록한 observe 콜백들이 알아서 처리한다.
                    viewModel.createMember(CreateMemberRequest(militaryId, name, password, rank, unitId))
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /** 대원 항목 클릭 시 새 비밀번호를 입력받아 서버에 재설정을 요청하는 다이얼로그를 띄운다. */
    private fun showResetPasswordDialog(member: MemberDto) {
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "새 비밀번호"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("${member.name} 비밀번호 재설정")
            .setView(input)
            .setPositiveButton("확인") { _, _ ->
                val newPassword = input.text.toString()
                if (newPassword.isEmpty()) {
                    Toast.makeText(requireContext(), "새 비밀번호를 입력하세요.", Toast.LENGTH_SHORT).show()
                } else {
                    // [MVVM 변경] 기존 Fragment의 resetPassword() 대신 ViewModel에 위임한다.
                    viewModel.resetPassword(member, newPassword)
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Fragment의 View가 파괴된 뒤에도 binding을 들고 있으면 메모리 누수가 나므로 null로 해제
        _binding = null
    }
}
