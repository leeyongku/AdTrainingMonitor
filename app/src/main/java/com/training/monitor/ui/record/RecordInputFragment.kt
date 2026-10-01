// 관리자 - 체력 측정 기록 입력 화면

package com.training.monitor.ui.record

import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.training.monitor.R
import com.training.monitor.databinding.DialogCreateSessionBinding
import com.training.monitor.databinding.FragmentRecordInputBinding
import com.training.monitor.ui.photo.PhotoViewActivity
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

/**
 * 관리자 전용 화면: 측정 세션 + 대원 + 종목을 선택하고 측정값을 입력해 기록을 저장한다.
 * 저장 전에 서버가 내려준 등급 기준표(GradeCriteria, 선택된 대원의 계급군 반영)로 등급
 * 미리보기를 계산해 보여주며, 실제 등급은 저장 시 서버 응답값을 최종으로 신뢰한다.
 *
 * [MVVM 변경] 이 클래스는 이제 "화면을 그리고 사용자 입력을 받는 View" 역할만 한다. 세션/대원
 * 목록 조회, 기록 저장, 등급 미리보기 계산은 [RecordInputViewModel]이 담당하고, 여기서는 그
 * 결과를 스피너/텍스트로 그리거나 스피너 선택값을 읽어 ViewModel에 넘기는 일만 한다.
 */
class RecordInputFragment : Fragment() {

    private var _binding: FragmentRecordInputBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecordInputViewModel by viewModels()

    // 종목 ID 매핑 (서버 기초 데이터와 일치) — RadioButton의 View id를 도메인 categoryId로 바꿔주는
    // 표만 남기고, 실제 판정/조회/저장 로직은 모두 ViewModel로 옮겼다.
    private val categoryMap = mapOf(
        R.id.rb3km to 1L,
        R.id.rbPushup to 2L,
        R.id.rbSitup to 3L
    )

    // 촬영해둔 임시 사진 파일. 저장 시 이 파일을 리사이즈/압축해서 base64로 보낸다.
    private var pendingPhotoFile: File? = null
    private var pendingPhotoUri: Uri? = null

    // launchCamera()가 만들어둔 "촬영 시도 중" 파일/uri. 카메라가 성공(success=true)을 돌려줘야
    // pendingPhotoFile/Uri로 승격된다. 취소/실패 시에는 이전에 촬영해둔 사진을 그대로 유지한다.
    private var candidatePhotoFile: File? = null
    private var candidatePhotoUri: Uri? = null

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            pendingPhotoFile = candidatePhotoFile
            pendingPhotoUri = candidatePhotoUri
            binding.ivPhotoThumbnail.visibility = View.VISIBLE
            binding.ivPhotoThumbnail.setImageURI(pendingPhotoUri)
        } else {
            candidatePhotoFile?.delete()
        }
        candidatePhotoFile = null
        candidatePhotoUri = null
    }

    //뷰를 생성하고 반환
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRecordInputBinding.inflate(inflater, container, false)
        return binding.root
    }

    //생성된 뷰를 초기화 (리스너, 어댑터 등)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // [MVVM 변경] 세션/대원 목록이 도착하면 스피너 어댑터를 구성하도록 관찰을 등록한다.
        // (기존에는 loadSessions()/loadMembers() 응답 콜백 안에서 바로 어댑터를 만들었다.)
        viewModel.sessions.observe(viewLifecycleOwner) { sessions ->
            val labels = sessions.map { "${it.measuredAt} ${it.location ?: ""}" }
            binding.spinnerSession.adapter = ArrayAdapter(
                requireContext(), android.R.layout.simple_spinner_item, labels
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }

        viewModel.members.observe(viewLifecycleOwner) { members ->
            val labels = members.map { "${it.rank ?: ""} ${it.name} (${it.militaryId})" }
            binding.spinnerMember.adapter = ArrayAdapter(
                requireContext(), android.R.layout.simple_spinner_item, labels
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            // 대원 목록이 도착해 스피너 기본 선택(0번)이 잡히면, 그 대원 기준 등급 기준표를 바로 불러온다.
            loadGradeCriteriaForSelection()
        }

        // 등급 기준표가 도착하면(최초 로딩이든, 종목/대원 변경으로 다시 불러온 것이든) 지금
        // 입력돼 있는 값으로 미리보기를 다시 계산한다.
        viewModel.gradeCriteria.observe(viewLifecycleOwner) { updateGradePreview() }

        viewModel.toastMessage.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                viewModel.onToastMessageShown()
            }
        }

        // [MVVM 변경] 저장 성공 이벤트를 관찰해 값/메모/사진 입력란만 초기화한다 (세션·대원 선택은 유지).
        viewModel.saveSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                binding.etValue.text?.clear()
                binding.etNote.text?.clear()
                pendingPhotoFile = null
                pendingPhotoUri = null
                binding.ivPhotoThumbnail.setImageURI(null)
                binding.ivPhotoThumbnail.visibility = View.GONE
                viewModel.onSaveHandled()
            }
        }

        viewModel.loadSessions()
        viewModel.loadMembers()

        binding.btnAddSession.setOnClickListener { showCreateSessionDialog() }
        binding.btnTakePhoto.setOnClickListener { launchCamera() }
        binding.ivPhotoThumbnail.setOnClickListener {
            pendingPhotoUri?.let { uri -> startActivity(PhotoViewActivity.newIntent(requireContext(), uri)) }
        }

        // 종목/대원 선택이 바뀌면 등급 기준표가 달라지므로(계급군, 종목별 기준) 다시 불러온다.
        binding.rgCategory.setOnCheckedChangeListener { _, _ -> loadGradeCriteriaForSelection() }
        binding.spinnerMember.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                loadGradeCriteriaForSelection()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 값 입력 시 등급 미리보기
        binding.etValue.addTextChangedListener { updateGradePreview() }

        binding.btnSave.setOnClickListener { saveRecord() }
    }

    /** 현재 선택된 종목/대원을 읽어 ViewModel에 등급 기준표 재조회를 요청한다. */
    private fun loadGradeCriteriaForSelection() {
        val members = viewModel.members.value ?: return
        val memberIdx = binding.spinnerMember.selectedItemPosition
        if (memberIdx < 0 || memberIdx >= members.size) return

        val categoryId = categoryMap[binding.rgCategory.checkedRadioButtonId] ?: 1L
        viewModel.loadGradeCriteria(categoryId, members[memberIdx].id)
    }

    /** 측정일/장소/메모를 입력받아 새 측정 세션을 생성하는 다이얼로그를 띄운다. */
    private fun showCreateSessionDialog() {
        val dialogBinding = DialogCreateSessionBinding.inflate(layoutInflater)
        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        var selectedDate = LocalDate.now()

        fun updateDateButton() {
            dialogBinding.btnSessionDate.text = selectedDate.format(fmt)
        }
        updateDateButton()

        dialogBinding.btnSessionDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, y, m, d ->
                selectedDate = LocalDate.of(y, m + 1, d)   // DatePickerDialog의 월(m)은 0부터 시작하므로 +1
                updateDateButton()
            }, selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth).show()
        }

        AlertDialog.Builder(requireContext())
            .setTitle("측정 세션 추가")
            .setView(dialogBinding.root)
            .setPositiveButton("추가") { _, _ ->
                viewModel.createSession(
                    measuredAt = selectedDate.format(fmt),
                    location = dialogBinding.etLocation.text.toString().trim().ifEmpty { null },
                    note = dialogBinding.etSessionNote.text.toString().trim().ifEmpty { null }
                )
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /**
     * 입력값을 ViewModel의 순수 계산 함수(현재 로딩된 [RecordInputViewModel.gradeCriteria] 기준)로
     * 넘겨 예상 등급을 받아오고, 등급별 색상으로 강조 표시한다 (색상 매핑은 View 스타일 관심사이므로
     * 여기 남겨둔다).
     */
    private fun updateGradePreview() {
        val value = binding.etValue.text.toString().toDoubleOrNull()
        val grade = viewModel.previewGrade(value)

        // 등급별 색상 강조 (특급=파랑 ~ 불합격=빨강 순으로 시각적 위계 표현)
//        binding.tvGradePreview.setTextColor(when (grade) {
//                        "특급" -> 0xFF1565C0.toInt()
//                        "1급" -> 0xFF2E7D32.toInt()
//                        "2급" -> 0xFFF57F17.toInt()
//                        "3급" -> 0xFFE65100.toInt()
//                        "불합격" -> 0xFFB71C1C.toInt()
//                        else -> 0xFF666666.toInt()
//        })

        binding.tvGradePreview.text = grade

        binding.tvGradePreview.setTextColor(ContextCompat.getColor(requireContext(), when (grade) {
            "특급" -> R.color.grade_top
            "1급" -> R.color.grade_1
            "2급" -> R.color.grade_2
            "3급" -> R.color.grade_3
            "불합격" -> R.color.color_danger
            else -> R.color.text_secondary
        }))
    }

    /** 앱 캐시 폴더에 임시 파일을 만들고, FileProvider로 카메라 앱에 촬영을 요청한다. */
    private fun launchCamera() {
        val imagesDir = File(requireContext().cacheDir, "images").apply { mkdirs() }
        val file = File.createTempFile("record_", ".jpg", imagesDir)
        val uri = FileProvider.getUriForFile(requireContext(), "${requireContext().packageName}.fileprovider", file)
        candidatePhotoFile = file
        candidatePhotoUri = uri
        cameraLauncher.launch(uri)
    }

    /** 선택된 세션/대원/종목과 입력값을 읽어 ViewModel에 저장을 요청한다. */
    private fun saveRecord() {
        val value = binding.etValue.text.toString().toDoubleOrNull()
        if (value == null) {
            Toast.makeText(requireContext(), "측정값을 입력하세요.", Toast.LENGTH_SHORT).show()
            return
        }

        // 스피너의 선택 위치(index)로 원본 리스트에서 실제 세션/대원 객체를 찾는다.
        val sessions = viewModel.sessions.value ?: emptyList()
        val members = viewModel.members.value ?: emptyList()
        val sessionIdx = binding.spinnerSession.selectedItemPosition
        val memberIdx = binding.spinnerMember.selectedItemPosition
        if (sessions.isEmpty() || members.isEmpty()) return

        val categoryId = categoryMap[binding.rgCategory.checkedRadioButtonId] ?: 1L
        viewModel.saveRecord(
            sessionId = sessions[sessionIdx].id,
            userId = members[memberIdx].id,
            categoryId = categoryId,
            value = value,
            note = binding.etNote.text.toString().ifBlank { null },
            photoFile = pendingPhotoFile
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
