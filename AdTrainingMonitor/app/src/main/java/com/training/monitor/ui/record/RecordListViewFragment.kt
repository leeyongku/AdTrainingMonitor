// 관리자 - 특정 대원의 전체 기록(사진 포함) 조회 화면

package com.training.monitor.ui.record

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.training.monitor.data.model.RecordDto
import com.training.monitor.databinding.FragmentRecordListViewBinding
import com.training.monitor.ui.photo.PhotoViewActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * 관리자 전용 화면: [com.training.monitor.ui.member.MemberListFragment]에서 대원의
 * "기록 보기" 아이콘을 탭하면 이 화면으로 넘어와, 그 대원의 전체 측정 기록(사진 포함)을
 * 보여준다. [RecordAdapter]를 [MyRecordFragment]와 그대로 공유하되, 여기서는
 * [RecordAdapter.onDeleteClick]을 넘겨줘서 항목별 삭제 아이콘이 보이게 한다.
 */
@AndroidEntryPoint
class RecordListViewFragment : Fragment() {

    private var _binding: FragmentRecordListViewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecordListViewViewModel by viewModels()
    private val adapter = RecordAdapter(
        onPhotoClick = { record -> startActivity(PhotoViewActivity.newIntentForRecord(requireContext(), record.id)) },
        onDeleteClick = { record -> showDeleteRecordDialog(record) }
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRecordListViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userId = requireArguments().getLong("userId")
        binding.tvMemberName.text = requireArguments().getString("memberName") ?: ""

        // 대원 목록에서 넘겨받은 얼굴 사진(Base64)이 있으면 헤더에 보여주고, 없으면 숨겨둔다.
        val photoBase64 = requireArguments().getString("memberPhotoBase64")
        if (photoBase64 != null) {
            val bytes = Base64.decode(photoBase64, Base64.NO_WRAP)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            binding.ivMemberPhoto.setImageBitmap(bitmap)
            binding.ivMemberPhoto.visibility = View.VISIBLE
        }

        // 뒤로가기 제스처/버튼에만 의존하지 않고, 닫기 버튼을 눌러도 바로 대원 목록으로 돌아간다.
        binding.ivCloseRecordList.setOnClickListener { findNavController().popBackStack() }
        binding.btnDeleteAllRecords.setOnClickListener { showDeleteAllRecordsDialog(userId) }

        binding.rvRecords.layoutManager = LinearLayoutManager(requireContext())
        binding.rvRecords.adapter = adapter

        viewModel.records.observe(viewLifecycleOwner) { adapter.submitList(it) }
        viewModel.toastMessage.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                viewModel.onToastMessageShown()
            }
        }

        viewModel.loadRecords(userId)
    }

    /** 기록 한 건을 삭제하기 전, 실수로 지우는 것을 막기 위해 확인 다이얼로그를 띄운다. */
    private fun showDeleteRecordDialog(record: RecordDto) {
        val userId = requireArguments().getLong("userId")
        AlertDialog.Builder(requireContext())
            .setTitle("기록 삭제")
            .setMessage("${record.categoryName} ${record.measuredAt ?: ""} 기록을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ -> viewModel.deleteRecord(record.id, userId) }
            .setNegativeButton("취소", null)
            .show()
    }

    /** 이 대원의 전체 기록을 삭제하기 전, 되돌릴 수 없는 작업이므로 확인 다이얼로그를 띄운다. */
    private fun showDeleteAllRecordsDialog(userId: Long) {
        val memberName = requireArguments().getString("memberName") ?: ""
        AlertDialog.Builder(requireContext())
            .setTitle("전체 기록 삭제")
            .setMessage("${memberName}의 모든 측정 기록을 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.")
            .setPositiveButton("전체 삭제") { _, _ -> viewModel.deleteAllRecords(userId) }
            .setNegativeButton("취소", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
