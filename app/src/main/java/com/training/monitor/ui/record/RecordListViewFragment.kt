// 관리자 - 특정 대원의 전체 기록(사진 포함) 조회 화면

package com.training.monitor.ui.record

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.training.monitor.databinding.FragmentRecordListViewBinding
import com.training.monitor.ui.photo.PhotoViewActivity

/**
 * 관리자 전용 화면: [com.training.monitor.ui.member.MemberListFragment]에서 대원의
 * "기록 보기" 아이콘을 탭하면 이 화면으로 넘어와, 그 대원의 전체 측정 기록(사진 포함)을
 * 보여준다. [RecordAdapter]를 [MyRecordFragment]와 그대로 공유한다.
 */
class RecordListViewFragment : Fragment() {

    private var _binding: FragmentRecordListViewBinding? = null
    private val binding get() = _binding!!

    private val viewModel: RecordListViewViewModel by viewModels()
    private val adapter = RecordAdapter(onPhotoClick = { record ->
        startActivity(PhotoViewActivity.newIntentForRecord(requireContext(), record.id))
    })

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRecordListViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val userId = requireArguments().getLong("userId")
        binding.tvMemberName.text = requireArguments().getString("memberName") ?: ""

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
