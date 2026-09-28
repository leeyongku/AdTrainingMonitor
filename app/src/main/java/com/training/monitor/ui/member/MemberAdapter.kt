// 대원 목록 RecyclerView 어댑터

package com.training.monitor.ui.member

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.training.monitor.data.model.MemberDto
import com.training.monitor.databinding.ItemMemberBinding

/**
 * [MemberDto] 목록을 RecyclerView에 표시하는 어댑터.
 *
 * [ListAdapter]를 사용해 [DiffCallback] 기반으로 리스트 변경분만 효율적으로 갱신한다
 * (검색어 입력 시 필터링된 리스트를 [submitList]로 넘기면 자동으로 diff 애니메이션 처리됨).
 *
 * [MVVM 변경] 이 클래스는 View 계층이라 자체 상태/비즈니스 로직을 갖지 않는다 — 데이터는
 * [MemberListViewModel]이 갖고 있고 [MemberListFragment]가 [submitList]로 넘겨줄 뿐이다.
 * [MemberAdapterViewModel] 참고.
 */
class MemberAdapter : ListAdapter<MemberDto, MemberAdapter.ViewHolder>(DiffCallback) {

    /** 항목(행) 클릭 콜백. 호출부([MemberListFragment] 등)에서 설정해 상세 화면 이동 등에 사용. */
    var onItemClick: ((MemberDto) -> Unit)? = null

    /** "기록 보기" 아이콘 클릭 콜백. 호출부가 관리자용 기록 열람 화면으로 이동시키는 데 사용. */
    var onRecordsClick: ((MemberDto) -> Unit)? = null

    inner class ViewHolder(private val binding: ItemMemberBinding) :
        RecyclerView.ViewHolder(binding.root) {

        /** 대원 한 명의 데이터를 뷰에 바인딩한다. */
        fun bind(member: MemberDto) {
            // 계급이 없을 수도 있으므로(null) 공백과 함께 이어붙인 뒤 trim으로 정리
            binding.tvName.text = "${member.rank ?: ""} ${member.name}".trim()
            binding.tvMilitaryId.text = member.militaryId
            binding.tvUnit.text = member.unitName ?: "-"
            binding.root.setOnClickListener { onItemClick?.invoke(member) }
            binding.ivViewRecords.setOnClickListener { onRecordsClick?.invoke(member) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = ViewHolder(
        ItemMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    /** ListAdapter가 리스트 갱신 시 항목 동일성/내용 변경 여부를 판단하는 데 사용하는 콜백. */
    companion object DiffCallback : DiffUtil.ItemCallback<MemberDto>() {
        // 같은 대원인지 (id 기준 — 리스트 내 위치가 바뀌어도 같은 항목으로 인식)
        override fun areItemsTheSame(a: MemberDto, b: MemberDto) = a.id == b.id
        // 내용이 실제로 바뀌었는지 (data class의 구조적 동등성 비교로 재바인딩 필요 여부 결정)
        override fun areContentsTheSame(a: MemberDto, b: MemberDto) = a == b
    }
}