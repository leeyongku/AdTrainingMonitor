// 관리자 - 대원 목록 조회 화면

package com.training.monitor.ui.member

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.training.monitor.R
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.training.monitor.data.model.CreateMemberRequest
import com.training.monitor.data.model.MemberDto
import com.training.monitor.data.model.UpdateMemberRequest
import com.training.monitor.databinding.DialogAddMemberBinding
import com.training.monitor.databinding.DialogDeleteMemberBinding
import com.training.monitor.databinding.DialogEditMemberBinding
import com.training.monitor.databinding.FragmentMemberListBinding
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

/**
 * 관리자 전용 화면: 소속 부대 대원 전체 목록을 조회하고 이름/군번으로 검색한다.
 * (대원 등록/비활성화는 이 화면에서 트리거만 하고, 실제 처리는 서버의
 * MembersController가 담당 — 관리자 권한이 없으면 서버가 403을 반환한다.)
 *
 * [MVVM 변경] 이 클래스는 이제 "화면을 그리고 사용자 입력을 받는 View" 역할만 한다.
 * 서버 통신과 상태 보관은 [MemberListViewModel]이 담당하고, 여기서는 그 결과를
 * 관찰(observe)해 화면에 반영하기만 한다.
 */
@AndroidEntryPoint
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

    // "대원 추가" 다이얼로그가 떠 있는 동안에만 유효한 사진 미리보기 참조. 카메라/갤러리 액티비티가
    // 떠 있는 동안에도 다이얼로그 자체는 살아있으므로, 결과 콜백에서 이 참조로 미리보기를 갱신한다.
    // 다이얼로그가 닫히면 null로 해제해 잘못된 화면을 건드리지 않게 한다.
    private var addMemberDialogBinding: DialogAddMemberBinding? = null

    // "대원 수정" 다이얼로그가 떠 있는 동안에만 유효한 참조. addMemberDialogBinding과 동일한 이유로 둔다.
    // 추가/수정 다이얼로그는 동시에 뜨지 않으므로, 카메라/갤러리 결과 콜백은 이 중 null이 아닌 쪽을 갱신한다.
    private var editMemberDialogBinding: DialogEditMemberBinding? = null

    // 촬영/선택된 얼굴 사진의 최종 Uri. 다이얼로그의 "등록"/"저장" 버튼을 누를 때 이 값을 ViewModel에 넘긴다.
    private var selectedMemberPhotoUri: Uri? = null

    // "대원 수정" 다이얼로그에서 "사진 삭제"를 눌렀는지 여부. true면 selectedMemberPhotoUri와 무관하게
    // 서버에 기존 사진 삭제를 요청한다.
    private var editRemovePhoto: Boolean = false

    // launchMemberCamera()가 만들어둔 "촬영 시도 중" 파일/uri. 카메라가 성공을 돌려줘야 selectedMemberPhotoUri로 승격된다.
    private var candidateMemberPhotoFile: File? = null
    private var candidateMemberPhotoUri: Uri? = null

    private val memberCameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            selectedMemberPhotoUri = candidateMemberPhotoUri
            editRemovePhoto = false
            addMemberDialogBinding?.ivMemberPhotoPreview?.setImageURI(selectedMemberPhotoUri)
            editMemberDialogBinding?.ivEditMemberPhotoPreview?.setImageURI(selectedMemberPhotoUri)
        } else {
            candidateMemberPhotoFile?.delete()
        }
        candidateMemberPhotoFile = null
        candidateMemberPhotoUri = null
    }

    private val memberGalleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedMemberPhotoUri = uri
            editRemovePhoto = false
            addMemberDialogBinding?.ivMemberPhotoPreview?.setImageURI(uri)
            editMemberDialogBinding?.ivEditMemberPhotoPreview?.setImageURI(uri)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMemberListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvMembers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvMembers.adapter = adapter
        // 행 전체를 탭하면 그 대원의 기록 보기 화면으로 이동한다.
        adapter.onItemClick = { member ->
            findNavController().navigate(
                R.id.action_memberListFragment_to_myRecordFragment,
                bundleOf(
                    "userId" to member.id,
                    "memberName" to "${member.rank ?: ""} ${member.name}".trim(),
                    "memberMilitaryId" to member.militaryId
                )
            )
        }
        // 열쇠 모양 아이콘을 탭하면 비밀번호 재설정 다이얼로그를 띄운다.
        adapter.onResetPasswordClick = { member -> showResetPasswordDialog(member) }
        // 연필 모양 아이콘을 탭하면 이름/계급/부대/사진 수정 다이얼로그를 띄운다.
        adapter.onEditClick = { member -> showEditMemberDialog(member) }
        // 휴지통 모양 아이콘을 탭하면 숨기기/완전 삭제 중 하나를 고르는 다이얼로그를 띄운다.
        adapter.onDeleteClick = { member -> showDeleteMemberChoiceDialog(member) }
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

    /** 군번/이름/초기 비밀번호/계급/소속 부대(필수)와 얼굴 사진(선택)을 입력받아 신규 대원을 등록하는 다이얼로그를 띄운다. */
    private fun showAddMemberDialog() {
        val dialogBinding = DialogAddMemberBinding.inflate(layoutInflater)
        addMemberDialogBinding = dialogBinding
        editMemberDialogBinding = null
        selectedMemberPhotoUri = null

        // 계급 스피너 — 자유 입력 대신 서버가 인정하는 계급 목록(viewModel.ranks)에서만 고르게 한다.
        // 계급은 필수 항목이라 "선택 안함" 옵션 없이 항상 실제 계급 중 하나가 선택돼 있다.
        val ranks = viewModel.ranks.value ?: emptyList()
        dialogBinding.spinnerRank.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, ranks
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // 소속 부대 스피너 — 부대 ID 직접 입력 대신 서버가 인정하는 부대 목록(viewModel.units)에서만 고르게 한다.
        // 소속 부대도 필수 항목이라 "선택 안함" 옵션 없이 항상 실제 부대 중 하나가 선택돼 있다.
        val units = viewModel.units.value ?: emptyList()
        dialogBinding.spinnerUnit.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, units.map { it.name }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        dialogBinding.btnMemberCamera.setOnClickListener { launchMemberCamera() }
        dialogBinding.btnMemberGallery.setOnClickListener { memberGalleryLauncher.launch("image/*") }

        AlertDialog.Builder(requireContext())
            .setTitle("대원 추가")
            .setView(dialogBinding.root)
            .setPositiveButton("등록") { _, _ ->
                val militaryId = dialogBinding.etMilitaryId.text.toString().trim()
                val name = dialogBinding.etName.text.toString().trim()
                val password = dialogBinding.etPassword.text.toString()
                val rank = ranks.getOrNull(dialogBinding.spinnerRank.selectedItemPosition)
                val unitId = units.getOrNull(dialogBinding.spinnerUnit.selectedItemPosition)?.id

                if (militaryId.isEmpty() || name.isEmpty() || password.isEmpty()) {
                    Toast.makeText(requireContext(), "군번, 이름, 비밀번호는 필수입니다.", Toast.LENGTH_SHORT).show()
                } else if (rank == null || unitId == null) {
                    Toast.makeText(requireContext(), "계급과 소속 부대는 필수입니다.", Toast.LENGTH_SHORT).show()
                } else {
                    // [MVVM 변경] 기존에는 Fragment의 createMember()가 직접 API를 호출했다.
                    // 이제는 ViewModel에 요청만 위임하고, 결과(성공/실패 메시지, 목록 갱신)는
                    // 위에서 등록한 observe 콜백들이 알아서 처리한다.
                    viewModel.createMember(
                        CreateMemberRequest(militaryId, name, password, rank, unitId),
                        selectedMemberPhotoUri
                    )
                }
            }
            .setNegativeButton("취소", null)
            .setOnDismissListener { addMemberDialogBinding = null }
            .show()
    }

    /** 앱 캐시 폴더에 임시 파일을 만들고, FileProvider로 카메라 앱에 대원 얼굴 사진 촬영을 요청한다. */
    private fun launchMemberCamera() {
        val imagesDir = File(requireContext().cacheDir, "images").apply { mkdirs() }
        val file = File.createTempFile("member_", ".jpg", imagesDir)
        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
        candidateMemberPhotoFile = file
        candidateMemberPhotoUri = uri
        memberCameraLauncher.launch(uri)
    }

    /**
     * 연필 아이콘 클릭 시 이름/계급/소속 부대/얼굴 사진을 수정하는 다이얼로그를 띄운다.
     * 군번/비밀번호는 이 다이얼로그로 바꿀 수 없다 (군번은 로그인 식별자, 비밀번호는 별도 기능).
     */
    private fun showEditMemberDialog(member: MemberDto) {
        val dialogBinding = DialogEditMemberBinding.inflate(layoutInflater)
        editMemberDialogBinding = dialogBinding
        addMemberDialogBinding = null
        selectedMemberPhotoUri = null
        editRemovePhoto = false

        dialogBinding.etEditName.setText(member.name)

        // 계급 스피너 — 현재 계급이 목록에 있으면 그 위치를 기본 선택값으로 맞춰준다.
        val ranks = viewModel.ranks.value ?: emptyList()
        dialogBinding.spinnerEditRank.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, ranks
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        ranks.indexOf(member.rank).takeIf { it >= 0 }?.let { dialogBinding.spinnerEditRank.setSelection(it) }

        // 소속 부대 스피너 — MemberDto는 unitId 없이 전체 경로 이름(unitName)만 갖고 있으므로,
        // units 목록에서 같은 이름을 찾아 그 위치를 기본 선택값으로 맞춰준다.
        val units = viewModel.units.value ?: emptyList()
        dialogBinding.spinnerEditUnit.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, units.map { it.name }
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        units.indexOfFirst { it.name == member.unitName }.takeIf { it >= 0 }
            ?.let { dialogBinding.spinnerEditUnit.setSelection(it) }

        // 기존 사진이 있으면 미리보기에 미리 띄워 둔다 — 아무것도 안 건드리면 이 사진이 그대로 유지된다.
        if (member.photoBase64 != null) {
            val bytes = Base64.decode(member.photoBase64, Base64.NO_WRAP)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            dialogBinding.ivEditMemberPhotoPreview.setImageBitmap(bitmap)
        }

        dialogBinding.btnEditMemberCamera.setOnClickListener { launchMemberCamera() }
        dialogBinding.btnEditMemberGallery.setOnClickListener { memberGalleryLauncher.launch("image/*") }
        dialogBinding.btnEditMemberRemovePhoto.setOnClickListener {
            editRemovePhoto = true
            selectedMemberPhotoUri = null
            dialogBinding.ivEditMemberPhotoPreview.setImageDrawable(null)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("${member.name} 정보 수정")
            .setView(dialogBinding.root)
            .setPositiveButton("저장") { _, _ ->
                val name = dialogBinding.etEditName.text.toString().trim()
                val rank = ranks.getOrNull(dialogBinding.spinnerEditRank.selectedItemPosition)
                val unitId = units.getOrNull(dialogBinding.spinnerEditUnit.selectedItemPosition)?.id

                if (name.isEmpty()) {
                    Toast.makeText(requireContext(), "이름은 필수입니다.", Toast.LENGTH_SHORT).show()
                } else if (rank == null || unitId == null) {
                    Toast.makeText(requireContext(), "계급과 소속 부대는 필수입니다.", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.updateMember(
                        member.id,
                        UpdateMemberRequest(name, rank, unitId),
                        selectedMemberPhotoUri,
                        editRemovePhoto
                    )
                }
            }
            .setNegativeButton("취소", null)
            .setOnDismissListener { editMemberDialogBinding = null }
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

    /**
     * 휴지통 아이콘 탭 시 "숨기기"(비활성화) 또는 "완전 삭제" 중 하나를 고르게 하는 바텀시트를 띄운다.
     * 각 항목을 고르면 바텀시트를 닫고 해당 확인 다이얼로그([showDeactivateConfirmDialog] /
     * [showDeletePermanentlyConfirmDialog])로 넘어간다.
     */
    private fun showDeleteMemberChoiceDialog(member: MemberDto) {
        val sheetBinding = DialogDeleteMemberBinding.inflate(layoutInflater)
        val bottomSheet = BottomSheetDialog(requireContext())
        bottomSheet.setContentView(sheetBinding.root)

        sheetBinding.tvDeleteMemberTitle.text = "${member.name} 대원 삭제"
        sheetBinding.rowHideMember.setOnClickListener {
            bottomSheet.dismiss()
            showDeactivateConfirmDialog(member)
        }
        sheetBinding.rowDeletePermanently.setOnClickListener {
            bottomSheet.dismiss()
            showDeletePermanentlyConfirmDialog(member)
        }

        bottomSheet.show()
    }

    /** 대원 숨기기(비활성화) 전, 확인 다이얼로그를 띄운다. */
    private fun showDeactivateConfirmDialog(member: MemberDto) {
        AlertDialog.Builder(requireContext())
            .setTitle("대원 숨기기")
            .setMessage("'${member.name}' 대원을 목록에서 숨기시겠습니까? (제대/전역 처리, 측정 기록은 유지됩니다.)")
            .setPositiveButton("숨기기") { _, _ -> viewModel.deactivateMember(member) }
            .setNegativeButton("취소", null)
            .show()
    }

    /** 대원 완전 삭제 전, 되돌릴 수 없는 작업이므로 강한 경고와 함께 확인 다이얼로그를 띄운다. */
    private fun showDeletePermanentlyConfirmDialog(member: MemberDto) {
        AlertDialog.Builder(requireContext())
            .setTitle("대원 완전 삭제")
            .setMessage("'${member.name}' 대원과 측정 기록을 DB에서 완전히 삭제하시겠습니까?\n이 작업은 절대 되돌릴 수 없습니다.")
            .setPositiveButton("완전 삭제") { _, _ -> viewModel.deletePermanently(member) }
            .setNegativeButton("취소", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Fragment의 View가 파괴된 뒤에도 binding을 들고 있으면 메모리 누수가 나므로 null로 해제
        _binding = null
    }
}
